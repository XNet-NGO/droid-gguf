package ngo.xnet.droid_gguf.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit = {}) {
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopLoop()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    val title = listOfNotNull(
                        viewModel.cpuModelName.takeIf { it.isNotEmpty() },
                        viewModel.modelBModelName.takeIf { it.isNotEmpty() }
                    ).joinToString(" | ")
                    Text(
                        text = title.ifEmpty { "droid-gguf" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.clearMessages() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(messages) { message ->
                    MessageBubble(message)
                }
                if (isGenerating) {
                    item {
                        Text(
                            text = "Generating...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // Input area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGenerating) {
                    // STOP button - prominent red
                    Button(
                        onClick = { viewModel.stopLoop() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "⏹ STOP",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enter prompt...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.startLoop(inputText.trim())
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val (roleLabel, roleColor) = when (message.role) {
        MessageRole.USER -> "User" to MaterialTheme.colorScheme.primary
        MessageRole.CPU -> "Model A" to Color(0xFF81C784)  // green, 5.5:1 on dark
        MessageRole.MODEL_B -> "Model B" to Color(0xFFFFB74D)  // amber, 6.2:1 on dark
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = roleColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = roleLabel,
                        color = roleColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (message.tokensPerSec != null) {
                    Text(
                        text = String.format("%.1f tok/s", message.tokensPerSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ThinkingShimmer(widthFraction: Float = 0.7f) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.3f),
    )
    val transition = rememberInfiniteTransition(label = "thinking")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "thinking_translate"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200f, 0f),
        end = Offset(translateAnim.value, 0f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(brush)
    )
}
