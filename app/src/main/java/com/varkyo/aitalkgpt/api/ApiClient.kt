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
 * Direct OpenAI API client (no backend server)
 * Calls OpenAI endpoints directly using API key
 */
class ApiClient(
    private val openAiApiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val openAiBaseUrl = "https://api.openai.com/v1"
    
    /**
     * Transcribe audio to text using OpenAI Whisper API
     * POST https://api.openai.com/v1/audio/transcriptions
     * @param audioData PCM audio bytes (16-bit, 16kHz, mono)
     * @return Transcribed text
     */
    suspend fun transcribe(audioData: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            // Convert PCM to WAV format
            val wavData = convertPcmToWav(audioData, sampleRate = 16000)
            Log.d("ApiClient", "Converted PCM to WAV: ${wavData.size} bytes")
            
            // Create temporary file for multipart upload
            tempFile = File.createTempFile("audio_", ".wav")
            FileOutputStream(tempFile!!).use { fos ->
                fos.write(wavData)
                fos.flush()
            }
            
            // Create multipart request body for OpenAI
            // OpenAI expects: file (audio file) and model (string)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "audio.wav",
                    tempFile!!.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", "whisper-1") // OpenAI Whisper model
                .build()
            
            val request = Request.Builder()
                .url("$openAiBaseUrl/audio/transcriptions")
                .addHeader("Authorization", "Bearer $openAiApiKey")
                .post(requestBody)
                .build()
            
            Log.d("ApiClient", "Sending transcription request to OpenAI...")
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e("ApiClient", "Transcribe failed: ${response.code} - $errorBody")
                return@withContext Result.failure(
                    IOException("Transcribe failed: ${response.code} - $errorBody")
                )
            }
            
            val responseBody = response.body?.string() ?: ""
            Log.d("ApiClient", "Transcription response: $responseBody")
            
            // OpenAI returns: { "text": "..." }
            val text = parseJsonResponse(responseBody, "text")
            
            if (text.isNullOrEmpty()) {
                return@withContext Result.failure(IOException("Empty transcription response"))
            }
            
            Log.d("ApiClient", "✅ Transcription successful: \"$text\"")
            Result.success(text)
        } catch (e: Exception) {
            Log.e("ApiClient", "Transcribe error", e)
            Result.failure(e)
        } finally {
            // Clean up temp file
            tempFile?.delete()
        }
    }
    
    /**
     * Get English teacher feedback and correction using OpenAI Chat API
     * POST https://api.openai.com/v1/chat/completions
     * @param userText User's spoken text (may contain errors)
     * @return Teacher's corrected sentence + natural reply
     */
    suspend fun ask(userText: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Build OpenAI chat completion request
            val requestBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are an English learning tutor. First correct the user's sentence, then reply naturally in simple conversational English.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userText)
                    })
                })
                put("temperature", 0.7)
            }
            
            val request = Request.Builder()
                .url("$openAiBaseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $openAiApiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(jsonMediaType))
                .build()
            
            Log.d("ApiClient", "Sending chat request to OpenAI with text: \"$userText\"")
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e("ApiClient", "Ask failed: ${response.code} - $errorBody")
                return@withContext Result.failure(
                    IOException("Ask failed: ${response.code} - $errorBody")
                )
            }
            
            val responseBody = response.body?.string() ?: ""
            Log.d("ApiClient", "Chat response: $responseBody")
            
            // OpenAI returns: { "choices": [{ "message": { "content": "..." } }] }
            val reply = parseOpenAIResponse(responseBody)
            
            if (reply.isNullOrEmpty()) {
                return@withContext Result.failure(IOException("Empty AI response"))
            }
            
            Log.d("ApiClient", "✅ AI response received: \"$reply\"")
            Result.success(reply)
        } catch (e: Exception) {
            Log.e("ApiClient", "Ask error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Convert text to speech using OpenAI TTS API
     * POST https://api.openai.com/v1/audio/speech
     * @param text Text to convert to speech
     * @return MP3 audio bytes
     */
    suspend fun speak(text: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            // Build OpenAI TTS request
            val requestBody = JSONObject().apply {
                put("model", "tts-1") // or "tts-1-hd" for higher quality
                put("voice", "alloy")
                put("input", text)
            }
            
            val request = Request.Builder()
                .url("$openAiBaseUrl/audio/speech")
                .addHeader("Authorization", "Bearer $openAiApiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(jsonMediaType))
                .build()
            
            Log.d("ApiClient", "Sending TTS request to OpenAI for text: \"$text\"")
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e("ApiClient", "Speak failed: ${response.code} - $errorBody")
                return@withContext Result.failure(
                    IOException("Speak failed: ${response.code} - $errorBody")
                )
            }
            
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
