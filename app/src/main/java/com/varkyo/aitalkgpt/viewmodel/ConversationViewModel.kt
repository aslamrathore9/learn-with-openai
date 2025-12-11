package com.varkyo.aitalkgpt.viewmodel

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

    // Main state for three screens
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private var callStartTime = 0L
    private var currentAiText = ""

    init {
        // Initialize WebRTC Client
        webRTCClient = WebRTCClient(context, serverBaseUrl)
        setupWebRTCCallbacks()
    }

    private fun setupWebRTCCallbacks() {
        webRTCClient?.onDataChannelMessage = { message ->
            handleDataChannelMessage(message)
        }
        
        webRTCClient?.onConnectionStateChange = { state ->
            Log.d("ConversationViewModel", "WebRTC State: $state")
            if (state == org.webrtc.PeerConnection.PeerConnectionState.CONNECTED) {
                 _callState.value = CallState.Listening()
                 callStartTime = System.currentTimeMillis()
            } else if (state == org.webrtc.PeerConnection.PeerConnectionState.FAILED) {
                 _callState.value = CallState.Error("Connection Failed")
            }
        }
    }

    private fun handleDataChannelMessage(message: String) {
        try {
            // Parse OpenAI Realtime Events (JSON)
            // Example: "response.audio_transcript.delta"
            // Note: In WebRTC, functionality is similar but events come over Data Channel
            Log.d("ConversationViewModel", "Event: $message")
            
            // Allow manual Close logic if needed
        } catch (e: Exception) {
            Log.e("ConversationViewModel", "JSON Parse Error", e)
        }
    }

    fun startCall() {
        val currentState = _callState.value
        if (currentState !is CallState.Idle && currentState !is CallState.Error) return

        _callState.value = CallState.Connecting
        _callDurationSeconds.value = 0L
        currentAiText = ""

        Log.d("ConversationViewModel", "🚀 Starting WebRTC Call...")
        webRTCClient?.startSession()
    }

    fun endCall() {
        viewModelScope.launch {
            Log.d("ConversationViewModel", "🛑 End call requested...")
            _callState.value = CallState.Idle
            _callDurationSeconds.value = 0L
            
            webRTCClient?.close()
            // Re-init for next call
            webRTCClient = WebRTCClient(context, serverBaseUrl)
            setupWebRTCCallbacks()
        }
    }

    override fun onCleared() {
        super.onCleared()
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