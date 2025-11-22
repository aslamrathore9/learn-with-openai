package com.varkyo.aitalkgpt.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.Channel
import kotlin.math.log10

class MicRecorder(
    private val sampleRate: Int = 24000,
    private val chunkMs: Int = 160,
    private val enableVAD: Boolean = true
) {

    val chunkChannel = Channel<ByteArray>(Channel.UNLIMITED)
    val rmsChannel = Channel<Float>(Channel.UNLIMITED)

    private var audioRecord: AudioRecord? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true

        val frameSizeBytes = sampleRate * 2 * chunkMs / 1000

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = maxOf(minBuf, frameSizeBytes)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()

        Thread {
            val buffer = ByteArray(frameSizeBytes)

            while (running) {
                var offset = 0

                while (offset < buffer.size && running) {
                    val r = audioRecord?.read(buffer, offset, buffer.size - offset) ?: 0
                    if (r <= 0) break
                    offset += r
                }

                if (offset > 0) {
                    val chunk = if (offset == buffer.size) buffer.copyOf() else buffer.copyOf(offset)
                    chunkChannel.trySend(chunk)

                    if (enableVAD) {
                        rmsChannel.trySend(computeRmsDb(chunk))
                    }
                }
            }

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

        }.start()
    }

    fun stop() {
        running = false
    }

    private fun computeRmsDb(pcm: ByteArray): Float {
        var sum = 0.0
        val n = pcm.size / 2

        var i = 0
        while (i + 1 < pcm.size) {
            val lo = pcm[i].toInt() and 0xFF
            val hi = pcm[i + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort()

            val s = sample.toDouble()
            sum += s * s

            i += 2
        }

        if (n == 0) return -120f

        val rms = Math.sqrt(sum / n)
        return if (rms > 0) (20 * log10(rms / 32768.0)).toFloat() else -120f
    }
}
