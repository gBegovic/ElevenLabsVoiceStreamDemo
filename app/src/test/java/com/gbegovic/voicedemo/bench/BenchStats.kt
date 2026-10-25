package com.gbegovic.voicedemo.bench

import java.io.File

/** One row of bench/results.csv, as written by bench/run_matrix.sh. */
data class BenchRow(
    val setup: String,
    val run: Int,
    val warmup: Boolean,
    val mode: String,
    val variant: String,
    val model: String,
    val firstByteMs: Long?,
    val firstAudioMs: Long?,
    val chars: Int,
    val success: Boolean,
) {
    /** Counted in the summary: not the warm-up run, and finished with audio. */
    val valid: Boolean get() = !warmup && success && firstAudioMs != null
}

data class Spread(val median: Double, val min: Long, val max: Long)

data class SetupStats(
    val setup: String,
    val mode: String,
    val variant: String,
    val totalRuns: Int,
    val validRuns: Int,
    val firstAudio: Spread?,
    val firstByte: Spread?,
)

/** Pure helpers for turning collected runs into summary numbers. No rounding: medians are exact. */
object BenchStats {

    const val RESULTS_HEADER = "setup,run,warmup,mode,variant,model,firstByteMs,firstAudioMs,chars,success"

    fun readResults(file: File): List<BenchRow> {
        val lines = file.readLines().filter { it.isNotBlank() }
        require(lines.firstOrNull() == RESULTS_HEADER) { "unexpected header in $file: ${lines.firstOrNull()}" }
        return lines.drop(1).map { line ->
            val f = parseCsvLine(line)
            require(f.size == 10) { "expected 10 columns, got ${f.size}: $line" }
            BenchRow(
                setup = f[0],
                run = f[1].toInt(),
                warmup = f[2].toBooleanStrict(),
                mode = f[3],
                variant = f[4],
                model = f[5],
                firstByteMs = f[6].toLongOrNull(),
                firstAudioMs = f[7].toLongOrNull(),
                chars = f[8].toInt(),
                success = f[9].toBooleanStrict(),
            )
        }
    }

    /** Groups rows by setup, keeping the order setups first appear in. */
    fun summarize(rows: List<BenchRow>): List<SetupStats> =
        rows.groupBy { it.setup }.map { (setup, runs) ->
            val valid = runs.filter { it.valid }
            SetupStats(
                setup = setup,
                mode = runs.first().mode,
                variant = runs.first().variant,
                totalRuns = runs.size,
                validRuns = valid.size,
                firstAudio = spread(valid.mapNotNull { it.firstAudioMs }),
                firstByte = spread(valid.mapNotNull { it.firstByteMs }),
            )
        }

    fun spread(values: List<Long>): Spread? =
        if (values.isEmpty()) null else Spread(median(values), values.min(), values.max())

    /** Exact median: the middle value, or the mean of the two middle values (x or x.5). */
    fun median(values: List<Long>): Double {
        require(values.isNotEmpty()) { "median of no values" }
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** "812" for whole numbers, "812.5" otherwise. A median of whole ms is always one of the two. */
    fun formatMs(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    fun markdownTable(stats: List<SetupStats>): String = buildString {
        appendLine("| Setup | Mode | Variant | Valid runs | firstAudioMs median | firstAudioMs min–max | firstByteMs median | firstByteMs min–max |")
        appendLine("|---|---|---|---|---|---|---|---|")
        for (s in stats) {
            // WebSocket sets firstByte = firstAudio (PCM is played on arrival), so it adds nothing there.
            val byte = s.firstByte.takeIf { s.mode == "http" }
            appendLine(
                "| ${s.setup} | ${s.mode} | ${s.variant} | ${s.validRuns} of ${s.totalRuns - 1} " +
                    "| ${s.firstAudio?.let { formatMs(it.median) } ?: "n/a"} " +
                    "| ${s.firstAudio?.let { "${it.min}–${it.max}" } ?: "n/a"} " +
                    "| ${byte?.let { formatMs(it.median) } ?: "n/a"} " +
                    "| ${byte?.let { "${it.min}–${it.max}" } ?: "n/a"} |"
            )
        }
    }

    /** Minimal RFC 4180 field splitter (quoted fields, doubled quotes). */
    fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && line.getOrNull(i + 1) == '"' -> { current.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { fields += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }
}
