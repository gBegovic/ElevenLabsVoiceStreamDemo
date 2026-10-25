package com.gbegovic.voicedemo.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class BenchStatsTest {

    @Test
    fun `median of odd count is the middle value`() {
        assertEquals(300.0, BenchStats.median(listOf(500, 100, 300, 200, 400)), 0.0)
    }

    @Test
    fun `median of even count is the exact mean of the middle two`() {
        assertEquals(250.5, BenchStats.median(listOf(100, 250, 251, 400)), 0.0)
    }

    @Test
    fun `formatMs never rounds`() {
        assertEquals("812", BenchStats.formatMs(812.0))
        assertEquals("812.5", BenchStats.formatMs(812.5))
    }

    @Test
    fun `summary skips warm-up and failed runs`() {
        val rows = listOf(
            row(run = 1, warmup = true, firstAudio = 9_000),
            row(run = 2, firstAudio = 300),
            row(run = 3, firstAudio = 100),
            row(run = 4, firstAudio = 200),
            row(run = 5, firstAudio = null, success = false),
        )
        val stats = BenchStats.summarize(rows).single()
        assertEquals(5, stats.totalRuns)
        assertEquals(3, stats.validRuns)
        assertEquals(Spread(median = 200.0, min = 100, max = 300), stats.firstAudio)
    }

    @Test
    fun `setup with no valid runs has no spread`() {
        val stats = BenchStats.summarize(listOf(row(run = 1, warmup = true, firstAudio = 50))).single()
        assertEquals(0, stats.validRuns)
        assertNull(stats.firstAudio)
    }

    @Test
    fun `results file round-trips through the reader`() {
        val file = File.createTempFile("results", ".csv").apply { deleteOnExit() }
        file.writeText(
            BenchStats.RESULTS_HEADER + "\n" +
                "http-default,1,true,http,default buffer,eleven_flash_v2_5,310,2900,141,true\n" +
                "http-default,2,false,http,default buffer,eleven_flash_v2_5,,,141,false\n"
        )
        val rows = BenchStats.readResults(file)
        assertEquals(2, rows.size)
        assertEquals(2_900L, rows[0].firstAudioMs)
        assertNull(rows[1].firstByteMs)
        assertEquals(false, rows[1].success)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `results file with the wrong header is rejected`() {
        val file = File.createTempFile("results", ".csv").apply { deleteOnExit() }
        file.writeText("mode,variant\nhttp,x\n")
        BenchStats.readResults(file)
    }

    private fun row(run: Int, firstAudio: Long?, warmup: Boolean = false, success: Boolean = true) = BenchRow(
        setup = "http-default", run = run, warmup = warmup, mode = "http", variant = "default buffer",
        model = "eleven_flash_v2_5", firstByteMs = firstAudio?.let { it / 10 }, firstAudioMs = firstAudio,
        chars = 141, success = success,
    )
}
