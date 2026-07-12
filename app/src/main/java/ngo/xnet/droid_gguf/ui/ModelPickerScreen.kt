package ngo.xnet.droid_gguf.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ModelLoadState { EMPTY, LOADING, READY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    onModelsSelected: (cpuModelPath: String, gpuModelPath: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cpuState by remember { mutableStateOf(ModelLoadState.EMPTY) }
    var gpuState by remember { mutableStateOf(ModelLoadState.EMPTY) }
    var cpuModelPath by remember { mutableStateOf<String?>(null) }
    var gpuModelPath by remember { mutableStateOf<String?>(null) }
    var cpuModelName by remember { mutableStateOf<String?>(null) }
    var gpuModelName by remember { mutableStateOf<String?>(null) }
    var cpuModelSize by remember { mutableStateOf<String?>(null) }
    var gpuModelSize by remember { mutableStateOf<String?>(null) }

    fun importModel(uri: Uri, onDone: (path: String, name: String, size: String) -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val filename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                } ?: uri.lastPathSegment?.substringAfterLast("/") ?: "model.gguf"

                val modelsDir = File(context.filesDir, "models")
                modelsDir.mkdirs()
                val dest = File(modelsDir, filename)

                if (!dest.exists()) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output, 8192 * 1024) }
                    }
                }

                val size = formatFileSize(dest.length())
                withContext(Dispatchers.Main) {
                    onDone(dest.absolutePath, filename.removeSuffix(".gguf"), size)
                }
            }
        }
    }

    val cpuPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            cpuState = ModelLoadState.LOADING
            importModel(it) { path, name, size ->
                cpuModelPath = path
                cpuModelName = name
                cpuModelSize = size
                cpuState = ModelLoadState.READY
            }
        }
    }

    val gpuPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            gpuState = ModelLoadState.LOADING
            importModel(it) { path, name, size ->
                gpuModelPath = path
                gpuModelName = name
                gpuModelSize = size
                gpuState = ModelLoadState.READY
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("droid-gguf", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Load a model for each inference path",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ModelSlotCard(
                label = "CPU",
                subtitle = "ARM NEON • 8 threads",
                state = cpuState,
                modelName = cpuModelName,
                modelSize = cpuModelSize,
                onClick = { cpuPicker.launch(arrayOf("*/*")) }
            )

            ModelSlotCard(
                label = "GPU",
                subtitle = "OpenCL • PowerVR",
                state = gpuState,
                modelName = gpuModelName,
                modelSize = gpuModelSize,
                onClick = { gpuPicker.launch(arrayOf("*/*")) }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onModelsSelected(cpuModelPath!!, gpuModelPath!!) },
                enabled = cpuState == ModelLoadState.READY && gpuState == ModelLoadState.READY,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Chat Loop", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ModelSlotCard(
    label: String,
    subtitle: String,
    state: ModelLoadState,
    modelName: String?,
    modelSize: String?,
    onClick: () -> Unit
) {
    val containerColor = when (state) {
        ModelLoadState.EMPTY -> MaterialTheme.colorScheme.surfaceVariant
        ModelLoadState.LOADING -> MaterialTheme.colorScheme.surfaceVariant
        ModelLoadState.READY -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state != ModelLoadState.LOADING) { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (state) {
                ModelLoadState.EMPTY -> {
                    Text(
                        "Tap to select .gguf file",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ModelLoadState.LOADING -> {
                    ShimmerBlock()
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBlock(widthFraction = 0.4f)
                }
                ModelLoadState.READY -> {
                    Text(
                        modelName ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modelSize ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            "✓ Ready",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerBlock(widthFraction: Float = 0.7f) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.3f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "shimmer_translate"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200f, 0f),
        end = Offset(translateAnim.value, 0f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(brush)
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.0f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
