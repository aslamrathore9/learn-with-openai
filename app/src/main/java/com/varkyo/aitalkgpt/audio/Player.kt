package com.varkyo.aitalkgpt.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

class Player(private val sampleRate: Int = 16000) {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    fun start() {
        if (isPlaying) return

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

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
            .setBufferSizeInBytes(minBuf * 4)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e("Player", "AudioTrack failed to initialize")
            return
        }

        audioTrack = track
        track.play()
        isPlaying = true
    }

    fun playChunk(pcm: ByteArray?) {


        if (!isPlaying || pcm == null){
            Log.e("isPlaying : ","!isPlaying")
            return}
        try {
            Log.d("Player", "PCM chunk size: ${pcm.size}")

            audioTrack?.write(pcm, 0, pcm.size)
        } catch (e: Exception) {
            Log.e("Player", "Error writing audio chunk", e)
        }
    }

    fun stop() {
        if (!isPlaying) return
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            Log.e("Player", "stop() error", e)
        }
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e("Player", "release() error", e)
        }

        audioTrack = null
        isPlaying = false
    }
}
