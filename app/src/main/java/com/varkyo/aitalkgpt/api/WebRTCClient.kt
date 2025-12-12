package com.varkyo.aitalkgpt.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.json.JSONObject

class WebRTCClient(
    private val context: Context,
    private val serverBaseUrl: String
) {

    private val TAG = "WebRTCClient"
    private val httpClient = OkHttpClient()
    private val gson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true } // Use manual JSON for now if needed

    // WebRTC Components
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private val eglBase = EglBase.create()

    // Callbacks


    private val scope = CoroutineScope(Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var originalAudioMode = android.media.AudioManager.MODE_NORMAL

    init {
        initWebRTC()
    }

    private fun initWebRTC() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

     fun startSession() {
        // Set Audio Mode to COMMUNICATION for WebRTC AEC/NS to work best
        originalAudioMode = audioManager.mode
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        
        scope.launch {
            try {
                Log.d(TAG, "Fetching ephemeral token...")
                val tokenData = fetchToken()
               // val ephemeralKey = tokenData.getString("client_secret") { "client_secret" } // Pseudo-code manual extraction
                
                // Real implementation:
                val json = JSONObject(tokenData)
                val clientSecret = json.getJSONObject("client_secret").getString("value")
                
                Log.d(TAG, "Token received. Creating PeerConnection...")
                createPeerConnection()
                
                // Add Local Audio Track (Microphone)
                val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
                val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
                peerConnection?.addTrack(localAudioTrack, listOf("streamId"))

                // Create Data Channel for control events
                val dcInit = DataChannel.Init()
                val dataChannel = peerConnection?.createDataChannel("oai-events", dcInit)
                setupDataChannel(dataChannel)

                // Create Offer
                Log.d(TAG, "Creating SDP Offer...")
                peerConnection?.createOffer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc?.let {
                            peerConnection?.setLocalDescription(this, it)
                            // Send Offer to OpenAI
                            sendOfferToOpenAI(it.description, clientSecret)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(s: String?) { Log.e(TAG, "Create Offer Failed: $s") }
                    override fun onSetFailure(s: String?) {}
                }, MediaConstraints())

            } catch (e: Exception) {
                Log.e(TAG, "Session start failed", e)
            }
        }
    }

    private suspend fun fetchToken(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$serverBaseUrl/session")
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Failed to get token: ${response.code}")
        response.body?.string() ?: throw Exception("Empty token response")
    }

    private fun sendOfferToOpenAI(sdpOffer: String, clientSecret: String) {
        scope.launch {
            try {
                val url = "https://api.openai.com/v1/realtime?model=gpt-4o-mini-realtime-preview"
                val body = sdpOffer.toRequestBody("application/sdp".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", "Bearer $clientSecret")
                    .addHeader("Content-Type", "application/sdp")
                    .build()

                Log.d(TAG, "Sending Offer to OpenAI...")
                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                
                if (response.isSuccessful) {
                    val sdpAnswer = response.body?.string()
                    Log.d(TAG, "Received Answer from OpenAI. Setting Remote Description...")
                    
                    val sessionDesc = SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
                    withContext(Dispatchers.Main) { // WebRTC usually prefers Main/Worker thread
                         peerConnection?.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() { Log.d(TAG, "Remote Description Set!") }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) { Log.e(TAG, "Set Remote Failed: $p0") }
                        }, sessionDesc)
                    }
                } else {
                    Log.e(TAG, "OpenAI Handshake Failed: ${response.code} ${response.body?.string()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "SDP Exchange Failed", e)
            }
        }
    }

    private fun createPeerConnection() {
        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
               Log.d(TAG, "ICE State: $newState")
               newState?.let {
                   val peerState = when (it) {
                       PeerConnection.IceConnectionState.CONNECTED,
                       PeerConnection.IceConnectionState.COMPLETED -> PeerConnection.PeerConnectionState.CONNECTED
                       PeerConnection.IceConnectionState.FAILED -> PeerConnection.PeerConnectionState.FAILED
                       PeerConnection.IceConnectionState.DISCONNECTED -> PeerConnection.PeerConnectionState.DISCONNECTED
                       PeerConnection.IceConnectionState.CLOSED -> PeerConnection.PeerConnectionState.CLOSED
                       else -> PeerConnection.PeerConnectionState.CONNECTING
                   }
                   // Dispatch to Main Thread to be safe, though ViewModel uses Flow
                   scope.launch(Dispatchers.Main) {
                       onConnectionStateChange?.invoke(peerState)
                   }
               }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(p0: IceCandidate?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "Stream Added! Tracks: ${stream?.audioTracks?.size}")
                if (stream?.audioTracks?.isNotEmpty() == true) {
                    onRemoteAudioTrack?.invoke(stream.audioTracks[0])
                }
            }
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {
                Log.d(TAG, "New Data Channel: ${dc?.label()}")
                setupDataChannel(dc)
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                 // onAddTrack is often preferred over onAddStream in newer WebRTC
                 receiver?.track()?.let { track ->
                     if (track.kind() == "audio") {
                         onRemoteAudioTrack?.invoke(track as AudioTrack)
                     }
                 }
            }
        })
    }

    // Callbacks
    var onRemoteAudioTrack: ((AudioTrack) -> Unit)? = null
    var onDataChannelMessage: ((String) -> Unit)? = null
    var onConnectionStateChange: ((PeerConnection.PeerConnectionState) -> Unit)? = null
    var onUserSpeechStart: (() -> Unit)? = null
    var onUserSpeechStop: (() -> Unit)? = null
    var onAiSpeechEnd: (() -> Unit)? = null

    private fun setupDataChannel(dc: DataChannel?) {
        dc?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() { Log.d(TAG, "DataChannel State: ${dc.state()}") }
            override fun onMessage(buffer: DataChannel.Buffer?) {
                val data = buffer?.data
                val bytes = ByteArray(data?.remaining() ?: 0)
                data?.get(bytes)
                val text = String(bytes)
                
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    
                    if (type == "input_audio_buffer.speech_started") {
                        Log.d(TAG, "🛑 User started speaking (Server VAD)")
                        scope.launch(Dispatchers.Main) {
                             onUserSpeechStart?.invoke()
                        }
                    } else if (type == "input_audio_buffer.speech_stopped") {
                        Log.d(TAG, "User stopped speaking")
                         scope.launch(Dispatchers.Main) {
                             onUserSpeechStop?.invoke()
                        }
                    } else if (type == "response.done") {
                        Log.d(TAG, "✅ AI finished speaking (response.done)")
                        scope.launch(Dispatchers.Main) {
                            onAiSpeechEnd?.invoke()
                        }
                    }
                    
                    // Forward full message just in case
                    scope.launch(Dispatchers.Main) {
                        onDataChannelMessage?.invoke(text)
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "JSON Parse Error on DC", e)
                }
            }
        })
    }

    fun close() {
        try {
            audioManager.mode = originalAudioMode
            peerConnection?.close()
            peerConnectionFactory.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebRTC", e)
        }
    }
}
