package com.varkyo.aitalkgpt.viewmodel

import com.varkyo.aitalkgpt.ui.Topic
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.varkyo.aitalkgpt.CallState
import com.varkyo.aitalkgpt.api.WebRTCClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for English Learning App using OpenAI WebRTC
 */
@OptIn(UnstableApi::class)
class ConversationViewModel(
    private val context: Context,
    private val serverBaseUrl: String = "https://my-server-openai-production.up.railway.app"
) : ViewModel() {

    // WebRTC Manager
    private var webRTCClient: WebRTCClient? = null
    
    // Ringing Sound
    private val toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_VOICE_CALL, 100)

    // Main state for three screens
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private var currentAiText = ""

    init {
        // Initialize WebRTC Client
        webRTCClient = WebRTCClient(context, serverBaseUrl)
    }
    
    // Lazy setup to ensure we attach callbacks to the *current* client instance
    private var isCallInitialized = false

    private fun ensureClientAndCallbacks() {
        if (webRTCClient == null) {
            webRTCClient = WebRTCClient(context, serverBaseUrl)
        }
        
        // ... (data channel and speech callbacks same as before) ...
        webRTCClient?.onDataChannelMessage = { message ->
            handleDataChannelMessage(message)
        }

        webRTCClient?.onUserSpeechStart = {
            Log.d("ConversationViewModel", "⚡ Interruption / Speech Start Detected!")
            if (_callState.value is CallState.Speaking) {
                 _callState.value = CallState.Listening(isUserSpeaking = true)
                 currentAiText = ""
            } else if (_callState.value is CallState.Listening) {
                // Update visualizer state
                _callState.value = CallState.Listening(isUserSpeaking = true, userTranscript = ( _callState.value as CallState.Listening).userTranscript)
            }
        }

        webRTCClient?.onUserSpeechStop = {
            Log.d("ConversationViewModel", "Speech Stopped")
             if (_callState.value is CallState.Listening) {
                _callState.value = CallState.Listening(isUserSpeaking = false, userTranscript = ( _callState.value as CallState.Listening).userTranscript)
            }
        }
        
        webRTCClient?.onAiSpeechEnd = {
            Log.d("ConversationViewModel", "✅ AI Finished.")
            // Only switch if we are currently speaking
            if (_callState.value is CallState.Speaking) {
                viewModelScope.launch {
                    // Estimate audio duration: ~70ms per character (avg speech) + 1.5s buffer
                    val textLength = currentAiText.length
                    val estimatedDelay = (textLength * 70L) + 1500L
                    Log.d("ConversationViewModel", "Waiting $estimatedDelay ms for audio to finish (len=$textLength)")
                    
                    kotlinx.coroutines.delay(estimatedDelay) 
                    
                    // Check again in case state changed during delay (e.g. interruption)
                    if (_callState.value is CallState.Speaking) {
                         _callState.value = CallState.Listening()
                         currentAiText = ""
                    }
                }
            }
        }
        
        webRTCClient?.onDataChannelOpen = {
            Log.d("ConversationViewModel", "Data Channel OPEN! Sending Greeting...")
             if (!isCallInitialized) {
                 isCallInitialized = true
                 stopRinging()
                 
                 // Trigger AI Greeting
                 val topicTitle = selectedTopic?.title ?: "English Practice"
                 val greetingInstruction = """
                     {
                         "type": "response.create",
                         "response": {
                             "modalities": ["text", "audio"],
                             "instructions": "Greet the user immediately for the topic '$topicTitle'. Say 'Hello! Welcome to $topicTitle session.' and ask a starting question."
                         }
                     }
                 """.trimIndent()
                 
                 viewModelScope.launch {
                     webRTCClient?.sendMessage(greetingInstruction)
                 }
             }
        }
        
        webRTCClient?.onConnectionStateChange = { state ->
            Log.d("ConversationViewModel", "WebRTC State: $state")
            if (state == org.webrtc.PeerConnection.PeerConnectionState.CONNECTED) {
                 // Stop ringing but wait for DC Open to init session
                 stopRinging()
            } else if (state == org.webrtc.PeerConnection.PeerConnectionState.FAILED) {
                 stopRinging()
                 _callState.value = CallState.Error("Connection Failed")
            }
        }
    }

    private fun handleDataChannelMessage(message: String) {
        try {
             // Parse text delta for Speaking Screen
             val json = org.json.JSONObject(message)
             val type = json.optString("type")
             
             if (type == "response.audio_transcript.delta") {
                 val delta = json.optString("delta")
                 currentAiText += delta
                 if (_callState.value is CallState.Speaking) {
                      _callState.value = CallState.Speaking(aiText = currentAiText)
                 } else {
                      // If we receive text, we should probably be in Speaking state
                      _callState.value = CallState.Speaking(aiText = currentAiText)
                 }
             }
        } catch (e: Exception) {
            Log.e("ConversationViewModel", "JSON Parse Error", e)
        }
    }

    private var selectedTopic: Topic? = null

    fun startCall(topic: Topic? = null) {
        val currentState = _callState.value
        if (currentState !is CallState.Idle && currentState !is CallState.Error) return

        val topicToUse = topic ?: selectedTopic
        if (topicToUse == null) {
            Log.e("ConversationViewModel", "Cannot start call: No topic selected")
            return
        }
        
        selectedTopic = topicToUse
        _callState.value = CallState.Connecting
        _callDurationSeconds.value = 0L
        currentAiText = ""
        isCallInitialized = false
        
        ensureClientAndCallbacks()
        
        Log.d("ConversationViewModel", "🚀 Starting WebRTC Call for topic: ${topicToUse.title}")
        startRinging()
        webRTCClient?.startSession(topicToUse.id)
    }
    
    private fun startRinging() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Play a tone every 2 seconds
                while (_callState.value is CallState.Connecting) {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_SUP_RINGTONE)
                    kotlinx.coroutines.delay(1000)
                    toneGenerator.stopTone()
                    kotlinx.coroutines.delay(2000)
                }
            } catch (e: Exception) {
                Log.e("ConversationViewModel", "Ringing Error", e)
            }
        }
    }
    
    private fun stopRinging() {
        toneGenerator.stopTone()
    }

    fun endCall() {
        viewModelScope.launch {
            Log.d("ConversationViewModel", "🛑 End call requested...")
            stopRinging()
            _callState.value = CallState.Idle
            _callDurationSeconds.value = 0L
            
            webRTCClient?.close()
            webRTCClient = null // Reset
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRinging()
        toneGenerator.release()
        webRTCClient?.close()
    }

    // Factory for ViewModelProvider
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ConversationViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}