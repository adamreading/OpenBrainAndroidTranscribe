#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Custom log callback to capture whisper.cpp internal errors
void whisper_log_callback(ggml_log_level level, const char * text, void * user_data) {
    if (level == GGML_LOG_LEVEL_ERROR) LOGE("whisper.cpp: %s", text);
    else LOGD("whisper.cpp: %s", text);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_openbrain_asr_WhisperLib_nativeInitWhisper(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Initializing Whisper with model: %s", path);

    whisper_log_set(whisper_log_callback, nullptr);

    // Check if file exists and is readable
    FILE *f = fopen(path, "rb");
    if (f == nullptr) {
        LOGE("Cannot open Whisper model file for reading: %s (errno: %d)", path, errno);
    } else {
        fclose(f);
        LOGD("Whisper model file is readable");
    }

    struct whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    params.flash_attn = false; // Disable flash attention for better compatibility on older/varied ARM devices

    struct whisper_context * ctx = whisper_init_from_file_with_params(path, params);
    
    env->ReleaseStringUTFChars(model_path, path);

    if (ctx == nullptr) {
        LOGE("Failed to initialize Whisper context (whisper_init_from_file returned null)");
        return 0;
    }

    LOGD("Whisper initialized successfully: %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_openbrain_asr_WhisperLib_nativeTranscribeChunk(JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray pcm_data) {
    struct whisper_context * ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx == nullptr) return env->NewStringUTF("");

    jsize len = env->GetArrayLength(pcm_data);
    float * samples = env->GetFloatArrayElements(pcm_data, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = 4;
    params.print_progress = false;
    params.language = "en";

    if (whisper_full(ctx, params, samples, len) != 0) {
        LOGE("Failed to process audio chunk");
        env->ReleaseFloatArrayElements(pcm_data, samples, JNI_ABORT);
        return env->NewStringUTF("");
    }

    std::string result = "";
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        result += whisper_full_get_segment_text(ctx, i);
    }

    env->ReleaseFloatArrayElements(pcm_data, samples, JNI_ABORT);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_openbrain_asr_WhisperLib_nativeFreeWhisper(JNIEnv *env, jobject thiz, jlong context_ptr) {
    struct whisper_context * ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx != nullptr) {
        LOGD("Freeing Whisper context: %p", ctx);
        whisper_free(ctx);
    }
}
