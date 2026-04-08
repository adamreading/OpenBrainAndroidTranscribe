package com.openbrain.wakeword

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.openbrain.core.Logger

/**
 * Wake word detection engine using Android's built-in SpeechRecognizer in offline mode.
 *
 * This is a fallback implementation until a proper ONNX model for "hey adam" is trained.
 * The interface is designed so ONNX Runtime can be swapped in later — just replace the
 * internal implementation while keeping start(), stop(), and feedAudio() signatures.
 *
 * Note: feedAudio() is part of the interface for future ONNX-based detection where raw
 * PCM is processed directly. The current SpeechRecognizer fallback uses its own mic input
 * but the AudioPipeline still registers this as a listener for interface compatibility.
 */
class WakeWordEngine(
    private val context: Context,
    private val onTranscriptUpdate: (String) -> Unit,
    private val onWakeUp: () -> Unit,
    private val onSleep: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalSystemVolume: Int = -1

    @Volatile
    private var isRunning = false
    private var isAwake = false

    private var wakeWord = "hey adam"
    private var sleepWord = "go to sleep"

    fun setWakeWord(word: String) { wakeWord = word.lowercase().trim() }
    fun setSleepWord(word: String) { sleepWord = word.lowercase().trim() }

    fun start() {
        if (isRunning) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Logger.e("SpeechRecognizer not available")
            return
        }
        isRunning = true
        Logger.i("System Speech Engine: Starting...")
        startListening()
    }

    fun stop() {
        isRunning = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        Logger.i("System Speech Engine: Stopped")
    }

    private fun startListening() {
        if (!isRunning) return
        
        // Use the main thread for SpeechRecognizer
        android.os.Handler(context.mainLooper).post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(recognitionListener)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // Request offline if possible, but allow online for better accuracy
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Logger.e("Error starting recognizer", e)
                restartAfterDelay()
            }
        }
    }

    private fun restartAfterDelay(delayMillis: Long = 500) {
        if (!isRunning) return
        android.os.Handler(context.mainLooper).postDelayed({
            startListening()
        }, delayMillis)
    }

    private fun processText(text: String, isFinal: Boolean) {
        val lower = text.lowercase()
        
        // 1. Check for State Toggles (Allow partials for faster wake word detection)
        if (lower.contains(wakeWord) && !isAwake) {
            Logger.i("Wake word detected!")
            isAwake = true
            onWakeUp()
        } else if (lower.contains(sleepWord) && isAwake) {
            Logger.i("Sleep word detected!")
            isAwake = false
            onSleep()
        }

        // 2. If we are "Awake", send transcription to the UI/Server
        // Only send final results to avoid duplicating partial strings in AmbientState
        if (isAwake && isFinal) {
            onTranscriptUpdate(text)
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No recognition result matched"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                SpeechRecognizer.ERROR_SERVER -> "Server sends error status"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error: $error"
            }

            // Error 7 (No match) and 6 (Speech timeout) are normal in ambient mode
            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Logger.w("Speech Engine Error: $error ($message)")
            }

            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                restartAfterDelay(2000) // Wait longer if busy
            } else {
                restartAfterDelay()
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { processText(it, true) }
            restartAfterDelay()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { processText(it, false) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

