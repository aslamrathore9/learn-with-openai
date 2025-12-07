package com.varkyo.aitalkgpt.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.InputStream

/**
 * Audio player for streaming and chunk-based playback
 * Supports both ExoPlayer (for streams) and AudioTrack (for low-latency chunks)
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioPlayer(private val context: Context) {
    
    private var exoPlayer: ExoPlayer? = null
    
    /**
     * Play audio from an InputStream (streaming from server)
     * @param inputStream Audio stream (MP3 format)
     * @param onComplete Callback when playback completes
     */
    fun playStream(inputStream: InputStream, onComplete: () -> Unit) {
        Log.d("AudioPlayer", "Starting stream playback")
        
        // Run on background thread to avoid blocking
        Thread {
            try {
                // Create ExoPlayer on main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    exoPlayer = ExoPlayer.Builder(context).build()
                }
                
                // Wait for player to be created
                Thread.sleep(100)
                
                val player = exoPlayer ?: run {
                    Log.e("AudioPlayer", "Failed to create ExoPlayer")
                    onComplete()
                    return@Thread
                }
                
                // Create custom DataSource from InputStream
                val dataSource = InputStreamDataSource(inputStream)
                val factory = DataSource.Factory { dataSource }
                
                // Create MediaSource
                val mediaSource = ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri(Uri.EMPTY))
                
                player.setMediaSource(mediaSource)
                player.prepare()
                player.playWhenReady = true
                
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            Log.d("AudioPlayer", "Stream Playback completed")
                            onComplete()
                        }
                    }
                    
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("AudioPlayer", "ExoPlayer stream error", error)
                        onComplete()
                    }
                })
                
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error initializing Stream Player", e)
                onComplete()
            }
        }.start()
    }
    
    fun release() { stop() }

    /**
     * Custom DataSource that reads from a generic InputStream.
     * Use with caution: InputStreams are one-shot!
     */
    private class InputStreamDataSource(private val inputStream: InputStream) : BaseDataSource(true) {
        private var opened = false
        private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

        override fun open(dataSpec: DataSpec): Long {
            if (opened) {
                 // Should not reopen valid stream usually, or ignored if same
            }
            transferInitializing(dataSpec)
            opened = true
            transferStarted(dataSpec)
            // We don't know length, return UNSET
            return C.LENGTH_UNSET.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
            if (readLength == 0) {
                return 0
            }
            
            try {
                val bytesRead = inputStream.read(buffer, offset, readLength)
                if (bytesRead == -1) {
                    return C.RESULT_END_OF_INPUT
                }
                bytesTransferred(bytesRead)
                return bytesRead
            } catch (e: java.io.IOException) {
                throw androidx.media3.datasource.DataSourceException(2000)
            }
        }

        override fun getUri(): Uri? {
            return Uri.EMPTY
        }

        override fun close() {
            if (opened) {
                opened = false
                try {
                    inputStream.close()
                } catch (e: java.io.IOException) {
                    // Ignore
                }
                transferEnded()
            }
        }
    }
    
    // ==========================================
    // LOW LATENCY CHUNK PLAYBACK (AudioTrack)
    // ==========================================
    private var audioTrack: android.media.AudioTrack? = null
    private var isAudioTrackInitialized = false
    
    fun playChunk(pcmData: ByteArray) {
        try {
            if (!isAudioTrackInitialized || audioTrack == null) {
                initializeAudioTrack()
            }
            
            // Write audio data in blocking mode for smoother playback
            val track = audioTrack ?: return
            
            // Write the entire chunk
            var offset = 0
            while (offset < pcmData.size) {
                val written = track.write(pcmData, offset, pcmData.size - offset, android.media.AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    Log.e("AudioPlayer", "Error writing audio chunk: $written")
                    break
                }
                offset += written
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing chunk", e)
            // Reinitialize on error
            isAudioTrackInitialized = false
            audioTrack?.release()
            audioTrack = null
        }
    }
    
    private fun initializeAudioTrack() {
        try {
            val sampleRate = 24000 // OpenAI Realtime default is 24kHz
            val channelConfig = android.media.AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            
            // Use larger buffer for smoother playback (3x minimum)
            val minBufferSize = android.media.AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufferSize * 3
            
            Log.d("AudioPlayer", "Initializing AudioTrack: sampleRate=$sampleRate, bufferSize=$bufferSize")
            
            audioTrack = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                .setPerformanceMode(android.media.AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            
            audioTrack?.play()
            isAudioTrackInitialized = true
            Log.d("AudioPlayer", "AudioTrack initialized and playing")
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to initialize AudioTrack", e)
            isAudioTrackInitialized = false
        }
    }

    fun stop() {
        Log.d("AudioPlayer", "Stopping player")
        try {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
            
            audioTrack?.stop()
            audioTrack?.flush()
            audioTrack?.release()
            audioTrack = null
            isAudioTrackInitialized = false
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping", e)
        }
    }
}
