package ngo.xnet.droid_gguf

import android.os.Bundle
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
        enableEdgeToEdge()
        setContent {
            DroidGgufTheme {
                DroidGgufApp()
            }
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
                onModelsSelected = { cpuPath, gpuPath ->
                    viewModel.loadCpuModel(cpuPath)
                    viewModel.loadGpuModel(gpuPath)
                    navController.navigate("chat") {
                        popUpTo("picker") { inclusive = true }
                    }
                }
            )
        }
        composable("chat") {
            ChatScreen(viewModel = viewModel)
        }
    }
}
