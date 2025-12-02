package com.varkyo.aitalkgpt

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.varkyo.aitalkgpt.api.ApiClient
import com.varkyo.aitalkgpt.api.CorrectionResponse
import com.varkyo.aitalkgpt.audio.AgoraAudioManager
import com.varkyo.aitalkgpt.audio.AudioPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ConversationViewModel - English Learning App with Agora RTC Integration
 * 
 * This ViewModel orchestrates the conversation flow using Agora for real-time audio:
 * Record (Agora) → Transcribe → Get Teacher Feedback → Speak → Play
 * 
 * Flow:
 * 1. User speaks in English (captured via Agora RTC)
 * 2. Audio is transcribed to text (via HTTP API)
 * 3. English teacher corrects grammar/pronunciation and responds (via HTTP API)
 * 4. Teacher's response is converted to speech (via HTTP API)
 * 5. Audio is played back to user (via AudioPlayer)
 * 
 * Benefits of Agora:
 * - Lower latency audio capture
 * - Better audio quality
 * - Real-time streaming capabilities
 * - Improved VAD (Voice Activity Detection)
 */
class ConversationViewModel(
    application: Application,
    private val serverBaseUrl: String = "https://my-server-openai.onrender.com",
    private val agoraAppId: String = "", // Set via constructor or environment
    private val agoraChannelName: String = "english-learning-${System.currentTimeMillis()}" // Unique channel per session
) : AndroidViewModel(application) {

    // Agora Audio Manager for real-time audio streaming
    private val agoraAudioManager = AgoraAudioManager(
        context = application.applicationContext,
        appId = agoraAppId.ifEmpty { getAgoraAppId() },
        channelName = agoraChannelName,
        uid = 0 // Agora will assign a random UID
    )
    
    private val apiClient = ApiClient(serverBaseUrl)
    private val audioPlayer = AudioPlayer()

    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private val _userText = MutableStateFlow<String?>(null)
    val userText: StateFlow<String?> = _userText.asStateFlow()

    private val _correctedText = MutableStateFlow<String?>(null)
    val correctedText: StateFlow<String?> = _correctedText.asStateFlow()

    private val _aiReply = MutableStateFlow<String?>(null)
    val aiReply: StateFlow<String?> = _aiReply.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private var callStartTime = 0L
    
    // Conversation history for context
    private val conversationHistory = mutableListOf<Pair<String, String>>() // (userMessage, aiReply)
    
    // Flag to track if we're in an active conversation
    private var isInConversation = false

    init {
        // Initialize Agora RTC Engine
        val initialized = agoraAudioManager.initialize()
        if (!initialized) {
            Log.e("ConversationViewModel", "Failed to initialize Agora RTC Engine")
            _error.value = "Failed to initialize audio system. Please check Agora App ID."
        }
        
        // Observe audio data from Agora (set up once)
        viewModelScope.launch {
            agoraAudioManager.audioData.collect { audioData ->
                if (audioData != null) {
                    processRecording(audioData)
                }
            }
        }
        
        // Observe Agora connection state
        viewModelScope.launch {
            agoraAudioManager.isConnected.collect { isConnected ->
                if (!isConnected && isInConversation && _state.value != ConversationState.IDLE) {
                    Log.w("ConversationViewModel", "Agora connection lost")
                    handleError("Connection lost. Please try again.")
                }
            }
        }
    }

    /**
     * Get Agora App ID from environment or build config
     * In production, store this securely (e.g., BuildConfig, environment variable)
     */
    private fun getAgoraAppId(): String {
        // TODO: Replace with your actual Agora App ID
        // You can get this from https://console.agora.io/
        // For now, return empty string - user must provide via constructor
        return ""
    }

    /**
     * Start call - begins Agora channel and recording with timer
     * Initializes conversation history for continuous conversation
     */
    fun startCall() {
        if (_state.value != ConversationState.IDLE && _state.value != ConversationState.ERROR) {
            Log.w("ConversationViewModel", "Cannot start call in state: ${_state.value}")
            return
        }

        // Validate Agora App ID
        if (agoraAudioManager.let { 
            val appId = getAgoraAppId()
            appId.isEmpty() && agoraAppId.isEmpty()
        }) {
            handleError("Agora App ID not configured. Please set AGORA_APP_ID.")
            return
        }

        _state.value = ConversationState.RECORDING
        _error.value = null
        _userText.value = null
        _correctedText.value = null
        _aiReply.value = null
        _callDurationSeconds.value = 0L
        callStartTime = System.currentTimeMillis()
        
        // Clear conversation history when starting a new call
        conversationHistory.clear()
        isInConversation = true

        // Start timer for the entire conversation
        viewModelScope.launch {
            while (isInConversation) {
                kotlinx.coroutines.delay(1000)
                val elapsed = (System.currentTimeMillis() - callStartTime) / 1000
                _callDurationSeconds.value = elapsed
            }
        }

        // Join Agora channel (token is optional for testing, required for production)
        val joinResult = agoraAudioManager.joinChannel(token = null)
        if (joinResult != 0) {
            Log.e("ConversationViewModel", "Failed to join Agora channel: $joinResult")
            handleError("Failed to connect to audio channel. Error code: $joinResult")
            return
        }

        // Start audio collection after a short delay to ensure channel is joined
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // Wait for channel join
            if (isInConversation) {
                agoraAudioManager.startAudioCollection()
                Log.d("ConversationViewModel", "Call started - Agora RTC enabled, continuous conversation mode")
            }
        }
    }

    /**
     * Stop call manually - ends the conversation completely
     */
    fun stopCall() {
        Log.d("ConversationViewModel", "User manually ending conversation")
        isInConversation = false
        agoraAudioManager.stopAudioCollection()
        agoraAudioManager.leaveChannel()
        audioPlayer.stop()
        _callDurationSeconds.value = 0L
        conversationHistory.clear()
        _state.value = ConversationState.IDLE
    }

    /**
     * Process recorded audio: Transcribe → Ask → Speak → Play
     * This is called automatically when AgoraAudioManager collects enough audio
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

            // OPTIMIZATION: Limit conversation history to last 5 turns for better performance
            // Server will also limit, but this saves bandwidth on upload
            // 5 turns is optimal for sentence-by-sentence corrections (covers recent context)
            val MAX_HISTORY_TURNS = 5 // Keep last 5 turns (10 messages)
            val limitedHistory = if (conversationHistory.size > MAX_HISTORY_TURNS) {
                conversationHistory.takeLast(MAX_HISTORY_TURNS)
            } else {
                conversationHistory
            }

            // Step 2: Ask AI for correction and reply (with limited conversation history)
            val askResult = apiClient.ask(text, limitedHistory)

            askResult.onSuccess { correctionResponse ->
                // DEBUG: Print AI response
                Log.d("ConversationViewModel", "═══════════════════════════════════════")
                Log.d("ConversationViewModel", "✏️ CORRECTED SENTENCE:")
                Log.d("ConversationViewModel", "   \"${correctionResponse.corrected}\"")
                Log.d("ConversationViewModel", "🤖 AI TUTOR REPLY:")
                Log.d("ConversationViewModel", "   \"${correctionResponse.reply}\"")
                Log.d("ConversationViewModel", "═══════════════════════════════════════")

                _correctedText.value = correctionResponse.corrected
                _aiReply.value = correctionResponse.reply
                
                // Add to conversation history
                conversationHistory.add(Pair(text, correctionResponse.reply))
                
                _state.value = ConversationState.SPEAKING

                // Step 3: Convert AI reply to speech (use the reply text, not the corrected text)
                val speakResult = apiClient.speak(correctionResponse.reply)

                speakResult.onSuccess { mp3Audio ->
                    Log.d("ConversationViewModel", "Audio generated: ${mp3Audio.size} bytes")
                    _state.value = ConversationState.PLAYING

                    // Step 4: Play audio
                    audioPlayer.playMp3(mp3Audio) {
                        // Playback completed
                        // Continue conversation automatically if still in conversation mode
                        // Check both the flag and current state to ensure conversation is still active
                        if (isInConversation && _state.value != ConversationState.IDLE && _state.value != ConversationState.ERROR) {
                            Log.d("ConversationViewModel", "✅ Turn completed, continuing conversation...")
                            // Restart audio collection for the next turn
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(500) // Small delay before restarting
                                // Double-check we're still in conversation before restarting
                                if (isInConversation && _state.value != ConversationState.IDLE && _state.value != ConversationState.ERROR) {
                                    _state.value = ConversationState.RECORDING
                                    agoraAudioManager.startAudioCollection()
                                    Log.d("ConversationViewModel", "🔄 Restarted audio collection for next turn")
                                }
                            }
                        } else {
                            // Conversation ended or error occurred
                            if (!isInConversation) {
                                _state.value = ConversationState.IDLE
                                _callDurationSeconds.value = 0L
                                Log.d("ConversationViewModel", "✅ Conversation ended")
                            }
                        }
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
        if (isInConversation) {
            // If we're in a conversation, restart audio collection
            _error.value = null
            _state.value = ConversationState.RECORDING
            agoraAudioManager.startAudioCollection()
        } else {
            _state.value = ConversationState.IDLE
            _error.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        agoraAudioManager.release()
        audioPlayer.release()
    }
}

/**
 * Conversation states
 */
enum class ConversationState {
    IDLE,           // Ready to record
    RECORDING,      // Recording user's voice (via Agora)
    TRANSCRIBING,   // Converting audio to text
    ASKING,         // Getting AI response
    SPEAKING,       // Converting AI text to speech
    PLAYING,        // Playing AI voice
    ERROR           // Error occurred
}
