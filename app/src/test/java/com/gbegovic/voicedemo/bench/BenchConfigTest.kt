package com.gbegovic.voicedemo.bench

import com.gbegovic.voicedemo.ui.StreamMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BenchConfigTest {

    @Test
    fun `no mode means a normal launch, not a batch`() {
        assertNull(BenchConfig.fromExtras(mode = null, lowLatency = true, tokenDelayMs = 0, runs = 6))
    }

    @Test
    fun `http mode keeps the buffer flag`() {
        val config = BenchConfig.fromExtras("http", lowLatency = true, tokenDelayMs = 40, runs = 6)
        assertEquals(BenchConfig(StreamMode.Http, lowLatencyBuffer = true, tokenDelayMs = 40, runs = 6), config)
    }

    @Test
    fun `ws mode is case insensitive and accepts zero delay`() {
        val config = BenchConfig.fromExtras("WS", lowLatency = false, tokenDelayMs = 0, runs = 1)
        assertEquals(BenchConfig(StreamMode.WebSocket, lowLatencyBuffer = false, tokenDelayMs = 0, runs = 1), config)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown mode is rejected`() {
        BenchConfig.fromExtras("grpc", lowLatency = false, tokenDelayMs = 40, runs = 6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative delay is rejected`() {
        BenchConfig.fromExtras("ws", lowLatency = false, tokenDelayMs = -1, runs = 6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero runs is rejected`() {
        BenchConfig.fromExtras("ws", lowLatency = false, tokenDelayMs = 40, runs = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `runs above the quota guard are rejected`() {
        BenchConfig.fromExtras("http", lowLatency = false, tokenDelayMs = 40, runs = 21)
    }
}
