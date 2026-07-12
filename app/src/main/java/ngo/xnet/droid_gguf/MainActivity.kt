package ngo.xnet.droid_gguf

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ngo.xnet.droid_gguf.ui.ChatScreen
import ngo.xnet.droid_gguf.ui.ChatViewModel
import ngo.xnet.droid_gguf.ui.ModelPickerScreen
import ngo.xnet.droid_gguf.ui.theme.DroidGgufTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllFilesAccess()
        enableEdgeToEdge()
        setContent {
            DroidGgufTheme {
                DroidGgufApp()
            }
        }
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}

@Composable
fun DroidGgufApp() {
    val navController = rememberNavController()
    val viewModel: ChatViewModel = viewModel()

    NavHost(navController = navController, startDestination = "picker") {
        composable("picker") {
            ModelPickerScreen(
                viewModel = viewModel,
                onModelsSelected = { cpuPath, gpuPath ->
                    viewModel.loadCpuModel(cpuPath)
                    viewModel.loadGpuModel(gpuPath)
                    navController.navigate("chat")
                }
            )
        }
        composable("chat") {
            ChatScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
