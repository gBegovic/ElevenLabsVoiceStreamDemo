package com.gbegovic.voicedemo.bench

import com.gbegovic.voicedemo.ui.RunResult
import com.gbegovic.voicedemo.ui.StreamMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchCsvTest {

    @Test
    fun `http run has the documented column order`() {
        val result = RunResult(
            StreamMode.Http, "default buffer", "eleven_flash_v2_5",
            firstByteMs = 312, firstAudioMs = 2_840, chars = 141, success = true,
        )
        assertEquals("http,default buffer,eleven_flash_v2_5,312,2840,141,true", BenchCsv.line(result))
    }

    @Test
    fun `failed run leaves unreached timings empty`() {
        val result = RunResult(
            StreamMode.WebSocket, "40 ms/word", "eleven_flash_v2_5",
            firstByteMs = null, firstAudioMs = null, chars = 141, success = false,
        )
        assertEquals("ws,40 ms/word,eleven_flash_v2_5,,,141,false", BenchCsv.line(result))
    }

    @Test
    fun `line has as many columns as the header`() {
        val result = RunResult(StreamMode.Http, "250 ms buffer", "m", 1, 2, 3, true)
        assertEquals(BenchCsv.HEADER.split(",").size, BenchCsv.line(result).split(",").size)
    }

    @Test
    fun `fields with commas or quotes are quoted`() {
        assertEquals("plain", BenchCsv.escape("plain"))
        assertEquals("\"a,b\"", BenchCsv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", BenchCsv.escape("say \"hi\""))
    }

    @Test
    fun `quoted fields round-trip through the parser`() {
        val result = RunResult(StreamMode.Http, "odd, variant", "m\"x", 1, 2, 3, true)
        assertEquals(
            listOf("http", "odd, variant", "m\"x", "1", "2", "3", "true"),
            BenchStats.parseCsvLine(BenchCsv.line(result)),
        )
    }
}
