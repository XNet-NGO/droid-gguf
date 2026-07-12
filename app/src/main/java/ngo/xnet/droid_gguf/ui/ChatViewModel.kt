package ngo.xnet.droid_gguf.ui

import androidx.lifecycle.ViewModel
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
    val nThreads: Int = 4,
)

class ChatViewModel : ViewModel() {

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

    var cpuConfig = MutableStateFlow(ModelConfig())
    var gpuConfig = MutableStateFlow(ModelConfig())

    @Volatile
    private var stopRequested = false
    private var loopJob: Job? = null

    fun loadCpuModel(path: String) {
        cpuModelName = path.substringAfterLast("/").removeSuffix(".gguf")
        viewModelScope.launch(Dispatchers.IO) {
            cpuEngine.loadModel(path, contextSize = cpuConfig.value.contextSize, nThreads = cpuConfig.value.nThreads, useGpu = false)
        }
    }

    fun loadGpuModel(path: String) {
        gpuModelName = path.substringAfterLast("/").removeSuffix(".gguf")
        viewModelScope.launch(Dispatchers.IO) {
            gpuEngine.loadModel(path, contextSize = gpuConfig.value.contextSize, nThreads = gpuConfig.value.nThreads, useGpu = true)
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
                                responseBuilder.append(token)
                                // Update the message in-place with new content
                                val updated = _messages.value.toMutableList()
                                if (msgIndex < updated.size) {
                                    updated[msgIndex] = updated[msgIndex].copy(content = responseBuilder.toString())
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

    /** Wrap prompt in ChatML format so the model responds conversationally */
    private fun buildChatPrompt(prompt: String, respondingAs: MessageRole): String {
        val sb = StringBuilder()
        // System instruction
        sb.append("<|im_start|>system\nYou are a helpful assistant. Respond concisely.<|im_end|>\n")
        // The prompt as user message
        sb.append("<|im_start|>user\n$prompt<|im_end|>\n")
        // Start assistant turn
        sb.append("<|im_start|>assistant\n")
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
