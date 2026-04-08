#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Custom log callback to capture llama.cpp internal errors
void llama_log_callback(ggml_log_level level, const char * text, void * user_data) {
    if (level == GGML_LOG_LEVEL_ERROR) LOGE("llama.cpp: %s", text);
    else LOGD("llama.cpp: %s", text);
}

// Helper to add a token to a batch (replaces removed llama_batch_add)
static void batch_add(llama_batch & batch, llama_token id, llama_pos pos, const std::vector<llama_seq_id> & seq_ids, bool logits) {
    batch.token[batch.n_tokens]    = id;
    batch.pos[batch.n_tokens]      = pos;
    batch.n_seq_id[batch.n_tokens] = (int32_t) seq_ids.size();
    for (size_t i = 0; i < seq_ids.size(); i++) {
        batch.seq_id[batch.n_tokens][i] = seq_ids[i];
    }
    batch.logits[batch.n_tokens]   = logits;
    batch.n_tokens++;
}

struct LlamaContext {
    llama_model * model;
    llama_context * ctx;
};

extern "C"
JNIEXPORT jlong JNICALL
Java_com_openbrain_llm_LlamaLib_nativeInitLlama(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Initializing llama with model: %s", path);

    llama_log_set(llama_log_callback, nullptr);

    // Check if file exists and is readable
    FILE *f = fopen(path, "rb");
    if (f == nullptr) {
        LOGE("Cannot open model file for reading: %s (errno: %d)", path, errno);
    } else {
        fclose(f);
        LOGD("Model file is readable");
    }

    llama_backend_init();

    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.use_mmap = true; // For 2.4GB, mmap is actually better to avoid heap exhaustion

    LOGD("Loading model from file...");
    llama_model * model = llama_model_load_from_file(path, model_params);

    env->ReleaseStringUTFChars(model_path, path);

    if (model == nullptr) {
        LOGE("Failed to load llama model (llama_model_load_from_file returned null)");
        return 0;
    }

    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    // Samsung S26 Ultra has high core count — use 6 threads
    ctx_params.n_threads = 6;

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        return 0;
    }

    auto * wrapper = new LlamaContext{model, ctx};
    LOGD("Llama initialized successfully");
    return reinterpret_cast<jlong>(wrapper);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_openbrain_llm_LlamaLib_nativeRunInference(JNIEnv *env, jobject thiz, jlong context_ptr, jstring prompt_str, jint max_tokens) {
    auto * wrapper = reinterpret_cast<LlamaContext *>(context_ptr);
    if (wrapper == nullptr || wrapper->ctx == nullptr) {
        return env->NewStringUTF("");
    }

    const char *prompt = env->GetStringUTFChars(prompt_str, nullptr);
    const llama_vocab * vocab = llama_model_get_vocab(wrapper->model);

    // Tokenize the prompt
    const int n_prompt_max = 2048;
    std::vector<llama_token> tokens(n_prompt_max);
    int n_tokens = llama_tokenize(vocab, prompt, (int32_t) strlen(prompt), tokens.data(), n_prompt_max, true, false);

    env->ReleaseStringUTFChars(prompt_str, prompt);

    if (n_tokens < 0) {
        LOGE("Failed to tokenize prompt");
        return env->NewStringUTF("");
    }
    tokens.resize(n_tokens);

    // Clear KV cache
    llama_memory_clear(llama_get_memory(wrapper->ctx), true);

    // Evaluate prompt tokens in a single batch
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(wrapper->ctx, batch) != 0) {
        LOGE("Failed to decode prompt");
        llama_batch_free(batch);
        return env->NewStringUTF("");
    }

    // Generate tokens with greedy sampling for deterministic JSON output
    std::string result;
    int n_cur = n_tokens;
    const int n_gen = max_tokens;

    for (int i = 0; i < n_gen; i++) {
        auto * logits = llama_get_logits_ith(wrapper->ctx, batch.n_tokens - 1);
        const int n_vocab = llama_vocab_n_tokens(vocab);

        // Greedy sampling
        llama_token best_token = 0;
        float best_logit = logits[0];
        for (int j = 1; j < n_vocab; j++) {
            if (logits[j] > best_logit) {
                best_logit = logits[j];
                best_token = j;
            }
        }

        // Check for EOS
        if (llama_vocab_is_eog(vocab, best_token)) {
            break;
        }

        // Convert token to text
        char buf[256];
        int n = llama_token_to_piece(vocab, best_token, buf, sizeof(buf), 0, false);
        if (n > 0) {
            result.append(buf, n);
        }

        // Prepare next batch
        batch.n_tokens = 0;
        batch_add(batch, best_token, n_cur, {0}, true);
        n_cur++;

        if (llama_decode(wrapper->ctx, batch) != 0) {
            LOGE("Failed to decode token at position %d", n_cur);
            break;
        }
    }

    llama_batch_free(batch);

    LOGD("Inference complete, output length: %zu", result.size());
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_openbrain_llm_LlamaLib_nativeFreeLlama(JNIEnv *env, jobject thiz, jlong context_ptr) {
    auto * wrapper = reinterpret_cast<LlamaContext *>(context_ptr);
    if (wrapper != nullptr) {
        if (wrapper->ctx != nullptr) {
            llama_free(wrapper->ctx);
        }
        if (wrapper->model != nullptr) {
            llama_model_free(wrapper->model);
        }
        delete wrapper;
        LOGD("Llama context freed");
    }
    llama_backend_free();
}
