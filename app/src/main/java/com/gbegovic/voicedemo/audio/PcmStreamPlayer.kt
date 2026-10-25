package com.gbegovic.voicedemo.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Plays raw 16-bit mono PCM chunks as they arrive. AudioTrack in MODE_STREAM skips
 * container parsing and extractor buffering, which is why it beats ExoPlayer on
 * time-to-first-audio for real-time speech.
 *
 * One instance per utterance: [finish] closes the queue, and the writer drains it.
 */
class PcmStreamPlayer(private val sampleRate: Int) {

    private val queue = Channel<ByteArray>(Channel.UNLIMITED)
    private var track: AudioTrack? = null
    private var writer: Job? = null

    /** Odd byte left over from a chunk that split a 16-bit sample in half. */
    private var carry: Byte? = null

    fun start(scope: CoroutineScope, onDrained: () -> Unit = {}) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = audioTrack
        audioTrack.play()

        writer = scope.launch(Dispatchers.IO) {
            for (chunk in queue) {
                audioTrack.write(chunk, 0, chunk.size) // blocking write paces us to real time
            }
            onDrained()
        }
    }

    fun enqueue(pcm: ByteArray) {
        queue.trySend(alignToSamples(pcm))
    }

    fun finish() {
        queue.close()
    }

    fun release() {
        queue.close()
        writer?.cancel()
        track?.let {
            runCatching { it.pause(); it.flush() }
            it.release()
        }
        track = null
    }

    private fun alignToSamples(chunk: ByteArray): ByteArray {
        val leading = carry
        val joined = if (leading != null) byteArrayOf(leading) + chunk else chunk
        return if (joined.size % 2 == 0) {
            carry = null
            joined
        } else {
            carry = joined.last()
            joined.copyOf(joined.size - 1)
        }
    }
}
