package com.varkyo.aitalkgpt.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Handles audio recording using Android's native AudioRecord.
 * Includes support for Acoustic Echo Cancellation (AEC) and Noise Suppression (NS).
 *
 * Configured for OpenAI Realtime API: 16kHz, Mono, 16-bit PCM.
 */
class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalAudioMode = AudioManager.MODE_NORMAL
    
    // Callbacks
    var onAudioDataReceived: ((ByteArray) -> Unit)? = null

    // Audio FX
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val LOG_TAG = "AudioRecorder"
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (recordingJob?.isActive == true) return

        try {
            // CRITICAL: Set Audio Mode to COMMUNICATION for AEC to work
            originalAudioMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true // Ensure speaker output
            
            Log.d(LOG_TAG, "Setting Audio Mode to COMMUNICATION for Echo Cancellation")

            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufferSize, 3200) // Ensure buffer isn't too small

            Log.d(LOG_TAG, "Initializing AudioRecord with buffer size: $bufferSize")

            // VOICE_COMMUNICATION source is critical for hardware Echo Cancellation
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(LOG_TAG, "AudioRecord initialization failed")
                return
            }

            // Initialize Audio Effects
            val audioSessionId = audioRecord!!.audioSessionId
            initAudioEffects(audioSessionId)

            audioRecord?.startRecording()
            Log.d(LOG_TAG, "Recording started")

            recordingJob = scope.launch {
                val buffer = ByteArray(320) // 20ms chunks
                
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    
                    if (readResult > 0) {
                        val data = buffer.copyOf(readResult)
                        onAudioDataReceived?.invoke(data)
                        
                        // DEBUG LOG: Log volume level occasionally to verify input
                        if (System.currentTimeMillis() % 2000 < 50) {
                             // Simple RMS calculation
                             var sum = 0.0
                             for (i in 0 until readResult / 2) {
                                 val sample = (buffer[i*2].toInt() and 0xFF) or (buffer[i*2+1].toInt() shl 8)
                                 sum += sample * sample
                             }
                             val rms = Math.sqrt(sum / (readResult / 2))
                             Log.d(LOG_TAG, "🎤 Audio RMS: $rms")
                        }
                    } else {
                         Log.w(LOG_TAG, "Audio read error: $readResult")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error starting recording", e)
        }
    }

    private fun initAudioEffects(sessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)
            acousticEchoCanceler?.enabled = true
            Log.d(LOG_TAG, "✅ AcousticEffectCanceler enabled")
        } else {
            Log.w(LOG_TAG, "⚠️ AcousticEffectCanceler NOT available on this device")
        }

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)
            noiseSuppressor?.enabled = true
            Log.d(LOG_TAG, "✅ NoiseSuppressor enabled")
        } else {
             Log.w(LOG_TAG, "⚠️ NoiseSuppressor NOT available")
        }
    }

    fun stopRecording() {
        try {
            recordingJob?.cancel()
            
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.stop()
            }
            
            audioRecord?.release()
            audioRecord = null
            
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null
            
            noiseSuppressor?.release()
            noiseSuppressor = null
            
            // Restore original audio mode
            audioManager.mode = originalAudioMode
            Log.d(LOG_TAG, "Recording stopped and resources released")
            
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error stopping recording", e)
        }
    }
}
