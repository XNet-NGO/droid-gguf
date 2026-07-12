package ngo.xnet.droid_gguf.ui

enum class MessageRole { USER, CPU, GPU }

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
