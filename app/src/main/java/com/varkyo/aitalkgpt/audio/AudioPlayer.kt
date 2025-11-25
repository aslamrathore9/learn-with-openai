package com.varkyo.aitalkgpt.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Audio player for MP3 playback using MediaPlayer
 */
class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null
    
    /**
     * Play MP3 audio bytes
     */
    suspend fun playMp3(audioBytes: ByteArray, onComplete: () -> Unit = {}) {
        withContext(Dispatchers.IO) {
            try {
                // Stop any currently playing audio
                stop()
                
                // Create temporary file for MP3
                tempFile = File.createTempFile("audio_", ".mp3")
                FileOutputStream(tempFile!!).use { fos ->
                    fos.write(audioBytes)
                    fos.flush()
                }
                
                withContext(Dispatchers.Main) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempFile!!.absolutePath)
                        prepare()
                        setOnCompletionListener {
                            Log.d("AudioPlayer", "Playback completed")
                            stop()
                            onComplete()
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e("AudioPlayer", "Playback error: what=$what, extra=$extra")
                            stop()
                            onComplete()
                            true
                        }
                        start()
                        Log.d("AudioPlayer", "Started playing MP3: ${audioBytes.size} bytes")
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error playing MP3", e)
                stop()
                onComplete()
            }
        }
    }
    
    /**
     * Stop playback and cleanup
     */
    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping MediaPlayer", e)
        }
        mediaPlayer = null
        
        // Delete temporary file
        tempFile?.delete()
        tempFile = null
    }
    
    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
    
    /**
     * Release all resources
     */
    fun release() {
        stop()
    }
}

