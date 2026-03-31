package com.openbrain.wakeword

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

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
    private val onWakeUp: () -> Unit,
    private val onSleep: () -> Unit
) {

    private val TAG = "WakeWordEngine"
    private var speechRecognizer: SpeechRecognizer? = null

    @Volatile
    private var isRunning = false

    private var wakeWord = "hey adam"
    private var sleepWord = "go to sleep"

    fun setWakeWord(word: String) {
        wakeWord = word.lowercase().trim()
    }

    fun setSleepWord(word: String) {
        sleepWord = word.lowercase().trim()
    }

    fun start() {
        if (isRunning) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer not available on this device")
            return
        }

        isRunning = true
        startListening()
        Log.d(TAG, "WakeWordEngine started (SpeechRecognizer offline fallback)")
    }

    fun stop() {
        isRunning = false
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d(TAG, "WakeWordEngine stopped")
    }

    /**
     * Feed raw PCM audio from the unified pipeline.
     * Currently a no-op — the SpeechRecognizer fallback uses its own mic.
     * When ONNX model is integrated, this will process audio directly.
     */
    fun feedAudio(pcm: ShortArray) {
        // Future ONNX implementation will process raw PCM here.
        // SpeechRecognizer fallback uses its own audio input.
    }

    private fun startListening() {
        if (!isRunning) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}")
            restartAfterDelay()
        }
    }

    private fun restartAfterDelay() {
        if (!isRunning) return
        android.os.Handler(context.mainLooper).postDelayed({
            if (isRunning) startListening()
        }, 1000)
    }

    private fun checkForKeywords(text: String) {
        val lower = text.lowercase().trim()
        when {
            lower.contains(wakeWord) -> {
                Log.d(TAG, "Wake word detected: $wakeWord")
                onWakeUp()
            }
            lower.contains(sleepWord) -> {
                Log.d(TAG, "Sleep word detected: $sleepWord")
                onSleep()
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error $error"
            }
            Log.d(TAG, "Recognition error: $errorMsg")
            restartAfterDelay()
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach { text ->
                checkForKeywords(text)
            }
            restartAfterDelay()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach { text ->
                checkForKeywords(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
