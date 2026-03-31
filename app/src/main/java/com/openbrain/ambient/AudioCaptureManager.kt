package com.openbrain.ambient

import android.util.Log
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue

class AudioCaptureManager : AudioPipeline.AudioPipelineListener, Closeable {

    private val TAG = "AudioCaptureManager"
    private val audioBufferQueue = ConcurrentLinkedQueue<FloatArray>()

    @Volatile
    private var capturing = false

    fun startCapture() {
        capturing = true
        audioBufferQueue.clear()
        Log.d(TAG, "Audio capture started (listener mode)")
    }

    fun stopCapture() {
        capturing = false
        audioBufferQueue.clear()
        Log.d(TAG, "Audio capture stopped")
    }

    override fun onAudioChunk(pcm: ShortArray) {
        if (!capturing) return

        val floatBuffer = FloatArray(pcm.size)
        for (i in pcm.indices) {
            floatBuffer[i] = pcm[i].toFloat() / 32768.0f
        }
        audioBufferQueue.add(floatBuffer)

        // Keep only last 60 seconds of audio in queue
        // Each chunk is 100ms = 1600 samples at 16kHz, so 600 chunks = 60s
        while (audioBufferQueue.size > 600) {
            audioBufferQueue.poll()
        }
    }

    fun getNextChunk(seconds: Int): FloatArray? {
        val neededSamples = AudioPipeline.SAMPLE_RATE * seconds
        val combined = mutableListOf<Float>()

        var collected = 0
        while (collected < neededSamples && audioBufferQueue.isNotEmpty()) {
            val chunk = audioBufferQueue.poll() ?: break
            combined.addAll(chunk.toList())
            collected += chunk.size
        }

        return if (combined.isEmpty()) null else combined.toFloatArray()
    }

    override fun close() {
        stopCapture()
    }
}
