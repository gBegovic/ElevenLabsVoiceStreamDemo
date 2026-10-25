package com.gbegovic.voicedemo.bench

import com.gbegovic.voicedemo.ui.RunResult
import com.gbegovic.voicedemo.ui.StreamMode

/** Formats one run as the `TTFA_BENCH` logcat line that bench/run_matrix.sh collects. */
object BenchCsv {
    const val TAG = "TTFA_BENCH"
    const val HEADER = "mode,variant,model,firstByteMs,firstAudioMs,chars,success"

    fun line(result: RunResult): String = listOf(
        modeName(result.mode),
        result.variant,
        result.modelId,
        result.firstByteMs?.toString().orEmpty(), // empty = never reached
        result.firstAudioMs?.toString().orEmpty(),
        result.chars.toString(),
        result.success.toString(),
    ).joinToString(",") { escape(it) }

    fun modeName(mode: StreamMode): String = when (mode) {
        StreamMode.Http -> "http"
        StreamMode.WebSocket -> "ws"
    }

    /** RFC 4180 quoting, so a model ID or variant with a comma can't shift the columns. */
    internal fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
