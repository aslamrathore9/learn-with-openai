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
             _callState.value = CallState.Error("Connection Failed")
        }
        
        audioClient?.onVadSpeechStart = {
             Log.d("ConversationViewModel", "User Speech Started")
             if (_callState.value is CallState.Listening) {
                 _callState.value = CallState.Listening(isUserSpeaking = true)
             }
        }
        
        audioClient?.onVadSpeechEnd = {
             Log.d("ConversationViewModel", "User Speech Ended")
             if (_callState.value is CallState.Listening) {
                  // Switch to Thinking immediately when VAD detects silence
                  _callState.value = CallState.Thinking
             }
        }
        
        audioClient?.onMessage = { text ->
            try {
                val json = org.json.JSONObject(text)
                val msgType = json.optString("type")
                
                if (msgType == "assistant.response.text") {
                     val textContent = json.optString("text")
                     currentAiText = textContent
                     // We might already be in Thinking, stay there until audio starts or update text
                     // Actually, if we get text, we can show it while "Thinking" or just wait for audio.
                     // Let's wait for audio to switch to Speaking state, 
                     // BUT we can update the Thinking UI with the text if we want "Streamed Text" effect?
                     // Request was: "show progress bar and show text below thinking".
                     // So maybe Thinking state should hold the text?
                     // For now, let's keep Thinking generic.
                } else if (msgType == "assistant.thinking") {
                     // Server confirm it heard us. Ensure we are in Thinking.
                     _callState.value = CallState.Thinking
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        audioClient?.onAiAudioStart = {
             // AI is speaking
              _callState.value = CallState.Speaking(aiText = currentAiText)
        }
        
        audioClient?.onAiAudioEnd = {
             // AI finished
             Log.d("ConversationViewModel", "AI Finished Speaking")
             viewModelScope.launch {
                 // Small delay to let UI settle?
                  kotlinx.coroutines.delay(500)
                  _callState.value = CallState.Listening()
                  // Ensure mic is on
                  startRecordingSafe()
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
        _callState.value = CallState.Paused
        stopRinging()
        audioClient?.stopAudioRecording()
    }

    fun resumeCall() {
         if (_callState.value is CallState.Paused) {
            _callState.value = CallState.Listening()
            startRecordingSafe()
        }
    }

    fun continueConversation() {
         if (_callState.value !is CallState.Speaking) {
            _callState.value = CallState.Listening(isUserSpeaking = true) 
        }
    }

    fun endCall() {
        viewModelScope.launch {
            Log.d("ConversationViewModel", "🛑 End call requested...")
            stopRinging()
            _callState.value = CallState.Idle
            _callDurationSeconds.value = 0L
            audioClient?.close()
            audioClient = null
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