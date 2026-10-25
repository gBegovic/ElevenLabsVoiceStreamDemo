package com.gbegovic.voicedemo.bench

import com.gbegovic.voicedemo.ui.StreamMode

/**
 * One benchmark batch, started from adb in debug builds:
 *
 * adb shell am start -S -n com.gbegovic.voicedemo/.MainActivity \
 *     --es mode http|ws --ez lowLatency true|false --ei tokenDelayMs 40 --ei runs 6  *     [--eia chunkSchedule 50,120]   (ws only: generation_config.chunk_length_schedule)
 */
data class BenchConfig(
    val mode: StreamMode,
    val lowLatencyBuffer: Boolean = false,
    val tokenDelayMs: Int = DEFAULT_TOKEN_DELAY_MS,
    val runs: Int = DEFAULT_RUNS,
    val chunkLengthSchedule: List<Int>? = null,
) {
    companion object {
        /** Batch start/done/aborted lines; per-run CSV lines go to [BenchCsv.TAG]. */
        const val LOG_TAG = "TTFA_BATCH"

        const val EXTRA_MODE = "mode"
        const val EXTRA_LOW_LATENCY = "lowLatency"
        const val EXTRA_TOKEN_DELAY_MS = "tokenDelayMs"
        const val EXTRA_RUNS = "runs"
        const val EXTRA_CHUNK_SCHEDULE = "chunkSchedule"

        const val DEFAULT_TOKEN_DELAY_MS = 40
        const val DEFAULT_RUNS = 6

        /**
         * Returns null when no mode is given (a normal app launch).
         * Throws [IllegalArgumentException] for values that would make a meaningless batch.
         */
        fun fromExtras(
            mode: String?,
            lowLatency: Boolean,
            tokenDelayMs: Int,
            runs: Int,
            chunkSchedule: IntArray? = null,
        ): BenchConfig? {
            val streamMode = when (mode?.lowercase()) {
                null -> return null
                "http" -> StreamMode.Http
                "ws" -> StreamMode.WebSocket
                else -> throw IllegalArgumentException("mode must be http or ws, was '$mode'")
            }
            require(tokenDelayMs >= 0) { "tokenDelayMs must be >= 0, was $tokenDelayMs" }
            require(runs in 1..MAX_RUNS) { "runs must be in 1..$MAX_RUNS, was $runs" }
            val schedule = chunkSchedule?.toList()
            if (schedule != null) {
                require(streamMode == StreamMode.WebSocket) { "chunkSchedule only applies to ws" }
                require(schedule.isNotEmpty() && schedule.all { it in CHUNK_RANGE }) {
                    "chunkSchedule values must be in $CHUNK_RANGE, was $schedule"
                }
            }
            return BenchConfig(streamMode, lowLatency, tokenDelayMs, runs, schedule)
        }

        /** Guards the character quota against a typo like `--ei runs 60`. */
        private const val MAX_RUNS = 20

        /** Range ElevenLabs accepts for chunk_length_schedule entries. */
        private val CHUNK_RANGE = 50..500
    }
}
