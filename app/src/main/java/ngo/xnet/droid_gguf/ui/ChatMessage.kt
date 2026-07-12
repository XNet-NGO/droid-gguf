package ngo.xnet.droid_gguf.ui

enum class MessageRole { USER, CPU, MODEL_B }

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
