package ngo.xnet.droid_gguf.ui

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

data class GgufFile(
    val file: File,
    val name: String,
    val sizeLabel: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    onModelsSelected: (cpuModelPath: String, gpuModelPath: String) -> Unit
) {
    val context = LocalContext.current
    var cpuModelPath by remember { mutableStateOf<String?>(null) }
    var gpuModelPath by remember { mutableStateOf<String?>(null) }
    var selectingFor by remember { mutableStateOf<SelectingFor>(SelectingFor.CPU) }

    val ggufFiles = remember {
        findGgufFiles(context.filesDir)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Models") },
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
        ) {
            // Selection status
            SelectionStatus(
                label = "CPU Model",
                path = cpuModelPath,
                isActive = selectingFor == SelectingFor.CPU,
                onClick = { selectingFor = SelectingFor.CPU }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SelectionStatus(
                label = "GPU Model",
                path = gpuModelPath,
                isActive = selectingFor == SelectingFor.GPU,
                onClick = { selectingFor = SelectingFor.GPU }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select for: ${selectingFor.name}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // File list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(ggufFiles) { gguf ->
                    GgufFileItem(
                        gguf = gguf,
                        isSelected = when (selectingFor) {
                            SelectingFor.CPU -> gguf.file.absolutePath == cpuModelPath
                            SelectingFor.GPU -> gguf.file.absolutePath == gpuModelPath
                        },
                        onClick = {
                            when (selectingFor) {
                                SelectingFor.CPU -> {
                                    cpuModelPath = gguf.file.absolutePath
                                    // Auto-advance to GPU selection
                                    if (gpuModelPath == null) selectingFor = SelectingFor.GPU
                                }
                                SelectingFor.GPU -> {
                                    gpuModelPath = gguf.file.absolutePath
                                }
                            }
                        }
                    )
                }

                if (ggufFiles.isEmpty()) {
                    item {
                        Text(
                            text = "No .gguf files found.\nPlace models in /sdcard/Download/ or app storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start button
            Button(
                onClick = {
                    onModelsSelected(cpuModelPath!!, gpuModelPath!!)
                },
                enabled = cpuModelPath != null && gpuModelPath != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Chat", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SelectionStatus(
    label: String,
    path: String?,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.3f)
            )
            Text(
                text = path?.substringAfterLast("/") ?: "Not selected",
                style = MaterialTheme.typography.bodySmall,
                color = if (path != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.7f)
            )
        }
    }
}

@Composable
private fun GgufFileItem(
    gguf: GgufFile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = gguf.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = gguf.sizeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class SelectingFor { CPU, GPU }

private fun findGgufFiles(appFilesDir: File): List<GgufFile> {
    val dirs = mutableListOf<File>()

    // /sdcard/Download
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (downloadDir.exists()) dirs.add(downloadDir)

    // App internal storage
    if (appFilesDir.exists()) dirs.add(appFilesDir)

    return dirs.flatMap { dir ->
        dir.listFiles { file -> file.extension == "gguf" }?.toList() ?: emptyList()
    }.distinctBy { it.absolutePath }
        .sortedBy { it.name }
        .map { file ->
            GgufFile(
                file = file,
                name = file.name,
                sizeLabel = formatFileSize(file.length())
            )
        }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
