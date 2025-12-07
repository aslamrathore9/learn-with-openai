package com.varkyo.aitalkgpt.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit


/**
 * Direct OpenAI API client (no backend server)
 * Calls OpenAI endpoints directly using API key
 */
class ApiClient(
    private val serverBaseUrl: String = "https://my-server-openai.onrender.com"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Get Agora Token from server
     * GET {serverBaseUrl}/agora/token?channelName=...&uid=...
     */
    suspend fun getToken(channelName: String, uid: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverBaseUrl/agora/token?channelName=$channelName&uid=$uid")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                // Fallback Token for debugging when server is unreachable
                 val fallbackToken = "0064b319c885d854bcb984b0efb6553f1c1IACJLMLpiqimZ0DwWbS5ux7yis4p3PlF8K8mwPI1M3WXPab4ks0AAAAAIgB8sDiUYpg1aQQAAQDyVDRpAgDyVDRpAwDyVDRpBADyVDRp"
                 Log.w("ApiClient", "⚠️ Server Token Failed (${response.code}). Using Fallback Token.")
                 return@withContext Result.success(fallbackToken)
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val token = json.optString("token", null)
            
            if (token != null) {
                Log.d("ApiClient", "✅ Received Agora Token: $token")
                Result.success(token)
            } else {
                Log.d("ApiClient", "✅ Received Agora Token: dd")

                Result.failure(IOException("Token not found in response"))
            }
        } catch (e: Exception) {
            // Fallback Token for debugging when server throws exception
             val fallbackToken = "0064b319c885d854bcb984b0efb6553f1c1IACJLMLpiqimZ0DwWbS5ux7yis4p3PlF8K8mwPI1M3WXPab4ks0AAAAAIgB8sDiUYpg1aQQAAQDyVDRpAgDyVDRpAwDyVDRpBADyVDRp"
             Log.w("ApiClient", "⚠️ Server Exception (${e.message}). Using Fallback Token.")
             Result.success(fallbackToken)
        }
    }


    // ==========================================
    // REALTIME API (WebSocket)
    // ==========================================

    interface RealtimeCallback {
        fun onAudioDelta(base64Audio: String)
        fun onUserTranscript(text: String)
        fun onAiTranscript(text: String)
        fun onResponseComplete()  // NEW: Called when AI finishes speaking
        fun onError(msg: String)
    }

    private var webSocket: WebSocket? = null

    
    fun connectRealtime(callback: RealtimeCallback) {
        // Convert HTTP/HTTPS to WS/WSS properly
        val wsUrl = when {
            serverBaseUrl.startsWith("https://") -> serverBaseUrl.replace("https://", "wss://")
            serverBaseUrl.startsWith("http://") -> serverBaseUrl.replace("http://", "ws://")
            else -> "ws://$serverBaseUrl"
        } + "/"
        
        Log.d("ApiClient", "🔌 Connecting to WebSocket: $wsUrl")
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("ApiClient", "✅ WebSocket Connected successfully!")
            }


            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    // Log ALL messages to see what we're receiving
                    Log.d("ApiClient", "📨 WebSocket message received: ${text.take(200)}...")
                    
                    val event = JSONObject(text)
                    val eventType = event.optString("type")
                    
                    Log.d("ApiClient", "Event type: $eventType")
                    
                    when (eventType) {
                        "response.audio.delta" -> {
                            val b64 = event.optString("delta")
                            Log.d("ApiClient", "🔊 Audio delta received: ${b64.length} chars")
                            if (b64.isNotEmpty()) callback.onAudioDelta(b64)
                        }
                        "conversation.item.input_audio_transcription.completed" -> {
                            // User speech transcribed
                            val txt = event.optString("transcript")
                            Log.d("ApiClient", "📝 User transcript event: $txt")
                            if (txt.isNotEmpty()) callback.onUserTranscript(txt)
                        }
                        "response.audio_transcript.delta" -> {
                            // AI speech (streaming text)
                            val txt = event.optString("delta")
                            Log.d("ApiClient", "🤖 AI transcript delta: $txt")
                            if (txt.isNotEmpty()) callback.onAiTranscript(txt)
                        }
                        "error" -> {
                            Log.e("ApiClient", "❌ Realtime Error from Server: $text")
                            callback.onError("Server error: ${event.optString("message")}")
                        }
                        "response.audio.done" -> {
                            // AI finished speaking - notify immediately
                            Log.d("ApiClient", "✅ AI audio complete - triggering callback")
                            callback.onResponseComplete()
                        }
                        "response.done" -> {
                            // Check if response failed
                            val response = event.optJSONObject("response")
                            val status = response?.optString("status")
                            if (status == "failed") {
                                val statusDetails = response.optJSONObject("status_details")
                                val errorObj = statusDetails?.optJSONObject("error")
                                val errorType = errorObj?.optString("type")
                                val errorMessage = errorObj?.optString("message")
                                val errorCode = errorObj?.optString("code")
                                
                                Log.e("ApiClient", "❌ OpenAI Response FAILED!")
                                Log.e("ApiClient", "Error Type: $errorType")
                                Log.e("ApiClient", "Error Code: $errorCode")
                                Log.e("ApiClient", "Error Message: $errorMessage")
                                Log.e("ApiClient", "Full response.done: $text")
                                
                                callback.onError("OpenAI Error: $errorMessage")
                            } else {
                                Log.d("ApiClient", "✅ Response completed successfully")
                            }
                        }
                        else -> {
                            // Log other event types we might be missing
                            Log.d("ApiClient", "ℹ️ Unhandled event type: $eventType")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ApiClient", "❌ WS Parse Error: ${e.message}", e)
                    Log.e("ApiClient", "Raw message: $text")
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ApiClient", "❌ WebSocket Connection Failed!")
                Log.e("ApiClient", "Error: ${t.message}", t)
                Log.e("ApiClient", "Response: ${response?.code} - ${response?.message}")
                callback.onError(t.message ?: "WebSocket connection failed")
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ApiClient", "⚠️ WebSocket Closing: code=$code, reason=$reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ApiClient", "🔌 WebSocket Closed: code=$code, reason=$reason")
            }
        })
    }
    
    
    private var audioChunkCount = 0
    
    fun sendRealtimeAudio(bytes: ByteArray) {
        if (webSocket == null) {
            if (audioChunkCount % 100 == 0) {
                Log.w("ApiClient", "⚠️ Cannot send audio: WebSocket is null (chunk #$audioChunkCount)")
            }
            audioChunkCount++
            return
        }
        
        // Send raw bytes (Server handles sticking it into input_audio_buffer.append)
        // Note: Our Server expects BINARY message for audio chunks.
        val byteString = okio.ByteString.of(*bytes)
        val success = webSocket?.send(byteString) ?: false
        
        if (audioChunkCount % 100 == 0) {
            Log.d("ApiClient", "📤 Sent audio chunk #$audioChunkCount (${bytes.size} bytes) - success: $success")
        }
        audioChunkCount++
    }
    
    fun disconnectRealtime() {
        Log.d("ApiClient", "🔌 Disconnecting WebSocket...")
        try {
            webSocket?.close(1000, "User ended call")
            webSocket = null
            audioChunkCount = 0
            Log.d("ApiClient", "✅ WebSocket disconnected successfully")
        } catch (e: Exception) {
            Log.e("ApiClient", "Error closing WebSocket", e)
            webSocket = null
        }
    }
}
