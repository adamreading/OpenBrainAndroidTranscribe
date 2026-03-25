package com.openbrain.asr

class WhisperLib {
    companion object {
        init {
            System.loadLibrary("whisper-jni")
        }
    }

    external fun initWhisper(modelPath: String): Long
    external fun transcribeChunk(contextPtr: Long, pcmData: FloatArray): String
    external fun freeWhisper(contextPtr: Long)
}
