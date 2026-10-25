package com.goranbegovic.voicedemo.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goranbegovic.voicedemo.BuildConfig
import com.goranbegovic.voicedemo.audio.PcmStreamPlayer
import com.goranbegovic.voicedemo.tts.ElevenLabsConfig
import com.goranbegovic.voicedemo.tts.HttpStreamingPlayer
import com.goranbegovic.voicedemo.tts.TtsEvent
import com.goranbegovic.voicedemo.tts.WebSocketTtsClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class StreamMode(val label: String) {
    Http("HTTP + Media3"),
    WebSocket("WebSocket + PCM"),
}

data class RunResult(
    val mode: StreamMode,
    val variant: String,
    val modelId: String,
    val firstByteMs: Long?,
    val firstAudioMs: Long?,
    val chars: Int,
)

private val SAMPLE_TEXT =
    """
        Streaming changes how speech feels in an app. Instead of waiting for a whole
        file, the first words start playing while the rest is still being generated.
        This demo compares two paths: a progressive MP3 stream through Media3, and raw
        PCM over a WebSocket fed word by word, the way an LLM would produce an answer.
    """.trimIndent().replace('\n', ' ')

data class DemoUiState(
    val text: String = SAMPLE_TEXT,
    val voiceId: String = BuildConfig.ELEVENLABS_VOICE_ID,
    val modelId: String = ElevenLabsConfig.DEFAULT_MODEL_ID,
    val mode: StreamMode = StreamMode.WebSocket,
    val tokenDelayMs: Int = 40,
    val lowLatencyBuffer: Boolean = false,
    val isRunning: Boolean = false,
    val firstByteMs: Long? = null,
    val firstAudioMs: Long? = null,
    val history: List<RunResult> = emptyList(),
    val log: List<String> = emptyList(),
) {
    val hasApiKey: Boolean get() = BuildConfig.ELEVENLABS_API_KEY.isNotBlank()
}

class DemoViewModel(application: Application) : AndroidViewModel(application) {

    private val okHttp = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming responses can be long-lived
        .pingInterval(15, TimeUnit.SECONDS)    // keeps the TCP connection healthy on mobile networks
        .build()

    private val apiKey = BuildConfig.ELEVENLABS_API_KEY
    private var httpPlayer = HttpStreamingPlayer(application, okHttp, apiKey, lowLatencyBuffer = false)
    private val wsClient = WebSocketTtsClient(okHttp, apiKey)

    private val _state = MutableStateFlow(DemoUiState())
    val state: StateFlow<DemoUiState> = _state.asStateFlow()

    private var wsJob: Job? = null
    private var pcmPlayer: PcmStreamPlayer? = null

    fun onTextChange(value: String) = _state.update { it.copy(text = value) }
    fun onVoiceIdChange(value: String) = _state.update { it.copy(voiceId = value.trim()) }
    fun onModelIdChange(value: String) = _state.update { it.copy(modelId = value.trim()) }
    fun onModeChange(value: StreamMode) = _state.update { it.copy(mode = value) }
    fun onTokenDelayChange(value: Int) = _state.update { it.copy(tokenDelayMs = value) }

    /** LoadControl is fixed when ExoPlayer is built, so switching variants means a new player. */
    fun onLowLatencyBufferChange(value: Boolean) {
        if (_state.value.isRunning) return
        httpPlayer.release()
        httpPlayer = HttpStreamingPlayer(getApplication(), okHttp, apiKey, lowLatencyBuffer = value)
        _state.update { it.copy(lowLatencyBuffer = value) }
    }

    fun speak() {
        val s = _state.value
        if (s.isRunning || s.text.isBlank() || !s.hasApiKey) return
        _state.update {
            it.copy(
                isRunning = true,
                firstByteMs = null,
                firstAudioMs = null,
                log = emptyList(),
            )
        }
        val variant = if (s.mode == StreamMode.Http) {
            if (s.lowLatencyBuffer) " · 250 ms buffer" else " · default buffer"
        } else ""
        log("▶ ${s.mode.label}$variant · ${s.modelId} · ${s.text.length} chars")
        when (s.mode) {
            StreamMode.Http -> speakHttp(s)
            StreamMode.WebSocket -> speakWebSocket(s)
        }
    }

