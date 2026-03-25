package com.openbrain.ambient

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openbrain.asr.WhisperLib
import com.openbrain.wakeword.WakeWordEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File

class AmbientService : Service() {

    private var wakeWordEngine: WakeWordEngine? = null
    private val TAG = "AmbientService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private val whisperLib = WhisperLib()
    private var whisperContext: Long = 0
    private val audioCaptureManager = AudioCaptureManager()
    private var transcriptionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initWhisper()
        observeState()
    }

    private fun initWhisper() {
        serviceScope.launch(Dispatchers.IO) {
            val modelFile = File(getExternalFilesDir(null), "ggml-tiny.en.bin")
            if (modelFile.exists()) {
                whisperContext = whisperLib.initWhisper(modelFile.absolutePath)
                Log.d(TAG, "Whisper initialized: $whisperContext")
            } else {
                Log.e(TAG, "Whisper model not found at ${modelFile.absolutePath}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification(AmbientState.isActive.value)
        startAmbientListening()
        return START_STICKY
    }

    private fun observeState() {
        AmbientState.isActive
            .onEach { isActive ->
                Log.d(TAG, "State changed: isActive = $isActive")
                updateNotification(isActive)
                if (isActive) {
                    startTranscription()
                } else {
                    stopTranscription()
                }
            }
            .launchIn(serviceScope)
    }

    private fun startAmbientListening() {
        try {
            wakeWordEngine = WakeWordEngine(
                context = this,
                onWakeUp = {
                    Log.d(TAG, "Wake word triggered: Hey Adam")
                    AmbientState.setActive(true)
                },
                onSleep = {
                    Log.d(TAG, "Sleep word triggered: Go to sleep")
                    AmbientState.setActive(false)
                }
            )
            wakeWordEngine?.start()
            Log.d(TAG, "WakeWordEngine started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting WakeWordEngine: ${e.message}")
        }
    }

    private fun startTranscription() {
        audioCaptureManager.startCapture()
        transcriptionJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(5000) // Process every 5 seconds
                if (whisperContext != 0L) {
                    val chunk = audioCaptureManager.getNextChunk(5)
                    if (chunk != null) {
                        Log.d(TAG, "Transcribing chunk of size ${chunk.size}")
                        val text = whisperLib.transcribeChunk(whisperContext, chunk)
                        if (text.isNotBlank()) {
                            Log.d(TAG, "Transcript: $text")
                            withContext(Dispatchers.Main) {
                                AmbientState.appendTranscript(text)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopTranscription() {
        transcriptionJob?.cancel()
        audioCaptureManager.stopCapture()
    }

    private fun updateNotification(isActive: Boolean) {
        val status = if (isActive) "Listening" else "Sleeping"
        val notification = NotificationCompat.Builder(this, "ambient_channel")
            .setContentTitle("OpenBrain Ambient")
            .setContentText("Status: $status")
            .setSmallIcon(if (isActive) android.R.drawable.presence_audio_online else android.R.drawable.presence_audio_busy)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "ambient_channel",
            "Ambient Listening",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeWordEngine?.stop()
        stopTranscription()
        if (whisperContext != 0L) {
            whisperLib.freeWhisper(whisperContext)
        }
        Log.d(TAG, "Service destroyed, WakeWordEngine and Whisper stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
