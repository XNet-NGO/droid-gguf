#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"
#include "ggml.h"
#include "ggml-backend.h"

#define TAG "droid-gguf"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Per-instance state held on the native side, pointer stored in Kotlin as a Long (handle).
struct EngineState {
    llama_model   *model   = nullptr;
    llama_context *ctx     = nullptr;
    llama_sampler *sampler = nullptr;
    std::atomic<bool> abort_flag{false};
    std::mutex    mtx; // protects load/unload
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

static EngineState *getState(jlong handle) {
    return reinterpret_cast<EngineState *>(handle);
}

static std::string jstringToStd(JNIEnv *env, jstring js) {
    if (!js) return {};
    const char *utf = env->GetStringUTFChars(js, nullptr);
    std::string s(utf);
    env->ReleaseStringUTFChars(js, utf);
    return s;
}

// Convert a single token to text. Returns empty string for special/control tokens.
static std::string tokenToString(const llama_vocab *vocab, llama_token token) {
    char buf[256];
    int32_t n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);
    if (n < 0) {
        // buffer too small, allocate
        std::vector<char> big(-n + 1);
        n = llama_token_to_piece(vocab, token, big.data(), big.size(), 0, false);
        if (n > 0) return std::string(big.data(), n);
        return {};
    }
    return std::string(buf, n);
}

// ---------------------------------------------------------------------------
// JNI: nativeCreate / nativeDestroy  (lifecycle of the handle)
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jlong JNICALL
Java_ngo_xnet_droid_1gguf_LlamaEngine_nativeCreate(JNIEnv *, jobject) {
    auto *state = new EngineState();
    return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT void JNICALL
Java_ngo_xnet_droid_1gguf_LlamaEngine_nativeDestroy(JNIEnv *, jobject, jlong handle) {
    auto *state = getState(handle);
    if (!state) return;
    // Ensure model is freed
    std::lock_guard<std::mutex> lock(state->mtx);
    if (state->sampler) { llama_sampler_free(state->sampler); state->sampler = nullptr; }
    if (state->ctx)     { llama_free(state->ctx);             state->ctx     = nullptr; }
    if (state->model)   { llama_model_free(state->model);     state->model   = nullptr; }
    delete state;
}

// ---------------------------------------------------------------------------
// JNI: loadModel
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_ngo_xnet_droid_1gguf_LlamaEngine_nativeLoadModel(
        JNIEnv *env, jobject, jlong handle,
        jstring jPath, jint contextSize, jint nThreads) {

    auto *state = getState(handle);
    if (!state) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(state->mtx);

    // If already loaded, unload first
    if (state->sampler) { llama_sampler_free(state->sampler); state->sampler = nullptr; }
    if (state->ctx)     { llama_free(state->ctx);             state->ctx     = nullptr; }
    if (state->model)   { llama_model_free(state->model);     state->model   = nullptr; }

    state->abort_flag.store(false);

    std::string path = jstringToStd(env, jPath);

    // --- Model params (CPU only) ---
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    LOGI("Loading model: %s (ctx=%d, threads=%d)", path.c_str(), contextSize, nThreads);

    state->model = llama_model_load_from_file(path.c_str(), model_params);
    if (!state->model) {
        LOGE("Failed to load model: %s", path.c_str());
        return JNI_FALSE;
    }

    // --- Context params ---
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx     = (contextSize > 0) ? static_cast<uint32_t>(contextSize) : 2048;
    ctx_params.n_threads = (nThreads > 0) ? nThreads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;
    ctx_params.n_batch   = 512;

    state->ctx = llama_init_from_model(state->model, ctx_params);
    if (!state->ctx) {
        LOGE("Failed to create context");
        llama_model_free(state->model);
        state->model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

// ---------------------------------------------------------------------------
// JNI: generate (streaming with callback)
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_ngo_xnet_droid_1gguf_LlamaEngine_nativeGenerate(
        JNIEnv *env, jobject, jlong handle,
        jstring jPrompt, jint maxTokens, jfloat temperature, jfloat topP,
        jobject callback) {

    auto *state = getState(handle);
    if (!state || !state->model || !state->ctx) {
        LOGE("generate: engine not loaded");
        return JNI_FALSE;
    }

    state->abort_flag.store(false);

    // Clear KV cache — each turn gets fresh context
    llama_memory_clear(llama_get_memory(state->ctx), true);

    // Look up callback methods
    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken    = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)Z");
    jmethodID onComplete = env->GetMethodID(cbClass, "onComplete", "()V");
    jmethodID onError    = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");

    if (!onToken || !onComplete || !onError) {
        LOGE("generate: cannot find callback methods");
        return JNI_FALSE;
    }

    std::string prompt = jstringToStd(env, jPrompt);
    const llama_vocab *vocab = llama_model_get_vocab(state->model);

    // Tokenize prompt
    int n_prompt_max = prompt.size() + 128;
    std::vector<llama_token> tokens(n_prompt_max);
    int n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.size(),
                                  tokens.data(), n_prompt_max, true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.size(),
                                  tokens.data(), tokens.size(), true, true);
    }
    if (n_tokens < 0) {
        env->CallVoidMethod(callback, onError, env->NewStringUTF("Tokenization failed"));
        return JNI_FALSE;
    }
    tokens.resize(n_tokens);

    // Setup sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);

    float temp = (temperature > 0.0f) ? temperature : 0.8f;
    float top_p_val = (topP > 0.0f && topP <= 1.0f) ? topP : 0.95f;

    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p_val, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // Free old sampler if any
    if (state->sampler) {
        llama_sampler_free(state->sampler);
    }
    state->sampler = smpl;

    // Evaluate prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(state->ctx, batch) != 0) {
        env->CallVoidMethod(callback, onError, env->NewStringUTF("Prompt evaluation failed"));
        return JNI_FALSE;
    }

    // Generation loop with timing
    auto gen_start = std::chrono::steady_clock::now();
    int tokens_generated = 0;
    int max = (maxTokens > 0) ? maxTokens : 512;
    for (int i = 0; i < max; i++) {
        if (state->abort_flag.load()) {
            LOGI("Generation aborted by user");
            break;
        }

        llama_token new_token = llama_sampler_sample(smpl, state->ctx, -1);

        // Check for end of generation
        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        // Convert token to string
        std::string piece = tokenToString(vocab, new_token);
        if (piece.empty()) continue;

        // Validate UTF-8 before passing to JNI (invalid bytes crash NewStringUTF)
        bool validUtf8 = true;
        for (size_t i = 0; i < piece.size(); i++) {
            unsigned char c = piece[i];
            if (c == 0) { validUtf8 = false; break; }
        }
        if (!validUtf8) continue;

        // Call onToken callback
        jstring jPiece = env->NewStringUTF(piece.c_str());
        if (!jPiece) continue;  // NewStringUTF can return null on OOM
        jboolean shouldContinue = env->CallBooleanMethod(callback, onToken, jPiece);
        env->DeleteLocalRef(jPiece);

        if (!shouldContinue) {
            LOGI("Generation stopped by callback");
            break;
        }

        tokens_generated++;

        // Prepare next batch (single token)
        llama_batch next = llama_batch_get_one(&new_token, 1);
        if (llama_decode(state->ctx, next) != 0) {
            env->CallVoidMethod(callback, onError, env->NewStringUTF("Decode failed during generation"));
            return JNI_FALSE;
        }
    }

    // Report metrics
    auto gen_end = std::chrono::steady_clock::now();
    auto elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(gen_end - gen_start).count();
    float tps = (elapsed_ms > 0) ? (tokens_generated * 1000.0f / elapsed_ms) : 0.0f;

    jmethodID onMetrics = env->GetMethodID(cbClass, "onMetrics", "(FIJ)V");
    if (onMetrics) {
        env->CallVoidMethod(callback, onMetrics, tps, (jint)tokens_generated, (jlong)elapsed_ms);
    }

    LOGI("Generated %d tokens in %lld ms (%.1f tok/s)", tokens_generated, (long long)elapsed_ms, tps);

    env->CallVoidMethod(callback, onComplete);
    return JNI_TRUE;
}

