package ngo.xnet.droid_gguf.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("droid_gguf", Context.MODE_PRIVATE)

    val cpuEngine = LlamaEngine()
    val gpuEngine = LlamaEngine()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    var cpuModelName: String = ""
        private set
    var gpuModelName: String = ""
        private set

    var cpuModelPath: String? = null
        private set
    var gpuModelPath: String? = null
        private set

    var cpuConfig = MutableStateFlow(ModelConfig())
    var gpuConfig = MutableStateFlow(ModelConfig())

    private val _cpuLoaded = MutableStateFlow(false)
    val cpuLoaded: StateFlow<Boolean> = _cpuLoaded.asStateFlow()
    private val _gpuLoaded = MutableStateFlow(false)
    val gpuLoaded: StateFlow<Boolean> = _gpuLoaded.asStateFlow()

    @Volatile
    private var stopRequested = false
    private var loopJob: Job? = null

    init {
        restoreState()
    }

    private fun restoreState() {
        val cpu = prefs.getString("cpu_model_path", null)
        val gpu = prefs.getString("gpu_model_path", null)
        cpuConfig.value = ModelConfig(
            temperature = prefs.getFloat("cpu_temp", 0.8f),
            topP = prefs.getFloat("cpu_topp", 0.95f),
            topK = prefs.getInt("cpu_topk", 40),
            maxTokens = prefs.getInt("cpu_max_tokens", 512),
            contextSize = prefs.getInt("cpu_context", 4096),
            nThreads = prefs.getInt("cpu_threads", 4),
        )
        gpuConfig.value = ModelConfig(
            temperature = prefs.getFloat("gpu_temp", 0.8f),
            topP = prefs.getFloat("gpu_topp", 0.95f),
            topK = prefs.getInt("gpu_topk", 40),
            maxTokens = prefs.getInt("gpu_max_tokens", 512),
            contextSize = prefs.getInt("gpu_context", 4096),
            nThreads = prefs.getInt("gpu_threads", 4),
        )
        if (cpu != null && java.io.File(cpu).exists()) loadCpuModel(cpu)
        if (gpu != null && java.io.File(gpu).exists()) loadGpuModel(gpu)
    }

    fun saveState() {
        prefs.edit()
            .putString("cpu_model_path", cpuModelPath)
            .putString("gpu_model_path", gpuModelPath)
            .putFloat("cpu_temp", cpuConfig.value.temperature)
            .putFloat("cpu_topp", cpuConfig.value.topP)
            .putInt("cpu_topk", cpuConfig.value.topK)
            .putInt("cpu_max_tokens", cpuConfig.value.maxTokens)
            .putInt("cpu_context", cpuConfig.value.contextSize)
            .putInt("cpu_threads", cpuConfig.value.nThreads)
            .putFloat("gpu_temp", gpuConfig.value.temperature)
            .putFloat("gpu_topp", gpuConfig.value.topP)
            .putInt("gpu_topk", gpuConfig.value.topK)
            .putInt("gpu_max_tokens", gpuConfig.value.maxTokens)
            .putInt("gpu_context", gpuConfig.value.contextSize)
            .putInt("gpu_threads", gpuConfig.value.nThreads)
            .apply()
    }

    fun loadCpuModel(path: String) {
        cpuModelPath = path
        cpuModelName = path.substringAfterLast("/").removeSuffix(".gguf")
        _cpuLoaded.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val success = cpuEngine.loadModel(path, contextSize = cpuConfig.value.contextSize, nThreads = cpuConfig.value.nThreads, useGpu = false)
            _cpuLoaded.value = success
            saveState()
        }
    }

    fun loadGpuModel(path: String) {
        gpuModelPath = path
        gpuModelName = path.substringAfterLast("/").removeSuffix(".gguf")
        _gpuLoaded.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val success = gpuEngine.loadModel(path, contextSize = gpuConfig.value.contextSize, nThreads = gpuConfig.value.nThreads, useGpu = true)
            _gpuLoaded.value = success
            saveState()
        }
    }

    /**
     * Start the alternating generation loop:
     * User prompt → CPU response → GPU response → CPU response → ... until stopped.
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
                    MessageRole.GPU -> gpuEngine
                    else -> break
                }

                // Wrap in ChatML format so the model responds to the prompt
                val formattedPrompt = buildChatPrompt(currentPrompt, nextRole)

                val config = when (nextRole) {
                    MessageRole.CPU -> cpuConfig.value
                    MessageRole.GPU -> gpuConfig.value
                    else -> ModelConfig()
                }

                // Add an empty message that we'll stream tokens into
                val msgIndex = _messages.value.size
                withContext(Dispatchers.Main) {
                    addMessage(ChatMessage(role = nextRole, content = ""))
                }

                val responseBuilder = StringBuilder()
                val success = try {
                    engine.generate(
                        prompt = formattedPrompt,
                        maxTokens = config.maxTokens,
                        temperature = config.temperature,
                        topP = config.topP,
                        callback = object : LlamaEngine.StreamCallback {
                            override fun onToken(token: String): Boolean {
                                // Stop on end-of-turn tokens
                                if (token.contains("<|im_end|>") || token.contains("<|endoftext|>") || token.contains("<|im_start|>")) {
                                    return false
                                }
                                responseBuilder.append(token)
                                // Update the message in-place with new content
                                val updated = _messages.value.toMutableList()
                                if (msgIndex < updated.size) {
                                    updated[msgIndex] = updated[msgIndex].copy(content = responseBuilder.toString().trim())
                                    _messages.value = updated
                                }
                                return !stopRequested
                            }
                            override fun onComplete() {}
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

                val response = responseBuilder.toString()
                if (response.isEmpty()) break

                currentPrompt = response

                // Alternate between CPU and GPU
                nextRole = when (nextRole) {
                    MessageRole.CPU -> MessageRole.GPU
                    MessageRole.GPU -> MessageRole.CPU
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
        gpuEngine.abort()
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    fun clearMessages() {
        stopLoop()
        _messages.value = emptyList()
    }

    /** Wrap prompt in ChatML format so the model responds conversationally */
    private fun buildChatPrompt(prompt: String, respondingAs: MessageRole): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\nYou are a helpful assistant. Respond concisely.<|im_end|>\n")

        if (respondingAs == MessageRole.CPU) {
            // CPU is the assistant, prompt comes from user (or GPU acting as user)
            sb.append("<|im_start|>user\n$prompt<|im_end|>\n")
            sb.append("<|im_start|>assistant\n")
        } else {
            // GPU is the user, prompt comes from CPU (assistant's previous response)
            sb.append("<|im_start|>assistant\n$prompt<|im_end|>\n")
            sb.append("<|im_start|>user\n")
        }
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        stopLoop()
        loopJob?.cancel()
        cpuEngine.close()
        gpuEngine.close()
    }
}
