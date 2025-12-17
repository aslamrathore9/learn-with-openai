package com.varkyo.aitalkgpt.utils

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

object SoundManager {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.e("SoundManager", "Failed to initialize ToneGenerator", e)
        }
    }

    fun playAiTurnSound() {
        // Higher pitch or distinct tone for AI
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 150) 
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing AI sound", e)
        }
    }

    fun playUserTurnSound() {
        // Different tone for User
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing User sound", e)
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