// ---------------------------------------------------------------------------
// JNI: unload
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_ngo_xnet_droid_1gguf_LlamaEngine_nativeUnload(JNIEnv *, jobject, jlong handle) {
    auto *state = getState(handle);
    if (!state) return;
    std::lock_guard<std::mutex> lock(state->mtx);

    if (state->sampler) { llama_sampler_free(state->sampler); state->sampler = nullptr; }
    if (state->ctx)     { llama_free(state->ctx);             state->ctx     = nullptr; }
    if (state->model)   { llama_model_free(state->model);     state->model   = nullptr; }

    LOGI("Model unloaded");
}

// ---------------------------------------------------------------------------
// JNI: abort
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_ngo_xnet_droid_1gguf_LlamaEngine_nativeAbort(JNIEnv *, jobject, jlong handle) {
    auto *state = getState(handle);
    if (!state) return;
    state->abort_flag.store(true);
}

// ---------------------------------------------------------------------------
// JNI: getModelInfo
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_ngo_xnet_droid_1gguf_LlamaEngine_nativeGetModelInfo(JNIEnv *env, jobject, jlong handle) {
    auto *state = getState(handle);
    if (!state || !state->model) {
        return env->NewStringUTF("{}");
    }

    char desc[256] = {};
    llama_model_desc(state->model, desc, sizeof(desc));

    uint64_t n_params = llama_model_n_params(state->model);
    uint64_t model_size = llama_model_size(state->model);
    int32_t  n_ctx_train = llama_model_n_ctx_train(state->model);
    int32_t  n_embd = llama_model_n_embd(state->model);
    int32_t  n_layer = llama_model_n_layer(state->model);

    const llama_vocab *vocab = llama_model_get_vocab(state->model);
    int32_t  n_vocab = llama_vocab_n_tokens(vocab);

    // Get general.name if available
    char name[128] = {};
    llama_model_meta_val_str(state->model, "general.name", name, sizeof(name));

    // Build JSON manually (no external dep needed)
    std::string json = "{";
    json += "\"name\":\"" + std::string(name) + "\",";
    json += "\"description\":\"" + std::string(desc) + "\",";
    json += "\"params\":" + std::to_string(n_params) + ",";
    json += "\"size\":" + std::to_string(model_size) + ",";
    json += "\"context_length\":" + std::to_string(n_ctx_train) + ",";
    json += "\"embedding_size\":" + std::to_string(n_embd) + ",";
    json += "\"layers\":" + std::to_string(n_layer) + ",";
    json += "\"vocab_size\":" + std::to_string(n_vocab);
    json += "}";

    return env->NewStringUTF(json.c_str());
}
