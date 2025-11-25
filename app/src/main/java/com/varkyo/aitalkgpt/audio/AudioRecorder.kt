package com.varkyo.aitalkgpt.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Audio recorder with Voice Activity Detection (VAD) and auto-stop functionality.
 * Automatically stops recording when user stops speaking.
 */
class AudioRecorder(
    private val sampleRate: Int = 16000,
    private val silenceThresholdDb: Float = -50f, // dB threshold for silence (lower = more sensitive)
    private val silenceDurationMs: Long = 1000, // Stop after 1.0s of silence (reduced from 1.5s)
    private val minRecordingDurationMs: Long = 500 // Minimum recording duration
) {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var recordingStartTime = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecordingState: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioData = MutableStateFlow<ByteArray?>(null)
    val audioData: StateFlow<ByteArray?> = _audioData.asStateFlow()

    /**
     * Start recording audio with VAD
     */
    fun startRecording() {
        if (isRecording) {
            Log.w("AudioRecorder", "Already recording")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            Log.e("AudioRecorder", "Invalid buffer size: $minBufferSize")
            return
        }

        val bufferSize = minBufferSize * 2

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioRecorder", "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return
        }

        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        _isRecording.value = true

        audioRecord?.startRecording()

        // Start recording thread with VAD
        recordingThread = Thread {
            recordWithVAD()
        }.apply {
            start()
        }

        Log.d("AudioRecorder", "Recording started")
    }

    /**
     * Stop recording manually
     */
    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        _isRecording.value = false
        recordingThread?.join(1000)
        recordingThread = null
    }

    /**
     * Record audio with Voice Activity Detection
     * Automatically stops when silence is detected
     */
    private fun recordWithVAD() {
        val buffer = ByteArray(1600) // 100ms at 16kHz, 16-bit mono
        val audioBuffer = mutableListOf<ByteArray>()
        var lastSpeechTime = System.currentTimeMillis()
        var hasDetectedSpeech = false
        var consecutiveSilenceChunks = 0
        val requiredSilenceChunks = (silenceDurationMs / 100).toInt() // Number of 100ms chunks needed

        Log.d("AudioRecorder", "VAD started: threshold=${silenceThresholdDb}dB, silence=${silenceDurationMs}ms")

        while (isRecording) {
            val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

            if (bytesRead <= 0) {
                if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e("AudioRecorder", "Invalid operation")
                    break
                }
                continue
            }

            val chunk = buffer.copyOf(bytesRead)
            audioBuffer.add(chunk)

            // Calculate RMS (Root Mean Square) for VAD
            val rmsDb = calculateRmsDb(chunk)
            val currentTime = System.currentTimeMillis()
            val recordingDuration = currentTime - recordingStartTime
            val silenceDuration = currentTime - lastSpeechTime

            // Check if we have speech (above threshold)
            if (rmsDb > silenceThresholdDb) {
                lastSpeechTime = currentTime
                hasDetectedSpeech = true
                consecutiveSilenceChunks = 0
                Log.d("AudioRecorder", "Speech detected: ${String.format("%.1f", rmsDb)}dB (threshold: ${silenceThresholdDb}dB)")
            } else {
                consecutiveSilenceChunks++
                // Log every 5 chunks to avoid spam
                if (consecutiveSilenceChunks % 5 == 0) {
                    Log.d("AudioRecorder", "Silence: ${String.format("%.1f", rmsDb)}dB, chunks: $consecutiveSilenceChunks/$requiredSilenceChunks")
                }
            }

            // Auto-stop conditions:
            // 1. Minimum recording duration has passed
            // 2. Speech was detected at least once
            // 3. Silence duration threshold exceeded
            if (recordingDuration >= minRecordingDurationMs &&
                hasDetectedSpeech &&
                silenceDuration >= silenceDurationMs) {
                Log.d("AudioRecorder", "✅ Auto-stop triggered!")
                Log.d("AudioRecorder", "   Recording duration: ${recordingDuration}ms")
                Log.d("AudioRecorder", "   Silence duration: ${silenceDuration}ms (threshold: ${silenceDurationMs}ms)")
                Log.d("AudioRecorder", "   Current RMS: ${String.format("%.1f", rmsDb)}dB")
                isRecording = false // Set flag before breaking
                break
            }
        }

        // Ensure recording flag is false
        isRecording = false
        _isRecording.value = false

        // Combine all chunks into single byte array
        if (audioBuffer.isNotEmpty()) {
            val totalSize = audioBuffer.sumOf { it.size }
            val combinedAudio = ByteArray(totalSize)
            var offset = 0

            for (chunk in audioBuffer) {
                chunk.copyInto(combinedAudio, offset)
                offset += chunk.size
            }

            _audioData.value = combinedAudio
            Log.d("AudioRecorder", "Recording complete: ${combinedAudio.size} bytes")
        }

        cleanupAfterRecording()
    }

    /**
     * Calculate RMS (Root Mean Square) in decibels
     */
    private fun calculateRmsDb(pcm: ByteArray): Float {
        if (pcm.isEmpty()) return -120f

        var sum = 0.0
        val sampleCount = pcm.size / 2

        // Convert bytes to 16-bit samples (little-endian format used by Android AudioRecord)
        for (i in 0 until pcm.size - 1 step 2) {
            // Little-endian: low byte first, then high byte
            val low = pcm[i].toInt() and 0xFF
            val high = pcm[i + 1].toInt()
            val unsigned = (high shl 8) or low
            // Convert unsigned to signed 16-bit
            val sample = if (unsigned > 32767) unsigned - 65536 else unsigned
            val sampleValue = sample.toDouble()
            sum += sampleValue * sampleValue
        }

        if (sampleCount == 0) return -120f

        val rms = sqrt(sum / sampleCount)
        return if (rms > 0) {
            (20 * log10(rms / 32768.0)).toFloat()
        } else {
            -120f
        }
    }

    /**
     * Release resources
     */
    fun release() {
        stopRecording()
        cleanupAfterRecording()
    }

    private fun cleanupAfterRecording() {
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping AudioRecord", e)
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error releasing AudioRecord", e)
        }
        audioRecord = null
        Log.d("AudioRecorder", "Recording stopped")
    }
}

