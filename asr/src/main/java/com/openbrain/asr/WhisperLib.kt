package com.openbrain.asr

import com.openbrain.core.Logger

class WhisperLib {
    companion object {
        private var isLoaded = false
        init {
            try {
                Logger.d("WhisperLib: Loading whisper-jni...")
                System.loadLibrary("whisper-jni")
                isLoaded = true
                Logger.i("WhisperLib: whisper-jni loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Logger.e("WhisperLib: Failed to load whisper-jni", e)
            } catch (e: Exception) {
                Logger.e("WhisperLib: Unexpected error loading whisper-jni", e)
            }
        }
        fun isAvailable() = isLoaded
    }

    fun initWhisper(modelPath: String): Long {
        Logger.d("WhisperLib.initWhisper(path=$modelPath)")
        return try {
            val ctx = nativeInitWhisper(modelPath)
            Logger.d("WhisperLib.initWhisper returned $ctx")
            ctx
        } catch (e: Exception) {
            Logger.e("WhisperLib.initWhisper exception", e)
            0L
        }
    }

    fun transcribeChunk(contextPtr: Long, pcmData: FloatArray): String {
        return try {
            nativeTranscribeChunk(contextPtr, pcmData)
        } catch (e: Exception) {
            Logger.e("WhisperLib.transcribeChunk exception", e)
            ""
        }
    }

    fun freeWhisper(contextPtr: Long) {
        Logger.d("WhisperLib.freeWhisper(ptr=$contextPtr)")
        try {
            nativeFreeWhisper(contextPtr)
        } catch (e: Exception) {
            Logger.e("WhisperLib.freeWhisper exception", e)
        }
    }

    private external fun nativeInitWhisper(modelPath: String): Long
    private external fun nativeTranscribeChunk(contextPtr: Long, pcmData: FloatArray): String
    private external fun nativeFreeWhisper(contextPtr: Long)
}
