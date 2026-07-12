package ngo.xnet.droid_gguf

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JNI bridge to llama.cpp for GGUF model inference (CPU backend).
 *
 * Multiple instances can coexist for dual-model chat loops.
 *
 * Usage:
 * ```
 * val engine = LlamaEngine()
 * engine.loadModel("/path/to/model.gguf", contextSize = 2048, nThreads = 6)
 * engine.generateStream("Hello", GenerateParams()).collect { token -> print(token) }
 * engine.unload()
 * engine.close()
 * ```
 */
class LlamaEngine : AutoCloseable {

    companion object {
        init {
            System.loadLibrary("droid-gguf")
        }
    }

    /** Opaque native handle */
    private var handle: Long = nativeCreate()

    val isLoaded: Boolean
        get() = handle != 0L

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Load a GGUF model file.
     *
     * @param path Absolute path to the .gguf model file.
     * @param contextSize Context window size (0 = model default).
     * @param nThreads Number of threads for inference (0 = auto).
     * @return true on success.
     */
    fun loadModel(path: String, contextSize: Int = 2048, nThreads: Int = 6): Boolean {
        check(handle != 0L) { "Engine has been closed" }
        return nativeLoadModel(handle, path, contextSize, nThreads)
    }

    /**
     * Run streaming text generation.
     *
     * @param prompt The input prompt.
     * @param maxTokens Maximum tokens to generate.
     * @param temperature Sampling temperature.
     * @param topP Top-p (nucleus) sampling threshold.
     * @param callback Receives tokens as they are generated.
     * @return true if generation completed without fatal error.
     */
    fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.8f,
        topP: Float = 0.95f,
        callback: StreamCallback
    ): Boolean {
        check(handle != 0L) { "Engine has been closed" }
        return nativeGenerate(handle, prompt, maxTokens, temperature, topP, callback)
    }

    /** Unload the current model, freeing all native memory. */
    fun unload() {
        if (handle != 0L) {
            nativeUnload(handle)
        }
    }

    /** Abort an in-progress generation from another thread. */
    fun abort() {
        if (handle != 0L) {
            nativeAbort(handle)
        }
    }

    /** Get model metadata as a JSON string. Returns "{}" if no model is loaded. */
    fun getModelInfo(): String {
        if (handle == 0L) return "{}"
        return nativeGetModelInfo(handle)
    }

    /** Release all native resources. The engine cannot be reused after this. */
    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    // -------------------------------------------------------------------------
    // Kotlin Flow wrapper
    // -------------------------------------------------------------------------

    /** Parameters for [generateStream]. */
    data class GenerateParams(
        val maxTokens: Int = 512,
        val temperature: Float = 0.8f,
        val topP: Float = 0.95f
    )

    /**
     * Streaming generation as a cold [Flow].
     * Emits each token string as it is produced. Completes when generation ends.
     * Collecting on a cancelled coroutine will call [abort] automatically.
     */
    fun generateStream(prompt: String, params: GenerateParams = GenerateParams()): Flow<String> =
        callbackFlow {
            val job = withContext(Dispatchers.IO) {
                generate(
                    prompt = prompt,
                    maxTokens = params.maxTokens,
                    temperature = params.temperature,
                    topP = params.topP,
                    callback = object : StreamCallback {
                        override fun onToken(token: String): Boolean {
                            val result = trySend(token)
                            return result.isSuccess
                        }

                        override fun onComplete() {
                            channel.close()
                        }

                        override fun onError(error: String) {
                            channel.close(RuntimeException(error))
                        }
                    }
                )
            }
            awaitClose { abort() }
        }

    // -------------------------------------------------------------------------
    // Callback interface
    // -------------------------------------------------------------------------

    /**
     * Callback interface for streaming token generation.
     */
    interface StreamCallback {
        /**
         * Called for each generated token.
         * @param token The decoded text piece.
         * @return true to continue generation, false to stop.
         */
        fun onToken(token: String): Boolean

        /** Called when generation completes normally or is stopped. */
        fun onComplete()

        /** Called with generation metrics after completion.
         * @param tokensPerSec Output tokens per second.
         * @param totalTokens Total tokens generated.
         * @param elapsedMs Total generation time in milliseconds.
         */
        fun onMetrics(tokensPerSec: Float, totalTokens: Int, elapsedMs: Long) {}

        /** Called on fatal error during generation. */
        fun onError(error: String)
    }

    // -------------------------------------------------------------------------
    // Native methods
    // -------------------------------------------------------------------------

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeLoadModel(handle: Long, path: String, contextSize: Int, nThreads: Int): Boolean
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temperature: Float, topP: Float, callback: StreamCallback): Boolean
    private external fun nativeUnload(handle: Long)
    private external fun nativeAbort(handle: Long)
    private external fun nativeGetModelInfo(handle: Long): String
}
