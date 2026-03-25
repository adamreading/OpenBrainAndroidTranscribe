#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.cpp/whisper.h"

#define TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct whisper_context * g_whisper_ctx = nullptr;

extern "C"
JNIEXPORT jlong JNICALL
Java_com_openbrain_asr_WhisperLib_initWhisper(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Initializing Whisper with model: %s", path);

    struct whisper_context_params params = whisper_context_default_params();
    g_whisper_ctx = whisper_init_from_file_with_params(path, params);
    
    env->ReleaseStringUTFChars(model_path, path);

    if (g_whisper_ctx == nullptr) {
        LOGE("Failed to initialize Whisper context");
        return 0;
    }

    return reinterpret_cast<jlong>(g_whisper_ctx);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_openbrain_asr_WhisperLib_transcribeChunk(JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray pcm_data) {
    struct whisper_context * ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx == nullptr) return env->NewStringUTF(nullptr);

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
Java_com_openbrain_asr_WhisperLib_freeWhisper(JNIEnv *env, jobject thiz, jlong context_ptr) {
    struct whisper_context * ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}
