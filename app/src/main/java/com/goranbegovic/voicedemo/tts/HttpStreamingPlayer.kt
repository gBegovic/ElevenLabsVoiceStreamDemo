package com.goranbegovic.voicedemo.tts

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * "Reader-style" playback: the full text is known up front, so we POST it to the
 * HTTP streaming endpoint and let ExoPlayer start playing MP3 as soon as the first
 * frames arrive, without waiting for the whole file.
 *
 * The trick is [ResolvingDataSource]: ExoPlayer only knows how to GET a URI, so we
 * rewrite each DataSpec into a POST with the JSON body and API key header.
 */
@OptIn(UnstableApi::class)
class HttpStreamingPlayer(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    lowLatencyBuffer: Boolean,
) {
    interface Callback {
        fun onFirstByte(elapsedMs: Long)
        fun onPlaybackStarted(elapsedMs: Long)
        fun onCompleted()
        fun onError(message: String)
    }

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .apply {
            // ExoPlayer's defaults wait for ~2.5 s of media before starting playback, which
            // suits video files but delays speech that is being generated on the fly.
            // The low-latency variant starts after 250 ms of buffered audio.
            if (lowLatencyBuffer) {
                setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            /* minBufferMs = */ 1_000,
                            /* maxBufferMs = */ 30_000,
                            /* bufferForPlaybackMs = */ 250,
                            /* bufferForPlaybackAfterRebufferMs = */ 500,
                        )
                        .build()
                )
            }
        }
        .build()

    private var callback: Callback? = null
    private var requestStartMs = 0L
    private var firstByteReported = false
    private var startReported = false

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && !startReported) {
                    startReported = true
                    callback?.onPlaybackStarted(SystemClock.elapsedRealtime() - requestStartMs)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) callback?.onCompleted()
            }

            override fun onPlayerError(error: PlaybackException) {
                callback?.onError(error.cause?.message ?: error.errorCodeName)
            }
        })
    }

    fun speak(text: String, voiceId: String, modelId: String, callback: Callback) {
        this.callback = callback
        requestStartMs = SystemClock.elapsedRealtime()
        firstByteReported = false
        startReported = false

        val body = JSONObject()
            .put("text", text)
            .put("model_id", modelId)
            .toString()
            .toByteArray()

        val upstream = OkHttpDataSource.Factory(okHttpClient).setTransferListener(firstByteListener())

        // Every open() becomes a POST. Note: ExoPlayer may re-open on retry, which would
        // re-generate (and re-bill) the audio. Fine for a demo; a production player would
        // cache the stream (e.g. CacheDataSource) so retries and seeks hit the cache.
        val postingFactory = ResolvingDataSource.Factory(upstream) { dataSpec ->
            dataSpec.buildUpon()
                .setHttpMethod(DataSpec.HTTP_METHOD_POST)
                .setHttpBody(body)
                .setHttpRequestHeaders(
                    mapOf(
                        ElevenLabsConfig.HEADER_API_KEY to apiKey,
                        "Content-Type" to "application/json",
                        "Accept" to "audio/mpeg",
                    )
                )
                .build()
        }

        val mediaSource = ProgressiveMediaSource.Factory(postingFactory)
            .createMediaSource(MediaItem.fromUri(ElevenLabsConfig.httpStreamUrl(voiceId)))

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        callback = null
        player.release()
    }

    private fun firstByteListener() = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

        // Called on the loading thread; the callback is responsible for hopping threads.
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (!firstByteReported && bytesTransferred > 0) {
                firstByteReported = true
                callback?.onFirstByte(SystemClock.elapsedRealtime() - requestStartMs)
            }
        }
    }
}
