package com.openbrain.ambient

import android.content.pm.ServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.openbrain.client.MemoryRequest
import com.openbrain.client.MemorySyncWorker
import com.openbrain.llm.LlamaLib
import com.openbrain.llm.MemoryExtractor
import com.openbrain.wakeword.WakeWordEngine
import com.openbrain.core.AppSettings
import com.openbrain.core.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.util.UUID

class AmbientService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Engines
    private var speechEngine: WakeWordEngine? = null
    private var llamaLib: LlamaLib? = null
    private var memoryExtractor: MemoryExtractor? = null
    private var llamaContext: Long = 0
    private var isLlamaInitializing = false

    // Session
    private var sessionId: String = UUID.randomUUID().toString()
    private var extractionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Logger.i("AmbientService starting with System Speech Engine")
        try {
            createNotificationChannel()
            updateNotification(false)
            AmbientState.init(this)
            initSpeechEngine()
            observeState()
        } catch (e: Exception) {
            Logger.e("AmbientService onCreate FAILED", e)
        }
    }

    private fun initSpeechEngine() {
        speechEngine = WakeWordEngine(
            context = this,
            onTranscriptUpdate = { text ->
                if (AmbientState.isActive.value) {
                    AmbientState.appendTranscript(text)
                }
            },
            onWakeUp = {
                Logger.i("Wake word detected")
                serviceScope.launch {
                    sessionId = UUID.randomUUID().toString()
                    AmbientState.setActive(this@AmbientService, true)
                }
            },
            onSleep = {
                Logger.i("Sleep word detected")
                serviceScope.launch {
                    runExtraction() // Final extraction before sleep
                    AmbientState.setActive(this@AmbientService, false)
                }
            }
        )

        serviceScope.launch(Dispatchers.IO) {
            val settings = AppSettings.getInstance(applicationContext).data.first()
            val wake = settings[AppSettings.WAKE_WORD] ?: "hey adam"
            val sleep = settings[AppSettings.SLEEP_WORD] ?: "go to sleep"
            speechEngine?.setWakeWord(wake)
            speechEngine?.setSleepWord(sleep)
            speechEngine?.start()
        }
    }

    private fun initLlama() {
        if (llamaContext != 0L || isLlamaInitializing) return
        
        isLlamaInitializing = true
        serviceScope.launch(Dispatchers.IO) {
            try {
                if (llamaLib == null) llamaLib = LlamaLib()
                if (memoryExtractor == null) memoryExtractor = MemoryExtractor(llamaLib!!)

                val data = AppSettings.getInstance(applicationContext).data.first()
                val llmModel = data[AppSettings.LLM_MODEL] ?: "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
                val modelFile = File(getExternalFilesDir("llm"), llmModel)
                
                if (modelFile.exists()) {
                    Logger.d("Initializing LLM: ${modelFile.name}")
                    llamaContext = llamaLib!!.initLlama(modelFile.absolutePath)
                    Logger.i("LLM ready: $llamaContext")
                } else {
                    Logger.e("LLM model file missing")
                }
            } catch (e: Exception) {
                Logger.e("initLlama failed", e)
            } finally {
                isLlamaInitializing = false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification(AmbientState.isActive.value)
        return START_STICKY
    }

    private fun observeState() {
        AmbientState.isActive
            .onEach { isActive ->
                updateNotification(isActive)
                if (isActive) {
                    if (llamaContext == 0L) initLlama()
                    startExtractionTimer()
                } else {
                    stopExtractionTimer()
                }
            }
            .launchIn(serviceScope)
    }

    private fun startExtractionTimer() {
        extractionJob?.cancel()
        extractionJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000) // Extract memories every minute
                runExtraction()
            }
        }
    }

    private fun stopExtractionTimer() {
        extractionJob?.cancel()
    }

    private suspend fun runExtraction() {
        if (llamaContext == 0L || memoryExtractor == null) return
        
        val transcript = AmbientState.transcript.value
        if (transcript.isBlank()) return

        try {
            Logger.d("Running LLM extraction...")
            val items = withContext(Dispatchers.Default) {
                memoryExtractor!!.extract(transcript, sessionId, llamaContext)
            }
            if (items.isNotEmpty()) {
                val settings = AppSettings.getInstance(applicationContext).data.first()
                val url = settings[AppSettings.SUPABASE_URL] ?: ""
                val key = settings[AppSettings.SUPABASE_API_KEY] ?: ""

                for (item in items) {
                    val request = MemoryRequest(item.timestamp, item.category, item.text, item.tags, item.source, item.sessionId)
                    MemorySyncWorker.enqueueMemory(this@AmbientService, request)
                }
                if (url.isNotBlank()) {
                    MemorySyncWorker.enqueue(this@AmbientService, url, key)
                }
            }
        } catch (e: Exception) {
            Logger.e("Extraction failed", e)
        }
    }

    private fun updateNotification(isActive: Boolean) {
        val status = if (isActive) "Listening" else "Watching for Wake Word"
        val builder = NotificationCompat.Builder(this, "ambient_channel")
            .setContentTitle("OpenBrain")
            .setContentText(status)
            .setSmallIcon(if (isActive) android.R.drawable.presence_audio_online else android.R.drawable.presence_audio_busy)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("ambient_channel", "Ambient", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        speechEngine?.stop()
        stopExtractionTimer()
        if (llamaContext != 0L) {
            llamaLib?.freeLlama(llamaContext)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
