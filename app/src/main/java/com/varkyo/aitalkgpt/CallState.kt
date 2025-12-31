package com.varkyo.aitalkgpt

/**
 * Represents the different states of the call flow
 */
sealed class CallState {
    // Call Screen states
    object Idle : CallState()
    object Initializing : CallState() // Full screen loading
    object Connecting : CallState()
    
    // Listening Screen state
    data class Listening(
        val isUserSpeaking: Boolean = false,
        val userTranscript: String = ""
    ) : CallState()
    
    // Speaking Screen state
    data class Speaking(
        val aiText: String = "",
        val isComplete: Boolean = false
    ) : CallState()

    // Thinking state (between user speech end and AI response)
    object Thinking : CallState()

    // Paused state - stores the previous state to resume correctly
    data class Paused(val previousState: CallState = Listening()) : CallState()
    
    // Error state
    data class Error(val message: String) : CallState()
}
