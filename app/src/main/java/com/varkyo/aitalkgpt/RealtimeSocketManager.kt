//package com.varkyo.aitalkgpt
//
//import android.util.Base64
//import com.varkyo.aitalkgpt.audio.MicRecorder
//import com.varkyo.aitalkgpt.audio.Player
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.Channel
//import kotlinx.coroutines.sync.Mutex
//import kotlinx.coroutines.sync.withLock
//import kotlinx.serialization.json.Json
//import kotlinx.serialization.json.contentOrNull
//import kotlinx.serialization.json.jsonObject
//import kotlinx.serialization.json.jsonPrimitive
//import okhttp3.*
//import okio.ByteString
//import java.util.concurrent.TimeUnit
//import kotlin.coroutines.resumeWithException
//import kotlin.math.min
//
///**
// * RealtimeSocketManager:
// * - Sends PCM chunks from recorder
// * - Receives audio chunks from WS -> Player
// * - Handles reconnect with exponential backoff
// * - Allows optional manual commit/requestResponse fallback
// */
//class RealtimeSocketManager(
//    private val baseUrl: String = "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview",
//    private val sessionToken: String,
//    private val recorder: MicRecorder,
//    private val player: Player,
//    private val scope: CoroutineScope
//) {
//    private val client = OkHttpClient.Builder()
//        .pingInterval(20, TimeUnit.SECONDS)
//        .build()
//
//    @Volatile private var webSocket: WebSocket? = null
//    private val sendMutex = Mutex() // avoid concurrent ws.send collisions
//    private val json = Json { ignoreUnknownKeys = true }
//
//    private var connectionJob: Job? = null
//    private var sendJob: Job? = null
//
//    // outgoing control channel (for controlled commits or other JSON messages)
//    private val controlChannel = Channel<String>(capacity = Channel.UNLIMITED)
//
//    // backoff
//    private var backoffSeconds = 1L
//    private val maxBackoffSeconds = 60L
//
//    fun start() {
//        if (connectionJob?.isActive == true) return
//
//        player.start()
//        recorder.start()
//
//        connectionJob = scope.launch(Dispatchers.IO) {
//            while (isActive) {
//                try {
//                    connectOnce()
//                    // reset backoff on successful connection
//                    backoffSeconds = 1L
//                    // suspend until connectionJob is cancelled (on close/failure we'll loop)
//                    // this coroutine will continue only after exceptions; OkHttp WS callbacks handle real I/O
//                    // keep alive until socket is closed externally:
//                    break
//                } catch (t: Throwable) {
//                    t.printStackTrace()
//                    // backoff then retry
//                    delay(backoffSeconds * 1000)
//                    backoffSeconds = min(maxBackoffSeconds, backoffSeconds * 2)
//                }
//            }
//        }
//    }
//
//    private suspend fun connectOnce() = suspendCancellableCoroutine<Unit> { cont ->
//        val request = Request.Builder()
//            .url(baseUrl)
//            .addHeader("Authorization", "Bearer $sessionToken")
//            .build()
//
//        val listener = object : WebSocketListener() {
//            override fun onOpen(ws: WebSocket, response: Response) {
//                webSocket = ws
//                // start sending mic chunks
//                sendJob = scope.launch(Dispatchers.IO) {
//                    val chunkChan = recorder.chunkChannel
//                    try {
//                        for (chunk in chunkChan) {
//                            val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
//                            val jsonMsg = """{"type":"input_audio_buffer.append","audio":"$b64"}"""
//                            safeSend(jsonMsg)
//                        }
//                    } catch (e: CancellationException) {
//                        // expected on stop
//                    } catch (e: Throwable) {
//                        e.printStackTrace()
//                    }
//                }
//
//                // separate coroutine to send control messages if any
//                scope.launch(Dispatchers.IO) {
//                    for (msg in controlChannel) {
//                        safeSend(msg)
//                    }
//                }
//
//                cont.resume(Unit) {}
//            }
//
//            override fun onMessage(ws: WebSocket, text: String) {
//                scope.launch(Dispatchers.IO) {
//                    handleTextEvent(text)
//                }
//            }
//
//            override fun onMessage(ws: WebSocket, bytes: ByteString) {
//                val arr = bytes.toByteArray()
//                // if server sends raw PCM bytes, play them
//                player.playChunk(arr)
//            }
//
//            override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
//                t.printStackTrace()
//                // ensure reconnect
//                cleanupSocket()
//                if (cont.isActive) cont.resumeWithException(t)
//            }
//
//            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
//                ws.close(1000, null)
//            }
//
//            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
//                cleanupSocket()
//            }
//        }
//
//        client.newWebSocket(request, listener)
//
//        cont.invokeOnCancellation {
//            // if caller cancels, close socket
//            webSocket?.close(1000, "cancel")
//            cleanupSocket()
//        }
//    }
//
//    private suspend fun safeSend(msg: String) {
//        sendMutex.withLock {
//            try {
//                webSocket?.send(msg)
//            } catch (e: Throwable) {
//                // maybe socket closed -> will trigger reconnect externally
//                e.printStackTrace()
//            }
//        }
//    }
//
//    private suspend fun handleTextEvent(text: String) {
//        try {
//            val el = json.parseToJsonElement(text).jsonObject
//            val type = el["type"]?.jsonPrimitive?.contentOrNull ?: return
//            when (type) {
//                "response.audio.delta", "output_audio_buffer.append" -> {
//                    // some models use `delta` some `audio`
//                    val b64 = el["delta"]?.jsonPrimitive?.contentOrNull ?: el["audio"]?.jsonPrimitive?.contentOrNull
//                    if (!b64.isNullOrEmpty()) {
//                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
//                        player.playChunk(bytes)
//                    }
//                }
//                "conversation.item.input_audio_transcription.completed" -> {
//                    // optional: UI update hook via an event sink (implement in ViewModel)
//                }
//                "error" -> {
//                    val err = el["error"]?.jsonPrimitive?.contentOrNull
//                    // handle error properly (emit to UI)
//                }
//                else -> {
//                    // other events (debug)
//                }
//            }
//        } catch (e: Throwable) {
//            e.printStackTrace()
//        }
//    }
//
//    private fun cleanupSocket() {
//        try {
//            webSocket = null
//            sendJob?.cancel()
//            sendJob = null
//        } catch (_: Throwable) { }
//    }
//
//    fun stop() {
//        connectionJob?.cancel()
//        sendJob?.cancel()
//        controlChannel.trySend("""{"type":"session.end"}""")
//        webSocket?.close(1000, "client closed")
//        recorder.stop()
//        player.stop()
//    }
//
//    // optional manual commit if you want client-side commit fallback
//    suspend fun commitInputBuffer() {
//        val json = """{"type":"input_audio_buffer.commit"}"""
//        controlChannel.send(json)
//    }
//
//    // optional manual response request (if you don't want server VAD)
//  /*  suspend fun requestResponse(instructions: String = "You are an English tutor. Correct grammar briefly and reply naturally.") {
//        val payload = """{"type":"response.create","response":{"instructions":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), instructions)}}}"""
//        controlChannel.send(payload)
//    }*/
//}
