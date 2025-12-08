package com.varkyo.aitalkgpt

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.varkyo.aitalkgpt.api.ApiClient
import com.varkyo.aitalkgpt.audio.AudioPlayer
import com.varkyo.aitalkgpt.audio.AgoraAudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for English Learning App with Three-Screen Flow:
 * Call Screen → Listening Screen → Speaking Screen (cycle)
 * 
 * FACTORY REQUIRED: Because AgoraAudioManager needs Context.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ConversationViewModel(
    private val context: Context,
    private val serverBaseUrl: String = "https://my-server-openai-production.up.railway.app"
) : ViewModel() {

    // Managers
    private val agoraAudioManager = AgoraAudioManager(context)
    private val apiClient = ApiClient(serverBaseUrl)
    private val audioPlayer = AudioPlayer(context)

    // Main state for three screens
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private var callStartTime = 0L
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    
    // Track AI speaking to prevent feedback loop
    private var isAiSpeaking = false
    private var lastAudioReceivedTime = 0L
    private var currentAiText = ""

    init {
        // Observe Audio from Agora
        agoraAudioManager.onAudioDataReceived = { data ->
             val currentState = _callState.value
             
             // ONLY send audio when in Listening state and AI is NOT speaking
             // This prevents the AI's voice (played through AudioTrack) from being
             // picked up by the microphone and sent back to the server
             if (currentState is CallState.Listening && !isAiSpeaking) {
                 apiClient.sendRealtimeAudio(data)
             }
             // When AI is speaking, we don't send mic input to prevent echo/feedback
             // The OpenAI Realtime API handles turn-taking automatically
        }
    }
    
    fun startCall() {
        val currentState = _callState.value
        if (currentState !is CallState.Idle && currentState !is CallState.Error) return

        _callState.value = CallState.Connecting
        _callDurationSeconds.value = 0L
        conversationHistory.clear()
        currentAiText = ""
        
        Log.d("ConversationViewModel", "🚀 Starting call - connecting to WebSocket...")
        
        // Start Realtime Connection
        apiClient.connectRealtime(object : ApiClient.RealtimeCallback {
            override fun onAudioDelta(base64Audio: String) {
                Log.d("ConversationViewModel", "🔊 Received audio delta: ${base64Audio.length} chars")
                try {
                    // Mark that AI is speaking
                    isAiSpeaking = true
                    lastAudioReceivedTime = System.currentTimeMillis()
                    
                    val bytes = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
                    audioPlayer.playChunk(bytes)
                    
                    // Transition to Speaking screen if not already there
                    if (_callState.value !is CallState.Speaking) {
                         _callState.value = CallState.Speaking(aiText = currentAiText)
                    }
                } catch (e: Exception) {
                    Log.e("ConversationViewModel", "Audio decode error", e)
                    isAiSpeaking = false
                }
            }
            
            override fun onUserTranscript(text: String) {
                Log.d("ConversationViewModel", "📝 User transcript: $text")
                // Update Listening state with user's transcript
                val currentState = _callState.value
                if (currentState is CallState.Listening) {
                    _callState.value = currentState.copy(
                        isUserSpeaking = true,
                        userTranscript = text
                    )
                }
            }
            
            override fun onAiTranscript(text: String) {
                Log.d("ConversationViewModel", "🤖 AI transcript: $text")
                currentAiText += text
                
                // Update Speaking state with streaming text
                val currentState = _callState.value
                if (currentState is CallState.Speaking) {
                    _callState.value = currentState.copy(aiText = currentAiText)
                } else {
                    // Transition to Speaking if we receive AI text
                    _callState.value = CallState.Speaking(aiText = currentAiText)
                }
            }
            
            override fun onResponseComplete() {
                Log.d("ConversationViewModel", "✅ AI response complete - returning to Listening immediately")
                isAiSpeaking = false
                // Return to Listening screen immediately
                _callState.value = CallState.Listening()
                currentAiText = "" // Reset for next response
            }
            
            override fun onError(msg: String) {
                Log.e("ConversationViewModel", "❌ WebSocket error: $msg")
                _callState.value = CallState.Error(msg)
            }
        })
        
        fetchTokenAndJoin()
    }
    
    private fun fetchTokenAndJoin() {
        viewModelScope.launch {
            val channelName = "conversation_v1"
            val uid = (System.currentTimeMillis() % 100000).toInt() + 1 
            
            Log.d("ConversationViewModel", "Fetching Agora token for channel: $channelName, uid: $uid")
            
            val timeoutJob = launch {
                kotlinx.coroutines.delay(30000)
                if (_callState.value is CallState.Connecting) {
                    Log.e("ConversationViewModel", "❌ Connection timed out")
                    _callState.value = CallState.Error("Connection timed out. Please check your internet or try again.")
                }
            }

            val result = apiClient.getToken(channelName, uid)
            timeoutJob.cancel()
            
            result.onSuccess { token ->
                Log.d("ConversationViewModel", "✅ Token received, joining channel...")
                agoraAudioManager.joinChannel(token, channelName, uid)
                
                // Transition to Listening screen
                _callState.value = CallState.Listening()
                callStartTime = System.currentTimeMillis()
                
            }.onFailure { e ->
                Log.e("ConversationViewModel", "❌ Failed to get token", e)
                _callState.value = CallState.Error("Failed to connect: ${e.message}")
            }
        }
    }

    fun endCall() {
        viewModelScope.launch {
            Log.d("ConversationViewModel", "🛑 End call requested - cleaning up...")
            Log.d("ConversationViewModel", "Current state: ${_callState.value}")
            
            // Immediately transition to Idle state to update UI
            _callState.value = CallState.Idle
            Log.d("ConversationViewModel", "State changed to: ${_callState.value}")
            
            // Reset all state variables
            currentAiText = ""
            isAiSpeaking = false
            conversationHistory.clear()
            _callDurationSeconds.value = 0L
            callStartTime = 0L
            
            // Clean up resources in order
            try {
                audioPlayer.stop()
                Log.d("ConversationViewModel", "✅ AudioPlayer stopped")
            } catch (e: Exception) {
                Log.e("ConversationViewModel", "Error stopping audio player", e)
            }
            
            try {
                apiClient.disconnectRealtime()
                Log.d("ConversationViewModel", "✅ WebSocket disconnected")
            } catch (e: Exception) {
                Log.e("ConversationViewModel", "Error disconnecting WebSocket", e)
            }
            
            try {
                agoraAudioManager.leaveChannel()
                Log.d("ConversationViewModel", "✅ Agora channel left")
            } catch (e: Exception) {
                Log.e("ConversationViewModel", "Error leaving Agora channel", e)
            }
            
            Log.d("ConversationViewModel", "✅ Call ended successfully")
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        agoraAudioManager.release()
        audioPlayer.release()
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
