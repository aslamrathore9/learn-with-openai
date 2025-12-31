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
    // WebSocket Manager
    private var audioClient: com.varkyo.aitalkgpt.api.WebSocketAudioClient? = null

    // Ringing Sound
    private val toneGenerator =
        android.media.ToneGenerator(android.media.AudioManager.STREAM_VOICE_CALL, 100)

    // Main state for three screens
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private val _currentTopicTitle = MutableStateFlow("Talk about anything")
    val currentTopicTitle: StateFlow<String> = _currentTopicTitle.asStateFlow()

    private var currentAiText = ""
    
    // Flag to prevent state changes during cleanup
    private var isEnding = false

    init {
        // Initialize Client
        ensureClient()
    }

    private fun ensureClient() {
        if (audioClient == null) {
            audioClient = com.varkyo.aitalkgpt.api.WebSocketAudioClient(context, serverBaseUrl)
        }
        
        audioClient?.onOpen = {
            Log.d("ConversationViewModel", "WebSocket Connected")
            stopRinging()
            
            // Trigger Greeting
            val topicTitle = selectedTopic?.title ?: "English Practice"
             val goal = when {
                selectedTopic?.id == "improve_vocabulary" -> "improve your vocabulary"
                selectedTopic?.category == "Interview" -> "prepare for your interview"
                else -> "improve your English fluency"
            }
            
            val configMsg = """{"type": "config", "topic": "$topicTitle"}"""
            audioClient?.sendJson(configMsg)
            
            val greetingMsg = """{"type": "greeting"}"""
            audioClient?.sendJson(greetingMsg)
            
            // Set to Thinking while waiting for Greeting
            // Do NOT set to Thinking here. Stay in Initializing until audio starts.
            // _callState.value = CallState.Thinking
        }
        
        audioClient?.onFailure = { error ->
             Log.e("ConversationViewModel", "Connection Error: $error")
             stopRinging()
             if (!isEnding) {
                 _callState.value = CallState.Error("Connection Failed")
             }
        }
        
        audioClient?.onVadSpeechStart = {
             Log.d("ConversationViewModel", "User Speech Started")
             if (!isEnding && _callState.value is CallState.Listening) {
                 _callState.value = CallState.Listening(isUserSpeaking = true)
             }
        }
        
        audioClient?.onVadSpeechEnd = {
             Log.d("ConversationViewModel", "User Speech Ended")
             if (!isEnding) {
                  val currentState = _callState.value
                  if (currentState is CallState.Listening) {
                      // Switch to Thinking immediately when VAD detects silence
                      _callState.value = CallState.Thinking
                  } else if (currentState is CallState.Paused && currentState.previousState is CallState.Listening) {
                      // Update pending state
                      _callState.value = currentState.copy(previousState = CallState.Thinking)
                  }
             }
        }
        
        audioClient?.onMessage = { text ->
            try {
                val json = org.json.JSONObject(text)
                val msgType = json.optString("type")
                
                if (msgType == "assistant.response.text") {
                     val textContent = json.optString("text")
                     currentAiText = textContent
                     
                     // Update state immediately if speaking or paused(speaking)
                     val currentState = _callState.value
                     if (currentState is CallState.Speaking) {
                         _callState.value = currentState.copy(aiText = textContent)
                     } else if (currentState is CallState.Paused && currentState.previousState is CallState.Speaking) {
                         _callState.value = currentState.copy(
                             previousState = currentState.previousState.copy(aiText = textContent)
                         )
                     }
                } else if (msgType == "assistant.thinking") {
                     // Server confirm it heard us. Ensure we are in Thinking.
                     if (!isEnding) {
                         _callState.value = CallState.Thinking
                     }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        audioClient?.onAiAudioStart = {
             // AI is speaking
             if (!isEnding) {
                 val currentState = _callState.value
                 if (currentState is CallState.Paused) {
                     // AI started speaking while paused (or buffered), so next state is Speaking
                     _callState.value = currentState.copy(previousState = CallState.Speaking(aiText = currentAiText))
                 } else {
                     _callState.value = CallState.Speaking(aiText = currentAiText)
                 }
             }
        }
        
        audioClient?.onAiAudioEnd = {
             // AI finished
             Log.d("ConversationViewModel", "AI Finished Speaking")
             if (!isEnding) {
                 val currentState = _callState.value
                 if (currentState is CallState.Paused) {
                     // If paused, update the preserved state to indicate completion but don't switch yet
                     if (currentState.previousState is CallState.Speaking) {
                         _callState.value = currentState.copy(
                             previousState = currentState.previousState.copy(isComplete = true)
                         )
                     } else {
                        // Fallback
                        _callState.value = currentState.copy(previousState = CallState.Listening())
                     }
                 } else {
                     viewModelScope.launch {
                         // Small delay to let UI settle?
                         kotlinx.coroutines.delay(500)
                         if (!isEnding) {
                             // Re-check state in case user paused during delay
                             val freshState = _callState.value
                             if (freshState is CallState.Paused) {
                                  _callState.value = freshState.copy(previousState = CallState.Listening())
                             } else {
                                  _callState.value = CallState.Listening()
                                  // Ensure mic is on
                                  startRecordingSafe()
                             }
                         }
                     }
                 }
             }
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
        isEnding = false // Reset flag when starting new call
        _callState.value = CallState.Initializing
        _callDurationSeconds.value = 0L
        _currentTopicTitle.value = topicToUse.title
        currentAiText = ""

        ensureClient()

        Log.d("ConversationViewModel", "🚀 Starting Call (Standard API) for topic: ${topicToUse.title}")
        startRinging()
        audioClient?.connect()
    }
    
    private fun startRecordingSafe() {
        // We can choose to stop recording when AI speaks to prevent echo if we want
        // For now, let's keep it simple: Start if not started.
        audioClient?.startAudioRecording()
    }

    private fun startRinging() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
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

    fun pauseCall() {
        val currentState = _callState.value
        
        // Don't pause if we're initializing, connecting, or in error state
        if (currentState is CallState.Initializing || 
            currentState is CallState.Connecting ||
            currentState is CallState.Error ||
            currentState is CallState.Paused) {
            return
        }
        
        // Store current state and pause
        _callState.value = CallState.Paused(previousState = currentState)
        stopRinging()
        audioClient?.stopAudioRecording()
        audioClient?.pauseAudioPlayback()
        
        Log.d("ConversationViewModel", "Call paused from state: $currentState")
    }

    fun resumeCall() {
        val currentState = _callState.value
        
        if (currentState is CallState.Paused) {
            val previousState = currentState.previousState
            
            // Restore the previous state
            _callState.value = previousState
            
            // Resume appropriate actions based on previous state
            when (previousState) {
                is CallState.Listening -> {
                    startRecordingSafe()
                }
                is CallState.Speaking -> {
                    audioClient?.resumeAudioPlayback()
                    // If audio finished while paused (isComplete), we need to transition to Listening
                    // Since we've updated WebSocketAudioClient to sync onAiAudioEnd with playback completion,
                    // we can rely on onAiAudioEnd firing when the queue empties.
                    if (previousState.isComplete) {
                        // If it was already complete, it means the server finished.
                        // The playback queue might still have data (which we are now playing).
                        // When that empties, onAiAudioEnd will fire, and we'll transition there.
                        // So we just play and wait.
                    }
                }
                is CallState.Thinking -> {
                    // Just wait, no action needed
                }
                else -> {
                    // Default to listening if previous state was unexpected
                    _callState.value = CallState.Listening()
                    startRecordingSafe()
                }
            }
            
            Log.d("ConversationViewModel", "Call resumed to state: $previousState")
        }
    }

    fun continueConversation() {
         val currentState = _callState.value
         
         if (currentState is CallState.Speaking) {
             // Stop Lyra AI (Interrupt) -> Switch to User
             audioClient?.interruptAudioPlayback()
             
             // We should also probably tell the server to stop generating?
             // Since we don't have a direct "cancel" message defined in standard usage yet, we just rely on VAD or silence.
             // But stopping playback and switching to listening is good.
             
             _callState.value = CallState.Listening()
             startRecordingSafe()
             
         } else if (currentState is CallState.Listening) {
             // User already speaking -> Listen again (Restart)
             // Stop and Start to ensure fresh state/VAD reset if needed
             audioClient?.stopAudioRecording()
             
             // Small delay to ensure clean restart?
             // Or just start immediately. WebSocketAudioClient checks isRecording flag. 
             // We can do a coroutine launch to create a small gap if needed.
             viewModelScope.launch {
                 // kotlin.io.println("Restarting listening...")
                 // delay(50) 
                 startRecordingSafe()
                 // Ensure state is Listening (it already is, but just in case)
                 _callState.value = CallState.Listening(isUserSpeaking = false) // Reset speaking indicator? "listen again"
             }
         } else if (currentState !is CallState.Paused && currentState !is CallState.Initializing) {
              // Default behavior for other states (Thinking, etc.)
            _callState.value = CallState.Listening(isUserSpeaking = false) 
            startRecordingSafe()
         }
         // Paused handled in UI
    }

    fun endCall() {
        viewModelScope.launch {
            Log.d("ConversationViewModel", "🛑 End call requested...")
            isEnding = true // Set flag to prevent callbacks from changing state
            stopRinging()
            
            // Close audio client first
            audioClient?.close()
            audioClient = null
            
            // Then update state - this ensures no callbacks fire after
            _callState.value = CallState.Idle
            _callDurationSeconds.value = 0L
            currentAiText = ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRinging()
        toneGenerator.release()
        audioClient?.close()
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