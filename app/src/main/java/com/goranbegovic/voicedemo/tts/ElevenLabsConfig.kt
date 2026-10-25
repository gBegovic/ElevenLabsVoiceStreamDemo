package com.goranbegovic.voicedemo.tts

/**
 * Endpoint and format constants for the ElevenLabs text-to-speech API.
 *
 * Note: the WebSocket stream-input endpoint does not support the v3/v4 models (as of Oct 2026),
 * so the real-time path defaults to Flash v2.5, the model ElevenLabs recommends for latency.
 */
object ElevenLabsConfig {
    const val BASE_HTTP = "https://api.elevenlabs.io/v1"
    const val BASE_WS = "wss://api.elevenlabs.io/v1"

    const val DEFAULT_MODEL_ID = "eleven_flash_v2_5"

    /** Compressed format for the Media3 path: ExoPlayer extracts MP3 progressively. */
    const val HTTP_OUTPUT_FORMAT = "mp3_44100_128"

    /** Raw 16-bit mono PCM for the WebSocket path, written straight to an AudioTrack. */
    const val PCM_SAMPLE_RATE = 16_000
    const val WS_OUTPUT_FORMAT = "pcm_$PCM_SAMPLE_RATE"

    const val HEADER_API_KEY = "xi-api-key"

    fun httpStreamUrl(voiceId: String): String =
        "$BASE_HTTP/text-to-speech/$voiceId/stream?output_format=$HTTP_OUTPUT_FORMAT"

    fun wsStreamInputUrl(voiceId: String, modelId: String): String =
        "$BASE_WS/text-to-speech/$voiceId/stream-input?model_id=$modelId&output_format=$WS_OUTPUT_FORMAT"
}
