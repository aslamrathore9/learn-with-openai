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
                            "response.audio.delta",
                            "output_audio_buffer.append" -> {
                                val b64 = json["delta"]?.jsonPrimitive?.content
                                    ?: json["audio"]?.jsonPrimitive?.content
                                    ?: return@launch

                                val pcm = Base64.decode(b64, Base64.DEFAULT)
                                player.start()
                                player.playChunk(pcm)

                                // Optional: check for text in the response
                                val textOutput = json["text"]?.jsonPrimitive?.content
                                    ?: json["response"]?.jsonObject
                                        ?.get("output")?.jsonArray
                                        ?.joinToString("") { it.jsonPrimitive.contentOrNull ?: "" }

                                if (!textOutput.isNullOrEmpty()) {
                                    Log.d("AI_WS", "Text snippet from response: ${textOutput.take(200)}") // first 200 chars
                                }

                                Log.d(
                                    "AI_WS",
                                    "Audio event received: type=$type, Base64 len=${b64.length}, PCM bytes=${pcm.size}"
                                )
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
                Log.d("AI_WS", "RAW TEXT MESSAGE: test")

                val pcm = bytes.toByteArray()
                player.playChunk(pcm)
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

