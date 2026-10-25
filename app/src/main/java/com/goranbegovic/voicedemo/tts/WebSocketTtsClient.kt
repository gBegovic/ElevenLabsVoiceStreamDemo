package com.goranbegovic.voicedemo.tts

import android.util.Base64
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

sealed interface TtsEvent {
    data object Connected : TtsEvent
    class Audio(val pcm: ByteArray) : TtsEvent
    data object Final : TtsEvent
    data class Error(val message: String) : TtsEvent
}

/**
 * "Agent-style" synthesis: text arrives in pieces (as it would from an LLM), and we
 * forward each piece over the stream-input WebSocket so audio can start before the
 * full answer exists.
 *
 * Protocol (per ElevenLabs docs):
 *  1. initial message: {"text": " ", "voice_settings": {...}}
 *  2. text chunks:     {"text": "Hello "}
 *  3. close:           {"text": ""}  -> forces generation of anything buffered
 * Server sends {"audio": "<base64>", "isFinal": bool, "alignment": {...}}.
 */
class WebSocketTtsClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
) {
    fun stream(voiceId: String, modelId: String, textChunks: Flow<String>): Flow<TtsEvent> = callbackFlow {
        val request = Request.Builder()
            .url(ElevenLabsConfig.wsStreamInputUrl(voiceId, modelId))
            .header(ElevenLabsConfig.HEADER_API_KEY, apiKey)
            .build()

        val webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(TtsEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrElse {
                    trySend(TtsEvent.Error("Unparseable message: ${text.take(120)}"))
                    return
                }
                if (json.has("error") || (json.has("message") && !json.has("audio"))) {
                    trySend(TtsEvent.Error(json.optString("error", json.optString("message"))))
                    return
                }
                if (!json.isNull("audio")) {
                    val pcm = Base64.decode(json.getString("audio"), Base64.DEFAULT)
                    if (pcm.isNotEmpty()) trySend(TtsEvent.Audio(pcm))
                }
                if (json.optBoolean("isFinal", false)) {
                    trySend(TtsEvent.Final)
                    channel.close()
                }
            }

            // Auth and quota problems typically arrive as a close frame with a reason.
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (code != 1000) trySend(TtsEvent.Error("Server closed socket ($code): $reason"))
                webSocket.close(1000, null)
                channel.close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val detail = response?.let { " (HTTP ${it.code})" }.orEmpty()
                trySend(TtsEvent.Error((t.message ?: "WebSocket failure") + detail))
                channel.close()
            }
        })

        // OkHttp queues these until the socket is open, so we can send immediately.
        webSocket.send(
            JSONObject()
                .put("text", " ")
                .put(
                    "voice_settings",
                    JSONObject().put("stability", 0.5).put("similarity_boost", 0.8),
                )
                .toString()
        )

        val sender = launch {
            textChunks.collect { chunk ->
                webSocket.send(JSONObject().put("text", chunk).toString())
            }
            webSocket.send(JSONObject().put("text", "").toString())
        }

        awaitClose {
            sender.cancel()
            webSocket.close(1000, null)
        }
    }
}
