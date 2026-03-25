package com.openbrain.wakeword

import android.content.Context
import android.util.Log
import org.pocketsphinx.Assets
import org.pocketsphinx.Hypothesis
import org.pocketsphinx.RecognitionListener
import org.pocketsphinx.SpeechRecognizer
import org.pocketsphinx.SpeechRecognizerSetup
import java.io.File
import java.io.IOException

class WakeWordEngine(
    private val context: Context,
    private val onWakeUp: () -> Unit,
    private val onSleep: () -> Unit
) : RecognitionListener {

    private var recognizer: SpeechRecognizer? = null
    private val TAG = "WakeWordEngine"
    private val KWS_SEARCH = "wakeup"

    fun start() {
        try {
            val assets = Assets(context)
            val assetsDir = assets.syncAssets()
            
            recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(File(assetsDir, "en-us-ptm"))
                .setDictionary(File(assetsDir, "cmudict-en-us.dict"))
                .setRawLogDir(assetsDir) // Log data to storage
                .setKeywordThreshold(1e-20f) // Sensitivity
                .setBoolean("-allphone_ci", true)
                .getRecognizer()

            recognizer?.addListener(this)

            // Define keywords
            // Threshold is very important for false positives/negatives
            // Format: "keyword /threshold/"
            val keyphraseFile = File(assetsDir, "keywords.list")
            keyphraseFile.writeText("hey adam /1e-20/\ngo to sleep /1e-20/")
            
            recognizer?.addKeywordSearch(KWS_SEARCH, keyphraseFile)
            recognizer?.startListening(KWS_SEARCH)
            Log.d(TAG, "PocketSphinx started listening")
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to setup PocketSphinx assets: ${e.message}")
        }
    }

    fun stop() {
        recognizer?.stop()
        recognizer?.shutdown()
        recognizer = null
        Log.d(TAG, "PocketSphinx stopped")
    }

    override fun onPartialResult(hypothesis: Hypothesis?) {
        if (hypothesis == null) return
        
        val text = hypothesis.hypstr
        Log.d(TAG, "Partial result: $text")
        
        if (text == "hey adam") {
            recognizer?.stop()
            onWakeUp()
            recognizer?.startListening(KWS_SEARCH)
        } else if (text == "go to sleep") {
            recognizer?.stop()
            onSleep()
            recognizer?.startListening(KWS_SEARCH)
        }
    }

    override fun onResult(hypothesis: Hypothesis?) {
        if (hypothesis != null) {
            Log.d(TAG, "Final result: ${hypothesis.hypstr}")
        }
    }

    override fun onBeginningOfSpeech() {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Exception?) {
        Log.e(TAG, "Error: ${error?.message}")
    }
    override fun onTimeout() {}
}
