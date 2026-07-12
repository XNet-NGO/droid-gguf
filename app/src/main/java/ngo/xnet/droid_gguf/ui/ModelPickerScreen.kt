package ngo.xnet.droid_gguf.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import kotlin.math.roundToInt

enum class ModelLoadState { EMPTY, LOADING, READY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    viewModel: ChatViewModel,
    onModelsSelected: (cpuModelPath: String, gpuModelPath: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cpuState by remember { mutableStateOf(if (viewModel.cpuModelPath != null) ModelLoadState.READY else ModelLoadState.EMPTY) }
    var gpuState by remember { mutableStateOf(if (viewModel.gpuModelPath != null) ModelLoadState.READY else ModelLoadState.EMPTY) }
    var cpuModelPath by remember { mutableStateOf(viewModel.cpuModelPath) }
    var gpuModelPath by remember { mutableStateOf(viewModel.gpuModelPath) }
    var cpuModelName by remember { mutableStateOf(viewModel.cpuModelName.takeIf { it.isNotEmpty() }) }
    var gpuModelName by remember { mutableStateOf(viewModel.gpuModelName.takeIf { it.isNotEmpty() }) }
    var cpuModelSize by remember { mutableStateOf<String?>(viewModel.cpuModelPath?.let { formatFileSize(java.io.File(it).length()) }) }
    var gpuModelSize by remember { mutableStateOf<String?>(viewModel.gpuModelPath?.let { formatFileSize(java.io.File(it).length()) }) }

    val cpuConfig by viewModel.cpuConfig.collectAsState()
    val gpuConfig by viewModel.gpuConfig.collectAsState()

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
                viewModel.loadCpuModel(path)
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
                viewModel.loadGpuModel(path)
                gpuState = ModelLoadState.READY
            }
        }
    }

    val bothReady = cpuState == ModelLoadState.READY && gpuState == ModelLoadState.READY

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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

            // Config section - shown after both models are loaded
            if (bothReady) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Generation Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // CPU Config Section
                ConfigSection(
                    title = "CPU Model Settings",
                    config = cpuConfig,
                    showThreads = true,
                    onConfigChanged = { viewModel.cpuConfig.value = it; viewModel.saveState() }
                )

                // GPU Config Section
                ConfigSection(
                    title = "GPU Model Settings",
                    config = gpuConfig,
                    showThreads = false,
                    onConfigChanged = { viewModel.gpuConfig.value = it; viewModel.saveState() }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onModelsSelected(cpuModelPath!!, gpuModelPath!!) },
                enabled = bothReady,
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
private fun ConfigSection(
    title: String,
    config: ModelConfig,
    showThreads: Boolean,
    onConfigChanged: (ModelConfig) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header - clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Temperature slider (0.0 to 2.0, step 0.1)
                    LabeledSlider(
                        label = "Temperature",
                        value = config.temperature,
                        valueRange = 0f..2f,
                        steps = 19, // (2.0 - 0.0) / 0.1 - 1 = 19 steps between
                        valueDisplay = { String.format("%.1f", it) },
                        onValueChange = { onConfigChanged(config.copy(temperature = roundToStep(it, 0.1f))) }
                    )

                    // Top-P slider (0.0 to 1.0, step 0.05)
                    LabeledSlider(
                        label = "Top-P",
                        value = config.topP,
                        valueRange = 0f..1f,
                        steps = 19, // (1.0 - 0.0) / 0.05 - 1 = 19 steps between
                        valueDisplay = { String.format("%.2f", it) },
                        onValueChange = { onConfigChanged(config.copy(topP = roundToStep(it, 0.05f))) }
                    )

                    // Top-K slider (1 to 100, step 1)
                    LabeledSlider(
                        label = "Top-K",
                        value = config.topK.toFloat(),
                        valueRange = 1f..100f,
                        steps = 98, // (100 - 1) / 1 - 1 = 98 steps between
                        valueDisplay = { it.roundToInt().toString() },
                        onValueChange = { onConfigChanged(config.copy(topK = it.roundToInt())) }
                    )

                    // Max Tokens slider (64 to 2048, step 64)
                    LabeledSlider(
                        label = "Max Tokens",
                        value = config.maxTokens.toFloat(),
                        valueRange = 64f..2048f,
                        steps = 30, // (2048 - 64) / 64 - 1 = 30 steps between
                        valueDisplay = { it.roundToInt().toString() },
                        onValueChange = { onConfigChanged(config.copy(maxTokens = roundToIntStep(it, 64))) }
                    )

                    // Context Size dropdown (1024, 2048, 4096, 8192)
                    ContextSizeSelector(
                        currentSize = config.contextSize,
                        onSizeSelected = { onConfigChanged(config.copy(contextSize = it)) }
                    )

                    // Threads slider (1 to 8, step 1) - CPU only
                    if (showThreads) {
                        LabeledSlider(
                            label = "Threads",
                            value = config.nThreads.toFloat(),
                            valueRange = 1f..8f,
                            steps = 6, // (8 - 1) / 1 - 1 = 6 steps between
                            valueDisplay = { it.roundToInt().toString() },
                            onValueChange = { onConfigChanged(config.copy(nThreads = it.roundToInt())) }
                        )
                    }

                    // System Prompt
                    Text("System Prompt", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = config.systemPrompt,
                        onValueChange = { onConfigChanged(config.copy(systemPrompt = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueDisplay: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                valueDisplay(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextSizeSelector(
    currentSize: Int,
    onSizeSelected: (Int) -> Unit
) {
    val options = listOf(1024, 2048, 4096, 8192)
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Context Size",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = "$currentSize tokens",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { size ->
                    DropdownMenuItem(
                        text = { Text("$size tokens") },
                        onClick = {
                            onSizeSelected(size)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

private fun roundToStep(value: Float, step: Float): Float {
    return (value / step).roundToInt() * step
}

private fun roundToIntStep(value: Float, step: Int): Int {
    return ((value / step).roundToInt() * step).coerceIn(step, Int.MAX_VALUE)
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
