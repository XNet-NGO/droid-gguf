package ngo.xnet.droid_gguf.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ngo.xnet.droid_gguf.LlamaEngine

data class ModelConfig(
    val temperature: Float = 0.8f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val maxTokens: Int = 512,
    val contextSize: Int = 4096,
    val nThreads: Int = 6,
    val systemPrompt: String = "/no_think\nYou are a helpful assistant. Respond concisely.",
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("droid_gguf", Context.MODE_PRIVATE)

    val cpuEngine = LlamaEngine()
    val modelBEngine = LlamaEngine()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    var cpuModelName: String = ""
        private set
    var modelBModelName: String = ""
        private set

    var cpuModelPath: String? = null
        private set
    var modelBModelPath: String? = null
        private set

    var cpuConfig = MutableStateFlow(ModelConfig())
    var modelBConfig = MutableStateFlow(ModelConfig())

    private val _cpuLoaded = MutableStateFlow(false)
    val cpuLoaded: StateFlow<Boolean> = _cpuLoaded.asStateFlow()
    private val _modelBLoaded = MutableStateFlow(false)
    val modelBLoaded: StateFlow<Boolean> = _modelBLoaded.asStateFlow()

    @Volatile
    private var stopRequested = false
    private var loopJob: Job? = null

    init {
        restoreState()
    }

    private fun restoreState() {
        val cpu = prefs.getString("cpu_model_path", null)
        val modelB = prefs.getString("gpu_model_path", null)
        cpuConfig.value = ModelConfig(
            temperature = prefs.getFloat("cpu_temp", 0.8f),
            topP = prefs.getFloat("cpu_topp", 0.95f),
            topK = prefs.getInt("cpu_topk", 40),
            maxTokens = prefs.getInt("cpu_max_tokens", 512),
            contextSize = prefs.getInt("cpu_context", 4096),
            nThreads = prefs.getInt("cpu_threads", 6),
            systemPrompt = prefs.getString("cpu_system_prompt", "/no_think\nYou are a helpful assistant. Respond concisely.") ?: "",
        )
        modelBConfig.value = ModelConfig(
            temperature = prefs.getFloat("gpu_temp", 0.8f),
            topP = prefs.getFloat("gpu_topp", 0.95f),
            topK = prefs.getInt("gpu_topk", 40),
            maxTokens = prefs.getInt("gpu_max_tokens", 512),
            contextSize = prefs.getInt("gpu_context", 4096),
            nThreads = prefs.getInt("gpu_threads", 6),
            systemPrompt = prefs.getString("gpu_system_prompt", "/no_think\nYou are a helpful assistant. Respond concisely.") ?: "",
        )
        if (cpu != null && java.io.File(cpu).exists()) loadCpuModel(cpu)
        if (modelB != null && java.io.File(modelB).exists()) loadModelB(modelB)
    }

    fun saveState() {
        prefs.edit()
            .putString("cpu_model_path", cpuModelPath)
            .putString("gpu_model_path", modelBModelPath)
            .putFloat("cpu_temp", cpuConfig.value.temperature)
            .putFloat("cpu_topp", cpuConfig.value.topP)
            .putInt("cpu_topk", cpuConfig.value.topK)
            .putInt("cpu_max_tokens", cpuConfig.value.maxTokens)
            .putInt("cpu_context", cpuConfig.value.contextSize)
            .putInt("cpu_threads", cpuConfig.value.nThreads)
            .putString("cpu_system_prompt", cpuConfig.value.systemPrompt)
            .putFloat("gpu_temp", modelBConfig.value.temperature)
            .putFloat("gpu_topp", modelBConfig.value.topP)
            .putInt("gpu_topk", modelBConfig.value.topK)
            .putInt("gpu_max_tokens", modelBConfig.value.maxTokens)
            .putInt("gpu_context", modelBConfig.value.contextSize)
            .putInt("gpu_threads", modelBConfig.value.nThreads)
            .putString("gpu_system_prompt", modelBConfig.value.systemPrompt)
            .apply()
    }

    fun loadCpuModel(path: String) {
        cpuModelPath = path
        cpuModelName = path.substringAfterLast("/").removeSuffix(".gguf")
        _cpuLoaded.value = false
        viewModelScope.launch(Dispatchers.IO) {
            // Stop any running generation first
            cpuEngine.abort()
            delay(100) // Let generation thread exit
            cpuEngine.unload()
            val success = cpuEngine.loadModel(path, contextSize = cpuConfig.value.contextSize, nThreads = cpuConfig.value.nThreads)
            _cpuLoaded.value = success
            saveState()
        }
    }

    fun loadModelB(path: String) {
        modelBModelPath = path
        modelBModelName = path.substringAfterLast("/").removeSuffix(".gguf")
        _modelBLoaded.value = false
        viewModelScope.launch(Dispatchers.IO) {
            // Stop any running generation first
            modelBEngine.abort()
            delay(100) // Let generation thread exit
            modelBEngine.unload()
            val success = modelBEngine.loadModel(path, contextSize = modelBConfig.value.contextSize, nThreads = modelBConfig.value.nThreads)
            _modelBLoaded.value = success
            saveState()
        }
    }

    /**
     * Start the alternating generation loop:
     * User prompt → Model A response → Model B response → Model A → ... until stopped.
     */
    fun startLoop(userPrompt: String) {
        if (_isGenerating.value) return

        stopRequested = false
        _isGenerating.value = true

        // Add user message
        addMessage(ChatMessage(role = MessageRole.USER, content = userPrompt))

        loopJob = viewModelScope.launch(Dispatchers.IO) {
            var currentPrompt = userPrompt
            var nextRole = MessageRole.CPU

            while (!stopRequested && isActive) {
                val engine = when (nextRole) {
                    MessageRole.CPU -> cpuEngine
                    MessageRole.MODEL_B -> modelBEngine
                    else -> break
                }

                // Skip if this engine isn't loaded
                if (!engine.isLoaded) {
                    nextRole = when (nextRole) {
                        MessageRole.CPU -> MessageRole.MODEL_B
                        MessageRole.MODEL_B -> MessageRole.CPU
                        else -> break
                    }
                    // If neither model is loaded, stop
                    val otherEngine = when (nextRole) {
                        MessageRole.CPU -> cpuEngine
                        MessageRole.MODEL_B -> modelBEngine
                        else -> break
                    }
                    if (!otherEngine.isLoaded) break
                    continue
                }

                val config = when (nextRole) {
                    MessageRole.CPU -> cpuConfig.value
                    MessageRole.MODEL_B -> modelBConfig.value
                    else -> ModelConfig()
                }
                val formattedPrompt = buildChatPrompt(
                    trimToContext(currentPrompt, config.contextSize),
                    nextRole
                )

                // Add an empty message that we'll stream tokens into
                val msgIndex = _messages.value.size
                withContext(Dispatchers.Main) {
                    addMessage(ChatMessage(role = nextRole, content = ""))
                }

                var inThinking = false
                val responseBuilder = StringBuilder()
                var measuredTps: Float? = null
                val success = try {
                    engine.generate(
                        prompt = formattedPrompt,
                        maxTokens = config.maxTokens,
                        temperature = config.temperature,
                        topP = config.topP,
                        callback = object : LlamaEngine.StreamCallback {
                            override fun onToken(token: String): Boolean {
                                // Filter out thinking blocks
                                if (token.contains("<think>")) { inThinking = true; return !stopRequested }
                                if (token.contains("</think>")) { inThinking = false; return !stopRequested }
                                if (inThinking) return !stopRequested

                                responseBuilder.append(token)
                                val updated = _messages.value.toMutableList()
                                if (msgIndex < updated.size) {
                                    updated[msgIndex] = updated[msgIndex].copy(content = responseBuilder.toString().trim())
                                    _messages.value = updated
                                }
                                return !stopRequested
                            }
                            override fun onComplete() {}
                            override fun onMetrics(tokensPerSec: Float, totalTokens: Int, elapsedMs: Long) {
                                measuredTps = tokensPerSec
                                // Update message with final metrics
                                val updated = _messages.value.toMutableList()
                                if (msgIndex < updated.size) {
                                    updated[msgIndex] = updated[msgIndex].copy(tokensPerSec = tokensPerSec)
                                    _messages.value = updated
                                }
                            }
                            override fun onError(error: String) {
                                responseBuilder.append("\n[Error: $error]")
                            }
                        }
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val updated = _messages.value.toMutableList()
                        if (msgIndex < updated.size) {
                            updated[msgIndex] = updated[msgIndex].copy(content = "[Error: ${e.message}]")
                            _messages.value = updated
                        }
                    }
                    break
                }

                if (stopRequested) break

                val response = responseBuilder.toString().trim()
                if (response.isEmpty()) {
                    // All tokens were filtered (think blocks or invalid UTF-8)
                    // Remove the empty bubble and continue with previous prompt
                    val updated = _messages.value.toMutableList()
                    if (msgIndex < updated.size) {
                        updated.removeAt(msgIndex)
                        _messages.value = updated
                    }
                    break
                }

                currentPrompt = response

                // Alternate between Model A and Model B
                nextRole = when (nextRole) {
                    MessageRole.CPU -> MessageRole.MODEL_B
                    MessageRole.MODEL_B -> MessageRole.CPU
                    else -> break
                }
            }

            withContext(Dispatchers.Main) {
                _isGenerating.value = false
            }
        }
    }

    fun stopLoop() {
        stopRequested = true
        cpuEngine.abort()
        modelBEngine.abort()
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    fun clearMessages() {
        stopLoop()
        _messages.value = emptyList()
    }

    /** Trim prompt to fit within context window, keeping newest content. */
    private fun trimToContext(prompt: String, contextSize: Int): String {
        val maxChars = (contextSize * 3)
        return if (prompt.length > maxChars) {
            prompt.takeLast(maxChars)
        } else {
            prompt
        }
    }

    /** Wrap prompt in ChatML format */
    private fun buildChatPrompt(prompt: String, respondingAs: MessageRole): String {
        val config = when (respondingAs) {
            MessageRole.CPU -> cpuConfig.value
            MessageRole.MODEL_B -> modelBConfig.value
            else -> cpuConfig.value
        }
        val sys = config.systemPrompt
        return if (sys.isNotBlank()) {
            "<|im_start|>system\n$sys<|im_end|>\n<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        } else {
            "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLoop()
        loopJob?.cancel()
        cpuEngine.close()
        modelBEngine.close()
    }
}
