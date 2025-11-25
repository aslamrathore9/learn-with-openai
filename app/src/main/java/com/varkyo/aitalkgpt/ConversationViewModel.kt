package com.varkyo.aitalkgpt

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varkyo.aitalkgpt.api.ApiClient
import com.varkyo.aitalkgpt.audio.AudioPlayer
import com.varkyo.aitalkgpt.audio.AudioRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for English Learning App:
 * Record → Transcribe → Get Teacher Feedback → Speak → Play
 * 
 * Flow:
 * 1. User speaks in English
 * 2. Audio is transcribed to text
 * 3. English teacher corrects grammar/pronunciation and responds
 * 4. Teacher's response is converted to speech
 * 5. Audio is played back to user
 */
class ConversationViewModel(
    private val openAiApiKey: String
) : ViewModel() {
    
    private val audioRecorder = AudioRecorder()
    private val apiClient = ApiClient(openAiApiKey)
    private val audioPlayer = AudioPlayer()
    
    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()
    
    private val _userText = MutableStateFlow<String?>(null)
    val userText: StateFlow<String?> = _userText.asStateFlow()
    
    private val _aiText = MutableStateFlow<String?>(null)
    val aiText: StateFlow<String?> = _aiText.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    init {
        // Observe audio data from recorder (set up once)
        viewModelScope.launch {
            audioRecorder.audioData.collect { audioData ->
                if (audioData != null) {
                    processRecording(audioData)
                }
            }
        }
    }
    
    /**
     * Start recording user's voice
     */
    fun startRecording() {
        if (_state.value != ConversationState.IDLE && _state.value != ConversationState.ERROR) {
            Log.w("ConversationViewModel", "Cannot start recording in state: ${_state.value}")
            return
        }
        
        _state.value = ConversationState.RECORDING
        _error.value = null
        _userText.value = null
        _aiText.value = null
        
        audioRecorder.startRecording()
        Log.d("ConversationViewModel", "Recording started")
    }
    
    /**
     * Stop recording manually (if needed)
     */
    fun stopRecording() {
        if (_state.value == ConversationState.RECORDING) {
            audioRecorder.stopRecording()
        }
    }
    
    /**
     * Process recorded audio: Transcribe → Ask → Speak → Play
     */
    private suspend fun processRecording(audioData: ByteArray) {
        Log.d("ConversationViewModel", "Processing audio: ${audioData.size} bytes")
        _state.value = ConversationState.TRANSCRIBING
        
        // Step 1: Transcribe audio to text
        val transcriptionResult = apiClient.transcribe(audioData)
        
        transcriptionResult.onSuccess { text ->
            // DEBUG: Print transcribed text
            Log.d("ConversationViewModel", "═══════════════════════════════════════")
            Log.d("ConversationViewModel", "🎤 YOUR VOICE TRANSCRIBED:")
            Log.d("ConversationViewModel", "   \"$text\"")
            Log.d("ConversationViewModel", "═══════════════════════════════════════")
            
            _userText.value = text
            _state.value = ConversationState.ASKING
            
            // Step 2: Ask AI
            val askResult = apiClient.ask(text)
            
            askResult.onSuccess { aiReply ->
                // DEBUG: Print AI response
                Log.d("ConversationViewModel", "═══════════════════════════════════════")
                Log.d("ConversationViewModel", "🤖 AI TUTOR REPLY:")
                Log.d("ConversationViewModel", "   \"$aiReply\"")
                Log.d("ConversationViewModel", "═══════════════════════════════════════")
                
                _aiText.value = aiReply
                _state.value = ConversationState.SPEAKING
                
                // Step 3: Convert AI reply to speech
                val speakResult = apiClient.speak(aiReply)
                
                speakResult.onSuccess { mp3Audio ->
                    Log.d("ConversationViewModel", "Audio generated: ${mp3Audio.size} bytes")
                    _state.value = ConversationState.PLAYING
                    
                    // Step 4: Play audio
                    audioPlayer.playMp3(mp3Audio) {
                        // Playback completed
                        _state.value = ConversationState.IDLE
                        Log.d("ConversationViewModel", "✅ Conversation cycle completed")
                    }
                }.onFailure { error ->
                    Log.e("ConversationViewModel", "❌ Speech generation failed", error)
                    handleError("Failed to generate speech: ${error.message}")
                }
            }.onFailure { error ->
                Log.e("ConversationViewModel", "❌ AI response failed", error)
                handleError("Failed to get AI response: ${error.message}")
            }
        }.onFailure { error ->
            Log.e("ConversationViewModel", "❌ Transcription failed", error)
            handleError("Failed to transcribe audio: ${error.message}")
        }
    }
    
    /**
     * Handle errors
     */
    private fun handleError(message: String) {
        Log.e("ConversationViewModel", message)
        _error.value = message
        _state.value = ConversationState.ERROR
    }
    
    /**
     * Retry after error
     */
    fun retry() {
        _state.value = ConversationState.IDLE
        _error.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        audioPlayer.release()
    }
}

/**
 * Conversation states
 */
enum class ConversationState {
    IDLE,           // Ready to record
    RECORDING,      // Recording user's voice
    TRANSCRIBING,   // Converting audio to text
    ASKING,         // Getting AI response
    SPEAKING,       // Converting AI text to speech
    PLAYING,        // Playing AI voice
    ERROR           // Error occurred
}

