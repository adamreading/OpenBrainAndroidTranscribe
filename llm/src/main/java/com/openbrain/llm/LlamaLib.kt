package com.openbrain.llm

class LlamaLib {
    companion object {
        init {
            System.loadLibrary("llama-jni")
        }
    }

    external fun initLlama(modelPath: String): Long
    external fun runInference(contextPtr: Long, prompt: String, maxTokens: Int): String
    external fun freeLlama(contextPtr: Long)
}
