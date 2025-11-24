package com.varkyo.aitalkgpt


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varkyo.aitalkgpt.audio.MicRecorder
import com.varkyo.aitalkgpt.audio.Player
import com.varkyo.aitalkgpt.realtime.RealtimeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RealtimeViewModel : ViewModel() {

    private val recorder = MicRecorder()
    // FIXED: OpenAI Realtime API uses 24000 Hz for audio output, not 16000 Hz
    private val player = Player(sampleRate = 24000)

    private var client: RealtimeClient? = null

    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    private var sessionToken: String? = null

    fun setSessionToken(token: String) {
        sessionToken = token
    }

    fun startCall() {
        if (sessionToken == null) return

        val c = RealtimeClient(
            sessionToken!!,
            player,
            recorder,
            viewModelScope
        )
        client = c
        recorder.start()
        c.start()

        _running.value = true
    }

    fun stopCall() {
        client?.stop()
        recorder.stop()
        player.stop()
        client = null
        _running.value = false
    }

    override fun onCleared() {
        stopCall()
        super.onCleared()
    }
}
