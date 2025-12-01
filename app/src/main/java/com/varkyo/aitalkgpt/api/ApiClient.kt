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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Transcribe audio to text using backend whisper API
     * POST {serverBaseUrl}/transcribe (multipart/form-data)
     * @param audioData PCM audio bytes (16-bit, 16kHz, mono)
     * @return Transcribed text
     */
    suspend fun transcribe(audioData: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            // Convert PCM to WAV format
            val wavData = convertPcmToWav(audioData, sampleRate = 16000)
            // Create temp WAV file
            tempFile = File.createTempFile("audio_", ".wav")
            FileOutputStream(tempFile).use { it.write(wavData) }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    "audio.wav",
                    tempFile.asRequestBody("audio/wav".toMediaType())
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
        } finally {
            tempFile?.delete()
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
    suspend fun speak(text: String): Result<ByteArray> = withContext(Dispatchers.IO) {
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
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e("ApiClient", "Speak failed: ${response.code} - $errorBody")
                return@withContext Result.failure(
                    IOException("Speak failed: ${response.code} - $errorBody")
                )
            }

            // Backend returns audio/mpeg directly (not JSON)
            val audioBytes = response.body?.bytes() ?: ByteArray(0)

            if (audioBytes.isEmpty()) {
                return@withContext Result.failure(IOException("Empty audio response"))
            }

            Log.d("ApiClient", "✅ Received MP3 audio: ${audioBytes.size} bytes")
            Result.success(audioBytes)
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
}
