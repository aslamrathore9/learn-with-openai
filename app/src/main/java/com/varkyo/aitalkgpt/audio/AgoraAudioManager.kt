package com.varkyo.aitalkgpt.audio

import android.content.Context
import android.util.Log
import io.agora.rtc2.Constants
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.audio.AudioParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/**
 * Manages Agora RTC Engine for real-time audio communication.
 * Handles:
 * - Engine Initialization
 * - Channel Join/Leave
 * - Audio Frame Observation (getting raw audio)
 */
class AgoraAudioManager(context: Context) {

    private var rtcEngine: RtcEngine? = null
    private var recordFrameCount = 0 // Debug counter
    private val appId = "4b319c885d854bcb984b0efb6553f1c1" // From AGORA_INTEGRATION.md (Assuming this is the user's ID)
    // NOTE: In production, do NOT hardcode. But for this migration, we use the known ID.
    
    private val _isJoined = MutableStateFlow(false)
    val isJoined: StateFlow<Boolean> = _isJoined.asStateFlow()

    // Callback to send audio data to ViewModel
    var onAudioDataReceived: ((ByteArray) -> Unit)? = null

    private val audioFrameObserver = object : IAudioFrameObserver {
        override fun onRecordAudioFrame(
            channelId: String?,
            type: Int,
            samplesPerChannel: Int,
            bytesPerSample: Int,
            channels: Int,
            samplesPerSec: Int,
            buffer: ByteBuffer?,
            renderTimeMs: Long,
            avsync_type: Int
        ): Boolean {
            if (buffer != null) {
                // DEBUG: Log every 50 frames (approx 1 sec at 20ms/frame)
                if (recordFrameCount % 50 == 0) { 
                     Log.d("AgoraAudioManager", "🎤 Audio Frame Received: ${buffer.remaining()} bytes (Frame #$recordFrameCount)")
                }
                recordFrameCount++
                
                val len = buffer.remaining()
                val data = ByteArray(len)
                buffer.get(data)
                onAudioDataReceived?.invoke(data)
            }
            return true
        }

        override fun onPlaybackAudioFrame(channelId: String?, type: Int, samplesPerChannel: Int, bytesPerSample: Int, channels: Int, samplesPerSec: Int, buffer: ByteBuffer?, renderTimeMs: Long, avsync_type: Int): Boolean = true
        override fun onMixedAudioFrame(channelId: String?, type: Int, samplesPerChannel: Int, bytesPerSample: Int, channels: Int, samplesPerSec: Int, buffer: ByteBuffer?, renderTimeMs: Long, avsync_type: Int): Boolean = true
        
        // Corrected Signature: avsync_type instead of syncTimeMs
        override fun onEarMonitoringAudioFrame(type: Int, samplesPerChannel: Int, bytesPerSample: Int, channels: Int, samplesPerSec: Int, buffer: ByteBuffer?, renderTimeMs: Long, avsync_type: Int): Boolean = true
        
        override fun onPlaybackAudioFrameBeforeMixing(channelId: String?, uid: Int, type: Int, samplesPerChannel: Int, bytesPerSample: Int, channels: Int, samplesPerSec: Int, buffer: ByteBuffer?, renderTimeMs: Long, avsync_type: Int, rtpTimestamp: Int, presentationMs: Long): Boolean = true
      //  override fun onPositionIndication(position: Float): Int = 0

        // Implement missing abstract members
        // Implement missing abstract members
        // enable valid positions (Record=1<<0, Playback=1<<1?? No, usually Record is 1<<2 or similar)
        // Safe bet: Return all positions or a large mask 
        // Return all positions to ensure we catch the relevant one
        // Record(1<<0), Playback(1<<1), Mixed(1<<2), BeforeMixing(1<<3) -> 15
        override fun getObservedAudioFramePosition(): Int = 15 // Check all positions
        
        // Match parameters with setRecordingAudioFrameParameters (16000, 1, 320)
        // 320 samples samplesAt 16kHz = 20ms duration
        override fun getRecordAudioParams(): AudioParams? = AudioParams(16000, 1, 0, 320)
        override fun getPlaybackAudioParams(): AudioParams? = AudioParams(16000, 1, 0, 320)
        override fun getMixedAudioParams(): AudioParams? = AudioParams(16000, 1, 0, 320)
        override fun getEarMonitoringAudioParams(): AudioParams? = AudioParams(16000, 1, 0, 320)
    }

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.d("AgoraAudioManager", "Joined channel: $channel, uid: $uid")
            _isJoined.value = true
            rtcEngine?.setEnableSpeakerphone(true) // Ensure speaker is used
        }

        override fun onLeaveChannel(stats: RtcStats?) {
            Log.d("AgoraAudioManager", "Left channel")
            _isJoined.value = false
        }

        override fun onError(err: Int) {
            Log.e("AgoraAudioManager", "Agora Error code: $err")
        }
        
        override fun onAudioVolumeIndication(speakers: Array<out io.agora.rtc2.IRtcEngineEventHandler.AudioVolumeInfo>?, totalVolume: Int) {
            // Check if we have volume > 0 to confirm mic is working
            if (totalVolume > 5) { // Noise floor
                 if (System.currentTimeMillis() % 2000 < 50) {
                     Log.d("AgoraAudioManager", "🔊 Mic Volume: $totalVolume (Microphone is working!)")
                 }
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            Log.d("AgoraAudioManager", "Connection state changed: $state, reason: $reason")
            if (state == Constants.CONNECTION_STATE_FAILED) {
                 Log.e("AgoraAudioManager", "❌ Connection FAILED. Reason: $reason")
                 _isJoined.value = false
            }
        }
    }

    init {
        try {
            val config = RtcEngineConfig()
            config.mContext = context
            config.mAppId = appId
            config.mEventHandler = eventHandler
            rtcEngine = RtcEngine.create(config)
            
            // Enable Audio Only
            rtcEngine?.enableAudio()
            rtcEngine?.disableVideo()
            rtcEngine?.enableAudioVolumeIndication(200, 3, true) // Report volume every 200ms
            
            // CRITICAL: Enable Echo Cancellation to prevent AI voice from being picked up by mic
            // This allows us to detect real human speech vs device speaker output
            rtcEngine?.setParameters("{\"che.audio.enable.aec\":true}")  // Acoustic Echo Cancellation
            rtcEngine?.setParameters("{\"che.audio.enable.ns\":true}")   // Noise Suppression
            rtcEngine?.setParameters("{\"che.audio.enable.agc\":true}")  // Automatic Gain Control
            
            Log.d("AgoraAudioManager", "✅ Echo Cancellation ENABLED - AI voice will not trigger interruption")
            
            // Set Audio Profile for better quality
            rtcEngine?.setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT, Constants.AUDIO_SCENARIO_DEFAULT)
            rtcEngine?.setClientRole(Constants.CLIENT_ROLE_BROADCASTER) // Critical: Must be Broadcaster to send audio
            
            // Register Audio Frame Observer to get raw data
            val regResult = rtcEngine?.registerAudioFrameObserver(audioFrameObserver)
            Log.d("AgoraAudioManager", "AudioFrameObserver registration result: $regResult")
            
            // Set params: 16kHz, 1 channel, RAW_AUDIO_FRAME_OP_MODE_READ_ONLY(0)
            // Use samplesPerCall = 0 (SDK default) or 320 (20ms) to ensure smooth callbacks
            // DEBUG: Commenting out explicit params to rely on defaults first
            rtcEngine?.setRecordingAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 320)
            rtcEngine?.setPlaybackAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 320)
            rtcEngine?.setMixedAudioFrameParameters(16000, 1, 320)
            rtcEngine?.setEarMonitoringAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 320)
            
            
        } catch (e: Exception) {
            Log.e("AgoraAudioManager", "Error initializing Agora", e)
        }
    }

    fun joinChannel(token: String, channelName: String, uid: Int) {
        rtcEngine?.joinChannel(token, channelName, "", uid)
    }

    fun leaveChannel() {
        rtcEngine?.leaveChannel()
    }

    fun release() {
        RtcEngine.destroy()
        rtcEngine = null
    }
}
