package com.openbrain.ambient

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

class AudioPipeline : Closeable {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val TAG = "AudioPipeline"
        private const val CHUNK_DURATION_MS = 100
    }

    interface AudioPipelineListener {
        fun onAudioChunk(pcm: ShortArray)
    }

    private var audioRecord: AudioRecord? = null
    private val listeners = CopyOnWriteArrayList<AudioPipelineListener>()
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun addListener(listener: AudioPipelineListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: AudioPipelineListener) {
        listeners.remove(listener)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (audioRecord != null) return

        val chunkSize = SAMPLE_RATE * CHUNK_DURATION_MS / 1000 // 1600 samples per 100ms
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, chunkSize * 2 * 10) // At least 10x chunk size

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            audioRecord?.release()
            audioRecord = null
            return
        }

        audioRecord?.startRecording()
        Log.d(TAG, "AudioRecord started, broadcasting ${CHUNK_DURATION_MS}ms chunks to ${listeners.size} listeners")

        captureJob = scope.launch {
            val buffer = ShortArray(chunkSize)
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    for (listener in listeners) {
                        try {
                            listener.onAudioChunk(chunk)
                        } catch (e: Exception) {
                            Log.e(TAG, "Listener error: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "AudioPipeline stopped")
    }

    override fun close() {
        stop()
        scope.cancel()
    }
}
