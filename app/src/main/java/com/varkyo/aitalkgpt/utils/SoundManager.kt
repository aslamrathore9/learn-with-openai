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
        // AI Turn: "Confirmation" tone (Pleasant rising tone)
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONFIRM, 150) 
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing AI sound", e)
        }
    }

    fun playUserTurnSound() {
        // User Turn: "Pip" tone (Short sharp beep indicating mic is open)
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_PIP, 100)
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing User sound", e)
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
