package com.gbegovic.voicedemo.bench

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Reports the collected benchmark: reads bench/results.csv (written by bench/run_matrix.sh),
 * prints the per-setup table and writes it to app/build/bench/summary-table.md.
 * Skipped when no results have been collected yet.
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*BenchReportTest*' -i
 */
class BenchReportTest {

    @Test
    fun reportCollectedResults() {
        val results = File("../bench/results.csv") // unit tests run from the module directory
        assumeTrue("no ${results.path} yet; run bench/run_matrix.sh first", results.exists())

        val rows = BenchStats.readResults(results)
        val table = BenchStats.markdownTable(BenchStats.summarize(rows))

        File("build/bench").mkdirs()
        File("build/bench/summary-table.md").writeText(table)
        println(table)
    }
}
