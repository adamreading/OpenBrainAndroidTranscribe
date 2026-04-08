package com.openbrain.llm

import com.openbrain.core.Logger

class LlamaLib {
    companion object {
        private var isLoaded = false
        init {
            try {
                Logger.d("LlamaLib: Loading llama-jni...")
                System.loadLibrary("llama-jni")
                isLoaded = true
                Logger.i("LlamaLib: llama-jni loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Logger.e("LlamaLib: Failed to load llama-jni", e)
            } catch (e: Exception) {
                Logger.e("LlamaLib: Unexpected error loading llama-jni", e)
            }
        }
        fun isAvailable() = isLoaded
    }

    fun initLlama(modelPath: String): Long {
        Logger.d("LlamaLib.initLlama(path=$modelPath)")
        return try {
            val ctx = nativeInitLlama(modelPath)
            Logger.d("LlamaLib.initLlama returned $ctx")
            ctx
        } catch (e: Exception) {
            Logger.e("LlamaLib.initLlama exception", e)
            0L
        }
    }

    fun runInference(contextPtr: Long, prompt: String, maxTokens: Int): String {
        return try {
            nativeRunInference(contextPtr, prompt, maxTokens)
        } catch (e: Exception) {
            Logger.e("LlamaLib.runInference exception", e)
            ""
        }
    }

    fun freeLlama(contextPtr: Long) {
        Logger.d("LlamaLib.freeLlama(ptr=$contextPtr)")
        try {
            nativeFreeLlama(contextPtr)
        } catch (e: Exception) {
            Logger.e("LlamaLib.freeLlama exception", e)
        }
    }

    private external fun nativeInitLlama(modelPath: String): Long
    private external fun nativeRunInference(contextPtr: Long, prompt: String, maxTokens: Int): String
    private external fun nativeFreeLlama(contextPtr: Long)
}
