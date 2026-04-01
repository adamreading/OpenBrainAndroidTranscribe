package com.openbrain.ambient

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openbrain.asr.WhisperLib
import com.openbrain.client.MemoryRequest
import com.openbrain.client.MemorySyncWorker
import com.openbrain.llm.LlamaLib
import com.openbrain.llm.MemoryExtractor
import com.openbrain.wakeword.WakeWordEngine
import com.openbrain.ambient.AppSettings.settingsDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.util.UUID

class AmbientService : Service() {

    private val TAG = "AmbientService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Audio pipeline
    private var audioPipeline: AudioPipeline? = null
    private val audioCaptureManager = AudioCaptureManager()
    private var wakeWordEngine: WakeWordEngine? = null

    // Whisper
    private val whisperLib = WhisperLib()
    private var whisperContext: Long = 0
    private var transcriptionJob: Job? = null

    // LLM
    private var llamaLib: LlamaLib? = null
    private var memoryExtractor: MemoryExtractor? = null
    private var llamaContext: Long = 0

    // Session
    private var sessionId: String = UUID.randomUUID().toString()
    private var extractionJob: Job? = null
    private var activeStartTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AmbientState.init(this)
        initWhisper()
        initLlama()
        initAudioPipeline()
        observeState()
    }

    private fun initAudioPipeline() {
        audioPipeline = AudioPipeline()

        // Register AudioCaptureManager as a listener
        audioPipeline?.addListener(audioCaptureManager)

        // Create and register WakeWordEngine
        wakeWordEngine = WakeWordEngine(
            context = this,
            onWakeUp = {
                Log.d(TAG, "Wake word triggered: Hey Adam")
                serviceScope.launch {
                    sessionId = UUID.randomUUID().toString()
                    AmbientState.setActive(this@AmbientService, true)
                }
            },
            onSleep = {
                Log.d(TAG, "Sleep word triggered: Go to sleep")
                serviceScope.launch {
                    runExtraction()
                    AmbientState.setActive(this@AmbientService, false)
                }
            }
        )

        // Load wake/sleep words from settings
        serviceScope.launch(Dispatchers.IO) {
            val wakeWord = settingsDataStore.data.map { it[AppSettings.WAKE_WORD] ?: "hey adam" }.first()
            val sleepWord = settingsDataStore.data.map { it[AppSettings.SLEEP_WORD] ?: "go to sleep" }.first()
            wakeWordEngine?.setWakeWord(wakeWord)
            wakeWordEngine?.setSleepWord(sleepWord)
        }

        // Register WakeWordEngine as a pipeline listener (for future ONNX integration)
        audioPipeline?.addListener(object : AudioPipeline.AudioPipelineListener {
            override fun onAudioChunk(pcm: ShortArray) {
                wakeWordEngine?.feedAudio(pcm)
            }
        })

        // Start audio pipeline and wake word engine
        audioPipeline?.start()
        wakeWordEngine?.start()
        Log.d(TAG, "Audio pipeline and WakeWordEngine started")
    }

    private fun initWhisper() {
        serviceScope.launch(Dispatchers.IO) {
            val whisperModel = settingsDataStore.data
                .map { it[AppSettings.WHISPER_MODEL] ?: "tiny" }
                .first()
            val modelFile = File(getExternalFilesDir("whisper"), "ggml-${whisperModel}.en.bin")
            if (modelFile.exists()) {
                whisperContext = whisperLib.initWhisper(modelFile.absolutePath)
                Log.d(TAG, "Whisper initialized: $whisperContext")
            } else {
                // Fallback to old path
                val fallbackFile = File(getExternalFilesDir(null), "ggml-tiny.en.bin")
                if (fallbackFile.exists()) {
                    whisperContext = whisperLib.initWhisper(fallbackFile.absolutePath)
                    Log.d(TAG, "Whisper initialized (fallback): $whisperContext")
                } else {
                    Log.e(TAG, "Whisper model not found at ${modelFile.absolutePath}")
                }
            }
        }
    }

    private fun initLlama() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                llamaLib = LlamaLib()
                memoryExtractor = MemoryExtractor(llamaLib!!)

                val llmModel = settingsDataStore.data
                    .map { it[AppSettings.LLM_MODEL] ?: "phi-3-mini-4k-instruct-q4.gguf" }
                    .first()
                val modelFile = File(getExternalFilesDir("llm"), llmModel)
                if (modelFile.exists()) {
                    llamaContext = llamaLib!!.initLlama(modelFile.absolutePath)
                    Log.d(TAG, "LLM initialized: $llamaContext")
                } else {
                    Log.w(TAG, "LLM model not found at ${modelFile.absolutePath} — extraction disabled")
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "llama-jni native library not available — extraction disabled: ${e.message}")
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
                Log.d(TAG, "State changed: isActive = $isActive")
                updateNotification(isActive)
                if (isActive) {
                    activeStartTime = System.currentTimeMillis()
                    startTranscription()
                    startExtractionTimer()
                } else {
                    stopTranscription()
                    stopExtractionTimer()
                }
            }
            .launchIn(serviceScope)
    }

    private fun startTranscription() {
        audioCaptureManager.startCapture()
        transcriptionJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(5000)
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

    private fun startExtractionTimer() {
        extractionJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000) // Every 60 seconds
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
            Log.d(TAG, "Running memory extraction...")
            val items = withContext(Dispatchers.Default) {
                memoryExtractor!!.extract(transcript, sessionId, llamaContext)
            }

            if (items.isNotEmpty()) {
                Log.d(TAG, "Extracted ${items.size} memory items")

                val baseUrl = settingsDataStore.data
                    .map { it[AppSettings.SUPABASE_URL] ?: "" }
                    .first()
                val apiKey = settingsDataStore.data
                    .map { it[AppSettings.SUPABASE_API_KEY] ?: "" }
                    .first()

                for (item in items) {
                    val request = MemoryRequest(
                        timestamp = item.timestamp,
                        category = item.category,
                        text = item.text,
                        tags = item.tags,
                        source = item.source,
                        session_id = item.sessionId
                    )
                    MemorySyncWorker.enqueueMemory(this@AmbientService, request)
                }

                if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                    MemorySyncWorker.enqueue(this@AmbientService, baseUrl, apiKey)
                    AmbientState.addSyncLogEntry(
                        this@AmbientService,
                        AmbientState.SyncLogEntry(
                            System.currentTimeMillis(),
                            "success",
                            "Enqueued ${items.size} memories for sync"
                        )
                    )
                } else {
                    AmbientState.addSyncLogEntry(
                        this@AmbientService,
                        AmbientState.SyncLogEntry(
                            System.currentTimeMillis(),
                            "error",
                            "Supabase not configured — memories queued locally"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction error: ${e.message}")
            AmbientState.addSyncLogEntry(
                this@AmbientService,
                AmbientState.SyncLogEntry(
                    System.currentTimeMillis(),
                    "error",
                    "Extraction failed: ${e.message}"
                )
            )
        }
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
        stopExtractionTimer()
        audioPipeline?.close()
        audioCaptureManager.close()
        if (whisperContext != 0L) {
            whisperLib.freeWhisper(whisperContext)
        }
        if (llamaContext != 0L) {
            llamaLib?.freeLlama(llamaContext)
        }
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed — all resources released")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