    fun stop() {
        httpPlayer.stop()
        wsJob?.cancel()
        releasePcm()
        if (_state.value.isRunning) finishRun("■ stopped")
    }

    // --- HTTP streaming into ExoPlayer --------------------------------------------------

    private fun speakHttp(s: DemoUiState) {
        httpPlayer.speak(
            text = s.text,
            voiceId = s.voiceId,
            modelId = s.modelId,
            callback = object : HttpStreamingPlayer.Callback {
                override fun onFirstByte(elapsedMs: Long) {
                    _state.update { it.copy(firstByteMs = elapsedMs) }
                    log("first byte after $elapsedMs ms")
                }

                override fun onPlaybackStarted(elapsedMs: Long) {
                    _state.update { it.copy(firstAudioMs = elapsedMs) }
                    log("ExoPlayer playing after $elapsedMs ms")
                }

                override fun onCompleted() = finishRun("✓ playback complete")
                override fun onError(message: String) = finishRun("✗ $message")
            },
        )
    }

    // --- WebSocket with simulated LLM token stream into AudioTrack ----------------------

    private fun speakWebSocket(s: DemoUiState) {
        val player = PcmStreamPlayer(ElevenLabsConfig.PCM_SAMPLE_RATE).also { pcmPlayer = it }
        val startMs = SystemClock.elapsedRealtime()
        var firstAudioSeen = false

        // Emit word by word with a delay, imitating an LLM producing an answer.
        val tokens = flow {
            WORD_SPLIT.findAll(s.text).forEach { match ->
                emit(match.value)
                delay(s.tokenDelayMs.toLong())
            }
        }

        player.start(viewModelScope) {
            viewModelScope.launch { finishRun("✓ playback complete") }
        }

        wsJob = viewModelScope.launch {
            wsClient.stream(s.voiceId, s.modelId, tokens).collect { event ->
                val elapsed = SystemClock.elapsedRealtime() - startMs
                when (event) {
                    TtsEvent.Connected -> log("socket open after $elapsed ms")
                    is TtsEvent.Audio -> {
                        if (!firstAudioSeen) {
                            firstAudioSeen = true
                            // PCM goes to the AudioTrack immediately, so first chunk ≈ first sound.
                            _state.update { it.copy(firstByteMs = elapsed, firstAudioMs = elapsed) }
                            log("first audio chunk after $elapsed ms")
                        }
                        player.enqueue(event.pcm)
                    }

                    TtsEvent.Final -> log("server sent isFinal after $elapsed ms")
                    is TtsEvent.Error -> {
                        log("✗ ${event.message}")
                        releasePcm()
                        finishRun(null)
                    }
                }
            }
            // Socket closed: let the queued audio finish playing, then onDrained fires.
            player.finish()
        }
    }

    // --- helpers ------------------------------------------------------------------------

    private fun finishRun(message: String?) {
        message?.let(::log)
        _state.update { s ->
            if (!s.isRunning) return@update s
            val variant = when (s.mode) {
                StreamMode.Http -> if (s.lowLatencyBuffer) "250 ms buffer" else "default buffer"
                StreamMode.WebSocket -> "${s.tokenDelayMs} ms/word"
            }
            val result =
                RunResult(s.mode, variant, s.modelId, s.firstByteMs, s.firstAudioMs, s.text.length)
            s.copy(isRunning = false, history = (listOf(result) + s.history).take(10))
        }
    }

    private fun releasePcm() {
        pcmPlayer?.release()
        pcmPlayer = null
    }

    private fun log(line: String) = _state.update { it.copy(log = it.log + line) }

    override fun onCleared() {
        wsJob?.cancel()
        releasePcm()
        httpPlayer.release()
        okHttp.dispatcher.executorService.shutdown()
    }

    private companion object {
        /** Words with their trailing whitespace, so chunks concatenate back to the original. */
        val WORD_SPLIT = Regex("""\S+\s*""")
    }
}
