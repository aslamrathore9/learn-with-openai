package com.varkyo.aitalkgpt.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Data class to hold corrected sentence and AI reply
 */
data class CorrectionResponse(
    val corrected: String,
    val reply: String
)

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
    
    private val jsonMediaType = "application/json".toMediaType()
    
    /**
     * Transcribe audio using backend API
     * POST {serverBaseUrl}/transcribe
     * @param audioData PCM audio bytes
     * @return Transcribed text
     */
    suspend fun transcribe(audioData: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val wavData = convertPcmToWav(audioData, sampleRate = 16000)
            
            // Check file size (limit to ~1MB for faster upload)
            if (wavData.size > 1024 * 1024) {
                return@withContext Result.failure(
                    IOException("Audio file too large (${wavData.size / 1024} KB). Please record shorter clips (max 30 seconds).")
                )
            }
            
            val estimatedDuration = (wavData.size / 32000).toFloat()
            Log.d("ApiClient", "Uploading audio: ${wavData.size / 1024} KB (~${String.format("%.1f", estimatedDuration)}s)")
            
            // Create temp WAV file
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    "audio.wav",
                    wavData.toRequestBody("audio/wav".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("$serverBaseUrl/transcribe")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(IOException("Transcribe failed: ${response.code} - $errorBody"))
            }
            val body = response.body?.string() ?: ""
            val text = parseJsonResponse(body, "text")
            if (text.isNullOrEmpty())
                return@withContext Result.failure(IOException("Empty transcription response"))
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get English teacher feedback and correction using backend API
     * POST {serverBaseUrl}/chat
     * @param userText user's sentence
     * @param conversationHistory List of previous (user, ai) messages (List<Pair<String,String>>) - map to [{user:...,ai:...}]
     * @return CorrectionResponse with corrected sentence and short reply
     */
    suspend fun ask(userText: String, conversationHistory: List<Pair<String, String>> = emptyList()): Result<CorrectionResponse> = withContext(Dispatchers.IO) {
        try {
            val historyArray = org.json.JSONArray().apply {
                for ((user, ai) in conversationHistory) {
                    put(JSONObject().apply {
                        put("user", user)
                        put("ai", ai)
                    })
                }
            }
            val requestBodyJson = JSONObject().apply {
                put("userText", userText)
                put("conversationHistory", historyArray)
            }
            val requestBody = requestBodyJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$serverBaseUrl/chat")
                .post(requestBody)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(IOException("Ask failed: ${response.code} - $errorBody"))
            }
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val corrected = json.optString("corrected", null)
            val reply = json.optString("reply", null)
            if (!corrected.isNullOrEmpty() && !reply.isNullOrEmpty()) {
                Result.success(CorrectionResponse(corrected, reply))
            } else {
                Result.failure(IOException("Invalid AI response: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    /**
     * Single-shot conversation: Audio -> Audio (Lowest Latency)
     * POST {serverBaseUrl}/converse
     * @return Triple<UserText, ReplyText?, InputStream>
     */
    suspend fun converse(
        audioData: ByteArray, 
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<Triple<String, String?, java.io.InputStream>> = withContext(Dispatchers.IO) {
        try {
            val wavData = convertPcmToWav(audioData, sampleRate = 16000)
            
            // Build Multipart Request
            val historyArray = org.json.JSONArray().apply {
                for ((user, ai) in conversationHistory) {
                    put(JSONObject().apply {
                        put("user", user)
                        put("ai", ai)
                    })
                }
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", "audio.wav", wavData.toRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("conversationHistory", historyArray.toString())
                .build()

            val request = Request.Builder()
                .url("$serverBaseUrl/converse")
                .post(requestBody)
                .build()
                
            Log.d("ApiClient", "Sending Converse request (One-Shot)...")
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(IOException("Converse failed: ${response.code} - $errorBody"))
            }

            // Extract Transcript from Header
            var userText = ""
            val transcriptB64 = response.header("X-Transcript-B64")
            if (transcriptB64 != null) {
                try {
                     userText = String(android.util.Base64.decode(transcriptB64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                     Log.d("ApiClient", "Received Transcript Header: $userText")
                } catch (e: Exception) {
                    Log.e("ApiClient", "Failed to decode transcript header", e)
                }
            } else {
                Log.w("ApiClient", "No transcript header found")
            }

            val stream = response.body?.byteStream()
            if (stream == null) {
                return@withContext Result.failure(IOException("Empty audio stream"))
            }

            Log.d("ApiClient", "✅ Received Audio Stream (Converse)")
            // We don't get 'Reply' text back in header currently (generated later), so return null. 
            // The UI will play audio.
            Result.success(Triple(userText, null, stream))

        } catch (e: Exception) {
            Log.e("ApiClient", "Converse error", e)
            Result.failure(e)
        }
    }

    /**
     * Combined Chat + Stream Audio (Lower Latency)
     * POST {serverBaseUrl}/chat-audio-stream
     * @return Pair<CorrectionResponse?, InputStream>
     */
    suspend fun chatAndStreamAudio(
        userText: String, 
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<Pair<CorrectionResponse?, java.io.InputStream>> = withContext(Dispatchers.IO) {
        try {
            // Prepare JSON body
            val historyArray = org.json.JSONArray().apply {
                for ((user, ai) in conversationHistory) {
                    put(JSONObject().apply {
                        put("user", user)
                        put("ai", ai)
                    })
                }
            }
            val requestBodyJson = JSONObject().apply {
                put("userText", userText)
                put("conversationHistory", historyArray)
            }
            
            val request = Request.Builder()
                .url("$serverBaseUrl/chat-audio-stream")
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            Log.d("ApiClient", "Sending Smart Stream request: \"$userText\"")
            
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(IOException("Stream failed: ${response.code} - $errorBody"))
            }

            // Extract "Corrected" text from Header (X-Corrected-Text-B64)
            var correctionResponse: CorrectionResponse? = null
            val correctedB64 = response.header("X-Corrected-Text-B64")
            if (correctedB64 != null) {
                try {
                    val decodedBytes = android.util.Base64.decode(correctedB64, android.util.Base64.DEFAULT)
                    val correctedText = String(decodedBytes, Charsets.UTF_8)
                    Log.d("ApiClient", "Received Corrected Header: $correctedText")
                    // We don't get the "Reply" text easily in the header, relying on audio for that.
                    // Construct a partial response
                    correctionResponse = CorrectionResponse(correctedText, "(Audio Reply)")
                } catch (e: Exception) {
                    Log.e("ApiClient", "Failed to decode header", e)
                }
            }

            val stream = response.body?.byteStream()
            if (stream == null) {
                return@withContext Result.failure(IOException("Empty audio stream"))
            }

            Log.d("ApiClient", "✅ Received Audio Stream (Smart)")
            Result.success(Pair(correctionResponse, stream))
            
        } catch (e: Exception) {
            Log.e("ApiClient", "Smart Stream error", e)
            Result.failure(e)
        }
    }

    /**
     * Convert text to speech using OpenAI TTS API
     * POST https://api.openai.com/v1/audio/speech
     * @param text Text to convert to speech
     * @return MP3 audio bytes
     */
    /**
     * Convert text to speech using backend TTS API
     * POST {serverBaseUrl}/tts
     * @param text Text to convert to speech
     * @return MP3 audio bytes
     */
    /**
     * Convert text to speech using backend TTS API (Streaming)
     * POST {serverBaseUrl}/tts
     * @param text Text to convert to speech
     * @return InputStream of MP3 audio
     */
    suspend fun speak(text: String): Result<java.io.InputStream> = withContext(Dispatchers.IO) {
        try {
            // Build TTS request to backend server
            val requestBody = JSONObject().apply {
                put("model", "tts-1") // or "tts-1-hd" for higher quality
                put("voice", "alloy")
                put("input", text)
            }

            val request = Request.Builder()
                .url("$serverBaseUrl/tts")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(jsonMediaType))
                .build()

            Log.d("ApiClient", "Sending TTS request to server for text: \"$text\"")
            
            // Execute as stream (do not use .execute() which buffers, wait... execute() buffers headers but .body?.byteStream() streams content)
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e("ApiClient", "Speak failed: ${response.code} - $errorBody")
                return@withContext Result.failure(
                    IOException("Speak failed: ${response.code} - $errorBody")
                )
            }

            // Return the raw stream (Caller must close it!)
            val stream = response.body?.byteStream()
            
            if (stream == null) {
                 return@withContext Result.failure(IOException("Empty audio stream"))
            }

            Log.d("ApiClient", "✅ Received Audio Stream (Header OK)")
            Result.success(stream)
        } catch (e: Exception) {
            Log.e("ApiClient", "Speak error", e)
            Result.failure(e)
        }
    }

    /**
     * Parse JSON response to extract a field value
     */
    private fun parseJsonResponse(json: String, fieldName: String): String? {
        return try {
            val jsonObj = JSONObject(json)
            jsonObj.optString(fieldName, null)
        } catch (e: Exception) {
            Log.e("ApiClient", "JSON parse error for field '$fieldName'", e)
            null
        }
    }

    /**
     * Parse OpenAI chat completion response
     * Format: { "choices": [{ "message": { "content": "..." } }] }
     */
    private fun parseOpenAIResponse(json: String): String? {
        return try {
            val jsonObj = JSONObject(json)
            val choices = jsonObj.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                message.optString("content", null)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "OpenAI response parse error", e)
            null
        }
    }

    /**
     * Parse correction response from AI
     * Expected format:
     * Corrected: <corrected sentence>
     * Reply: <your short reply>
     *
     * Handles multi-line responses and various formatting styles
     */
    private fun parseCorrectionResponse(response: String): CorrectionResponse? {
        return try {
            // First, try regex to extract content after "Corrected:" and "Reply:" labels
            // This handles cases where content might span multiple lines
            val correctedPattern = Regex(
                "Corrected:\\s*(.+?)(?=\\s*Reply:|$)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val replyPattern = Regex(
                "Reply:\\s*(.+?)$",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )

            val correctedMatch = correctedPattern.find(response)
            val replyMatch = replyPattern.find(response)

            val corrected = correctedMatch?.groupValues?.get(1)?.trim()?.replace(Regex("\\s+"), " ")
            val reply = replyMatch?.groupValues?.get(1)?.trim()?.replace(Regex("\\s+"), " ")

            if (corrected != null && reply != null && corrected.isNotEmpty() && reply.isNotEmpty()) {
                CorrectionResponse(corrected, reply)
            } else {
                // Fallback: try line-by-line parsing
                val lines = response.lines()
                var correctedFallback: String? = null
                var replyFallback: String? = null
                var currentSection: String? = null
                val correctedBuilder = StringBuilder()
                val replyBuilder = StringBuilder()

                for (line in lines) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("Corrected:", ignoreCase = true) -> {
                            currentSection = "corrected"
                            val content = trimmed.removePrefix("Corrected:").trim()
                            if (content.isNotEmpty()) {
                                correctedBuilder.append(content)
                            }
                        }
                        trimmed.startsWith("Reply:", ignoreCase = true) -> {
                            currentSection = "reply"
                            val content = trimmed.removePrefix("Reply:").trim()
                            if (content.isNotEmpty()) {
                                replyBuilder.append(content)
                            }
                        }
                        else -> {
                            // Continue appending to current section if we're in one
                            if (trimmed.isNotEmpty()) {
                                when (currentSection) {
                                    "corrected" -> {
                                        if (correctedBuilder.isNotEmpty()) correctedBuilder.append(" ")
                                        correctedBuilder.append(trimmed)
                                    }
                                    "reply" -> {
                                        if (replyBuilder.isNotEmpty()) replyBuilder.append(" ")
                                        replyBuilder.append(trimmed)
                                    }
                                }
                            }
                        }
                    }
                }

                correctedFallback = correctedBuilder.toString().trim().takeIf { it.isNotEmpty() }
                replyFallback = replyBuilder.toString().trim().takeIf { it.isNotEmpty() }

                if (correctedFallback != null && replyFallback != null) {
                    CorrectionResponse(correctedFallback, replyFallback)
                } else {
                    Log.w("ApiClient", "Could not parse correction format from: $response")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "Error parsing correction response", e)
            null
        }
    }

    /**
     * Convert PCM audio to WAV format
     * WAV file structure:
     * - RIFF header: 4 bytes
     * - File size: 4 bytes (size of rest of file)
     * - Format: 4 bytes ("WAVE")
     * - fmt chunk: 24 bytes (4 + 16 + 4)
     * - data chunk header: 8 bytes (4 + 4)
     * - PCM data: pcmData.size bytes
     * Total = 44 + pcmData.size
     */
    private fun convertPcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        // RIFF chunk size = size of file - 8 (RIFF header + size field)
        // = (44 + pcmData.size) - 8 = 36 + pcmData.size
        val riffChunkSize = 36 + pcmData.size

        // Total WAV file size = 44 (header) + pcmData.size
        val wavSize = 44 + pcmData.size
        val wav = ByteArray(wavSize)

        // RIFF header
        wav[0] = 'R'.code.toByte()
        wav[1] = 'I'.code.toByte()
        wav[2] = 'F'.code.toByte()
        wav[3] = 'F'.code.toByte()

        // File size (RIFF chunk size)
        writeInt(wav, 4, riffChunkSize)

        // "WAVE"
        wav[8] = 'W'.code.toByte()
        wav[9] = 'A'.code.toByte()
        wav[10] = 'V'.code.toByte()
        wav[11] = 'E'.code.toByte()

        // "fmt "
        wav[12] = 'f'.code.toByte()
        wav[13] = 'm'.code.toByte()
        wav[14] = 't'.code.toByte()
        wav[15] = ' '.code.toByte()

        // Subchunk1Size (16 for PCM)
        writeInt(wav, 16, 16)

        // AudioFormat (1 = PCM)
        writeShort(wav, 20, 1)

        // NumChannels (1 = mono)
        writeShort(wav, 22, 1)

        // SampleRate
        writeInt(wav, 24, sampleRate)

        // ByteRate (sampleRate * numChannels * bitsPerSample / 8)
        writeInt(wav, 28, sampleRate * 2)

        // BlockAlign (numChannels * bitsPerSample / 8)
        writeShort(wav, 32, 2)

        // BitsPerSample
        writeShort(wav, 34, 16)

        // "data"
        wav[36] = 'd'.code.toByte()
        wav[37] = 'a'.code.toByte()
        wav[38] = 't'.code.toByte()
        wav[39] = 'a'.code.toByte()

        // Subchunk2Size (PCM data size)
        writeInt(wav, 40, pcmData.size)

        // PCM data (starts at offset 44)
        pcmData.copyInto(wav, 44, 0, pcmData.size)

        return wav
    }

    private fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
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
        webSocket?.close(1000, "User ended call")
        webSocket = null
    }
}
