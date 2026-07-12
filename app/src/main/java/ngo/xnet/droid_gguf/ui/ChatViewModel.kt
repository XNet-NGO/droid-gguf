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

    @Volatile
    private var stopRequested = false
    private var loopJob: Job? = null

    fun loadCpuModel(path: String) {
        cpuModelName = path.substringAfterLast("/").removeSuffix(".gguf")
        viewModelScope.launch(Dispatchers.IO) {
            cpuEngine.loadModel(path, contextSize = 2048, nThreads = 4, useGpu = false)
        }
    }

    fun loadGpuModel(path: String) {
        gpuModelName = path.substringAfterLast("/").removeSuffix(".gguf")
        viewModelScope.launch(Dispatchers.IO) {
            gpuEngine.loadModel(path, contextSize = 2048, nThreads = 4, useGpu = true)
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

                val responseBuilder = StringBuilder()
                val success = try {
                    engine.generate(
                        prompt = currentPrompt,
                        maxTokens = 512,
                        temperature = 0.8f,
                        topP = 0.95f,
                        callback = object : LlamaEngine.StreamCallback {
                            override fun onToken(token: String): Boolean {
                                responseBuilder.append(token)
                                return !stopRequested
                            }
                            override fun onComplete() {}
                            override fun onError(error: String) {}
                        }
                    )
                } catch (e: Exception) {
                    addMessage(ChatMessage(role = nextRole, content = "[Error: ${e.message}]"))
                    break
                }

                if (stopRequested) break

                val response = responseBuilder.toString()
                if (response.isNotEmpty()) {
                    addMessage(ChatMessage(role = nextRole, content = response))
                    currentPrompt = response
                } else {
                    break
                }

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

    override fun onCleared() {
        super.onCleared()
        stopLoop()
        loopJob?.cancel()
        cpuEngine.close()
        gpuEngine.close()
    }
}
