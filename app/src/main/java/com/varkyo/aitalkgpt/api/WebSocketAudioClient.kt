package com.varkyo.aitalkgpt.api

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class WebSocketAudioClient(
    private val context: Context,
    private val serverUrl: String
) {
    private val TAG = "WebSocketAudioClient"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Audio Configuration
    private val SAMPLE_RATE_IN_HZ = 16000 // Mic Input
    private val PLAYBACK_SAMPLE_RATE = 24000 // OpenAI TTS Output
    private val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    private val PREFERRED_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG_IN, PREFERRED_AUDIO_FORMAT)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)

    // Callbacks
    var onOpen: (() -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null
    var onFailure: ((String) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    // VAD/State Callbacks
    var onVadSpeechStart: (() -> Unit)? = null
    var onVadSpeechEnd: (() -> Unit)? = null
    var onAiAudioStart: (() -> Unit)? = null
    var onAiAudioEnd: (() -> Unit)? = null


    fun connect() {
        // Ensure ws:// or wss://
        val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://")
        val request = Request.Builder().url(wsUrl).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Server")
                startAudioPlayback() // Ready to play
                scope.launch(Dispatchers.Main) { onOpen?.invoke() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Control Messages
               handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Audio Data (PCM)
                // Log.d(TAG, "Received Audio: ${bytes.size()} bytes")
                writeAudioToTrack(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection Failed", t)
                stopAudioRecording()
                stopAudioPlayback()
                scope.launch(Dispatchers.Main) { onFailure?.invoke(t.message ?: "Unknown Error") }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Connection Closed: $reason")
                stopAudioRecording()
                stopAudioPlayback()
                scope.launch(Dispatchers.Main) { onClosed?.invoke() }
            }
        })
    }

    private fun handleTextMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")

            scope.launch(Dispatchers.Main) {
                when (type) {
                    "vad.speech_start" -> onVadSpeechStart?.invoke()
                    "vad.speech_end" -> onVadSpeechEnd?.invoke()
                    "assistant.audio.start" -> onAiAudioStart?.invoke()
                    "assistant.audio.end" -> onAiAudioEnd?.invoke()
                    else -> onMessage?.invoke(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON Error: $text")
        }
    }

    // ==========================================
    // AUDIO RECORDING (MIC)
    // ==========================================
    @SuppressLint("MissingPermission")
    fun startAudioRecording() {
        if (isRecording.get()) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Echo Cancellation preferred
            SAMPLE_RATE_IN_HZ,
            CHANNEL_CONFIG_IN,
            PREFERRED_AUDIO_FORMAT,
            BUFFER_SIZE * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed")
            return
        }

        audioRecord?.startRecording()
        isRecording.set(true)
        Log.d(TAG, "Mic Recording Started")

        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(BUFFER_SIZE)
            while (isRecording.get()) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // Send RAW PCM to Server
                     webSocket?.send(buffer.toByteString(0, read))
                }
            }
        }
    }

    fun stopAudioRecording() {
        if (!isRecording.get()) return
        isRecording.set(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Stop Mic Error", e)
        }
        audioRecord = null
    }

    // ==========================================
    // AUDIO PLAYBACK (SPEAKER)
    // ==========================================
    private fun startAudioPlayback() {
        if (audioTrack != null) return

        // Configure AudioManager for Speakerphone
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        try {
            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speakerphone", e)
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            PLAYBACK_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) // Use Voice Call volume stream
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(PLAYBACK_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        audioTrack?.play()
        isPlaying.set(true)
        Log.d(TAG, "AudioTrack Started")
    }

    private fun writeAudioToTrack(pcmData: ByteArray) {
        if (audioTrack != null && isPlaying.get()) {
            audioTrack?.write(pcmData, 0, pcmData.size)
        }
    }

    private fun stopAudioPlayback() {
        isPlaying.set(false)
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) { e.printStackTrace() }
        audioTrack = null

        // Restore Audio Settings
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        try {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = android.media.AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore audio settings", e)
        }
    }

    // ==========================================
    // CONTROL
    // ==========================================
    fun sendJson(json: String) {
        webSocket?.send(json)
    }

    fun close() {
        stopAudioRecording()
        stopAudioPlayback()
        webSocket?.close(1000, "User Exit")
        webSocket = null
    }
}
