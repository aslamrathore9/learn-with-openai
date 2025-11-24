package com.varkyo.aitalkgpt.realtime

import android.util.Base64
import android.util.Log
import com.varkyo.aitalkgpt.audio.MicRecorder
import com.varkyo.aitalkgpt.audio.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonObject
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class RealtimeClient(
    private val sessionToken: String,
    private val player: Player,
    private val recorder: MicRecorder,
    private val scope: CoroutineScope
) {
    private var webSocket: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val wsUrl = "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview"

    fun start() {
        val req = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $sessionToken")
            .build()

        player.start()

        val listener = object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                webSocket = ws
                Log.d("AI_WS", "WebSocket OPENED")

                // Counter for throttling logs
                var sentChunks = 0

                // Start sending mic audio in background
                scope.launch(Dispatchers.IO) {
                    for (chunk in recorder.chunkChannel) {
                        if (chunk.isNotEmpty()) {

                            // Send chunk as base64
                            val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                            val json = buildJsonObject {
                                put("type", "input_audio_buffer.append")
                                put("audio", b64)
                            }.toString()

                            ws.send(json)

                            sentChunks++

                            // Log every 5th chunk to avoid spam
                            if (sentChunks % 5 == 0) {
                                val previewSize = minOf(20, chunk.size)
                                val chunkPreview = chunk.take(previewSize).joinToString(", ") { it.toString() }
                                val minSample = chunk.minOrNull() ?: 0
                                val maxSample = chunk.maxOrNull() ?: 0

                                Log.d(
                                    "AI_WS",
                                    "Sent chunk #$sentChunks, size=${chunk.size}, " +
                                            "first $previewSize bytes=$chunkPreview, min=$minSample, max=$maxSample"
                                )
                            }
                        }
                    }
                }
            }


            override fun onMessage(ws: WebSocket, text: String) {
                Log.d("AI_WS", "RAW TEXT MESSAGE: $text")


                scope.launch(Dispatchers.IO) {
                    try {
                        val json = Json.parseToJsonElement(text).jsonObject
                        val type = json["type"]?.jsonPrimitive?.content ?: return@launch

                        when (type) {
                            // FIXED: OpenAI sends audio in response.output_audio.delta events (not response.audio.delta)
                            // The delta field contains base64-encoded PCM audio data
                            "response.output_audio.delta" -> {
                                val b64 = json["delta"]?.jsonPrimitive?.content
                                    ?: return@launch

                                try {
                                    val pcm = Base64.decode(b64, Base64.DEFAULT)
                                    if (pcm.isNotEmpty()) {
                                        // Ensure player is started before playing
                                        player.start()
                                        player.playChunk(pcm)
                                        
                                        Log.d(
                                            "AI_WS",
                                            "Audio delta event: Base64 len=${b64.length}, PCM bytes=${pcm.size}"
                                        )
                                    } else {
                                        Log.w("AI_WS", "Decoded audio data is empty")
                                    }
                                } catch (e: Exception) {
                                    Log.e("AI_WS", "Error decoding/playing audio delta", e)
                                }
                            }
                            
                            // Handle response.content_part.done - this only contains transcript, not audio
                            // Audio comes in response.output_audio.delta events above
                            "response.content_part.done" -> {
                                val part = json["part"]?.jsonObject
                                val partType = part?.get("type")?.jsonPrimitive?.content
                                
                                if (partType == "audio") {
                                    val transcript = part["transcript"]?.jsonPrimitive?.content
                                    if (!transcript.isNullOrEmpty()) {
                                        Log.d("AI_WS", "Audio transcript: $transcript")
                                    }
                                    // Note: Audio data is NOT in this event - it comes in response.output_audio.delta
                                }
                            }
                            
                            // Legacy event types (may not be used by current API)
                            "response.audio.delta",
                            "output_audio_buffer.append" -> {
                                val b64 = json["delta"]?.jsonPrimitive?.content
                                    ?: json["audio"]?.jsonPrimitive?.content
                                    ?: return@launch

                                try {
                                    val pcm = Base64.decode(b64, Base64.DEFAULT)
                                    if (pcm.isNotEmpty()) {
                                        player.start()
                                        player.playChunk(pcm)
                                        Log.d(
                                            "AI_WS",
                                            "Legacy audio event: type=$type, Base64 len=${b64.length}, PCM bytes=${pcm.size}"
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("AI_WS", "Error decoding/playing legacy audio event", e)
                                }
                            }

                            "error" -> {
                                Log.e("AI_WS", "AI ERROR EVENT: $json")
                            }

                            "session.created" -> {
                                Log.d(
                                    "AI_WS",
                                    "Session created, session_id=${json["session"]?.jsonObject?.get("id")?.jsonPrimitive?.content}"
                                )
                            }

                            else -> {
                                Log.d("AI_WS", "Other event type=$type")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AI_WS", "JSON PARSE ERROR: ${e.message}", e)
                    }
                }
            }


            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // FIXED: Handle binary audio data directly from WebSocket
                // Some implementations send raw PCM audio as binary messages
                Log.d("AI_WS", "RAW BINARY MESSAGE: ${bytes.size} bytes")
                
                val pcm = bytes.toByteArray()
                if (pcm.isNotEmpty()) {
                    player.start()
                    player.playChunk(pcm)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
                Log.e("AI_WS", "WebSocket failure", t)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d("AI_WS", "WebSocket CLOSED code=$code reason=$reason")
                webSocket = null
            }
        }

        client.newWebSocket(req, listener)
    }

    fun stop() {
        webSocket?.close(1000, "closed")
        player.stop()
        recorder.stop()
    }
}

