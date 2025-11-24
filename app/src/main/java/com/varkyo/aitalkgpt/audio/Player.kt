package com.varkyo.aitalkgpt.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * FIXED: Audio playback issues resolved:
 * 1. Added MODE_STREAM mode for streaming audio chunks
 * 2. Check write() return value to ensure data is written
 * 3. Verify AudioTrack is in PLAYSTATE_PLAYING before writing
 * 4. Better error handling and logging
 * 5. Ensure AudioTrack is properly initialized before use
 * 6. CRASH FIX: Added thread synchronization to prevent null pointer crashes
 *    when multiple coroutines access audioTrack simultaneously
 */
class Player(private val sampleRate: Int = 16000) {

    @Volatile
    private var audioTrack: AudioTrack? = null
    
    @Volatile
    private var isPlaying = false
    
    // CRASH FIX: Synchronization lock to prevent race conditions
    // Multiple coroutines can call playChunk() simultaneously, causing crashes
    private val lock = Any()

    fun start() {
        // CRASH FIX: Synchronize access to prevent race conditions
        synchronized(lock) {
            // FIXED: Don't return early if already playing - allow re-initialization if needed
            try {
                if (isPlaying && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    Log.d("Player", "AudioTrack already playing, skipping start")
                    return
                }
            } catch (e: IllegalStateException) {
                // AudioTrack might be in invalid state, continue to reinitialize
                Log.w("Player", "AudioTrack in invalid state, reinitializing", e)
            }

            // Clean up existing track if any
            val oldTrack = audioTrack
            if (oldTrack != null) {
                try {
                    oldTrack.stop()
                    oldTrack.release()
                } catch (e: Exception) {
                    Log.w("Player", "Error cleaning up existing AudioTrack", e)
                }
                audioTrack = null
            }

            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBuf == AudioTrack.ERROR_BAD_VALUE || minBuf == AudioTrack.ERROR) {
                Log.e("Player", "Invalid buffer size: $minBuf")
                return
            }

            // SMOOTH AUDIO FIX: Increase buffer size significantly for smoother playback
            // For 24000 Hz: minBuf is typically ~9600 bytes, so 8x = ~76KB buffer
            // This prevents buffer underruns and choppy audio
            val bufferSize = minBuf * 8

            // FIXED: Use MODE_STREAM for streaming audio chunks
            // This is critical for real-time audio playback
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize) // SMOOTH AUDIO FIX: Larger buffer
                .setTransferMode(AudioTrack.MODE_STREAM) // FIXED: Critical for streaming!
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e("Player", "AudioTrack failed to initialize, state=${track.state}")
                track.release()
                return
            }

            audioTrack = track
            
            // FIXED: Start playback and verify it's actually playing
            // Note: play() returns Unit in some Kotlin/Android versions, so we check playState instead
            track.play()
            
            // Verify playback state after calling play()
            val playState = track.playState
            if (playState != AudioTrack.PLAYSTATE_PLAYING) {
                Log.e("Player", "AudioTrack not in playing state after play(), state=$playState")
                track.release()
                audioTrack = null
                return
            }
            
            isPlaying = true
            Log.d("Player", "AudioTrack started successfully, buffer size=$bufferSize, sample rate=$sampleRate")
        }
    }

    fun playChunk(pcm: ByteArray?) {
        // FIXED: Better validation and error messages
        if (pcm == null || pcm.isEmpty()) {
            Log.w("Player", "Received null or empty PCM chunk")
            return
        }

        // CRASH FIX: Keep write operation inside synchronized block
        // This prevents AudioTrack from being released while we're writing
        synchronized(lock) {
            if (!isPlaying) {
                Log.w("Player", "Player not started, attempting to start...")
                start()
                if (!isPlaying) {
                    Log.e("Player", "Failed to start player, cannot play chunk")
                    return
                }
            }

            // CRASH FIX: Get local reference AFTER start() call
            // start() may create a new AudioTrack, so we need to get the current one
            val track = audioTrack
            if (track == null) {
                Log.e("Player", "AudioTrack is null, cannot play chunk")
                return
            }
            
            // CRASH FIX: Verify track state is valid before proceeding
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e("Player", "AudioTrack not initialized, state=${track.state}")
                return
            }

            // CRASH FIX: Verify track is still valid before using it
            // Double-check that audioTrack hasn't changed (another thread might have released it)
            if (audioTrack !== track) {
                Log.w("Player", "AudioTrack was replaced, skipping chunk")
                return
            }

            // Quick state check
            try {
                val playState = track.playState
                if (playState != AudioTrack.PLAYSTATE_PLAYING) {
                    Log.w("Player", "AudioTrack not playing (state=$playState), attempting to restart...")
                    try {
                        track.stop()
                        track.play()
                        // Verify track is still the same after restart
                        if (audioTrack !== track) {
                            Log.w("Player", "AudioTrack was replaced during restart")
                            return
                        }
                        val newPlayState = track.playState
                        if (newPlayState != AudioTrack.PLAYSTATE_PLAYING) {
                            Log.e("Player", "Failed to restart playback, state=$newPlayState")
                            return
                        }
                    } catch (e: Exception) {
                        Log.e("Player", "Error restarting AudioTrack", e)
                        return
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e("Player", "AudioTrack is in invalid state (possibly released)", e)
                audioTrack = null
                isPlaying = false
                return
            } catch (e: Exception) {
                // CRASH FIX: Catch any other exceptions from playState access
                Log.e("Player", "Exception checking AudioTrack state", e)
                return
            }

            // CRASH FIX: Final verification before write
            if (audioTrack !== track) {
                Log.w("Player", "AudioTrack was replaced before write, skipping chunk")
                return
            }

            // SMOOTH AUDIO FIX: Write audio data while holding the lock
            // This ensures AudioTrack can't be released during write
            try {
                // AudioTrack.write() in MODE_STREAM is non-blocking
                // It writes as much as possible and returns immediately
                val bytesWritten = track.write(pcm, 0, pcm.size)
                
                when {
                    bytesWritten < 0 -> {
                        // Error codes: ERROR, ERROR_BAD_VALUE, ERROR_INVALID_OPERATION, ERROR_DEAD_OBJECT
                        Log.e("Player", "Error writing audio chunk, error code=$bytesWritten")
                    }
                    bytesWritten < pcm.size -> {
                        // SMOOTH AUDIO FIX: Partial write - buffer might be full
                        // With larger buffer (8x), this should be rare
                        // Retry writing remaining data (quick retries only)
                        var offset = bytesWritten
                        var retries = 0
                        while (offset < pcm.size && retries < 2) {
                            // CRASH FIX: Verify track is still valid before each retry
                            if (audioTrack !== track) {
                                Log.w("Player", "AudioTrack was replaced during partial write retry")
                                break
                            }
                            val remaining = pcm.size - offset
                            val written = track.write(pcm, offset, remaining)
                            if (written <= 0) break
                            offset += written
                            retries++
                        }
                        
                        if (offset < pcm.size) {
                            // Some data couldn't be written - buffer is likely full
                            // This is acceptable for streaming - next chunk will fill it
                            Log.v("Player", "Partial write: wrote $offset of ${pcm.size} bytes (buffer may be full)")
                        } else {
                            // All data written successfully
                        }
                    }
                    else -> {
                        // Success - no logging to reduce overhead
                    }
                }
                // Success - no logging to reduce overhead
            } catch (e: IllegalStateException) {
                // CRASH FIX: AudioTrack might have been released during write
                Log.e("Player", "AudioTrack was released during write", e)
                audioTrack = null
                isPlaying = false
            } catch (e: NullPointerException) {
                // CRASH FIX: Handle null pointer exceptions from AudioTrack
                Log.e("Player", "Null pointer exception during write - AudioTrack may be invalid", e)
                audioTrack = null
                isPlaying = false
            } catch (e: Exception) {
                Log.e("Player", "Exception writing audio chunk", e)
            }
        }
    }

    fun stop() {
        // CRASH FIX: Synchronize access to prevent race conditions
        synchronized(lock) {
            if (!isPlaying) return
            
            val track = audioTrack
            if (track != null) {
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                } catch (e: Exception) {
                    Log.e("Player", "stop() error", e)
                }
                try {
                    track.release()
                } catch (e: Exception) {
                    Log.e("Player", "release() error", e)
                }
            }

            audioTrack = null
            isPlaying = false
            Log.d("Player", "Player stopped")
        }
    }
}
