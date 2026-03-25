package com.openbrain.ambient

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class AudioCaptureManager {
    private val TAG = "AudioCaptureManager"
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val audioBufferQueue = ConcurrentLinkedQueue<FloatArray>()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun startCapture() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize * 10
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            return
        }

        audioRecord?.startRecording()
        
        captureJob = scope.launch {
            val buffer = ShortArray(SAMPLE_RATE * 1) // 1 second buffer
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val floatBuffer = FloatArray(read)
                    for (i in 0 until read) {
                        floatBuffer[i] = buffer[i].toFloat() / 32768.0f
                    }
                    audioBufferQueue.add(floatBuffer)
                    
                    // Keep only last 30 seconds of audio in queue
                    while (audioBufferQueue.size > 30) {
                        audioBufferQueue.poll()
                    }
                }
            }
        }
        Log.d(TAG, "Audio capture started")
    }

    fun stopCapture() {
        captureJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioBufferQueue.clear()
        Log.d(TAG, "Audio capture stopped")
    }

    fun getNextChunk(seconds: Int): FloatArray? {
        val neededSamples = SAMPLE_RATE * seconds
        val combined = mutableListOf<Float>()
        
        var collected = 0
        while (collected < neededSamples && audioBufferQueue.isNotEmpty()) {
            val chunk = audioBufferQueue.poll() ?: break
            combined.addAll(chunk.toList())
            collected += chunk.size
        }
        
        return if (combined.isEmpty()) null else combined.toFloatArray()
    }
}
