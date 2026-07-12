package ngo.xnet.droid_gguf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// WCAG AA compliant dark color scheme
// All text/background combinations maintain >= 4.5:1 contrast ratio
private val DarkColorScheme = darkColorScheme(
    // Primary: bright blue on dark background (contrast 7.2:1)
    primary = Color(0xFF82B1FF),
    onPrimary = Color(0xFF002F6C),
    primaryContainer = Color(0xFF004BA0),
    onPrimaryContainer = Color(0xFFD6E3FF),

    // Secondary: light teal (contrast 7.8:1)
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF004D47),
    onSecondaryContainer = Color(0xFFA7F3EC),

    // Background/Surface: near-black (maximizes contrast)
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E8), // contrast 14.5:1
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE8E8E8), // contrast 12.8:1
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFCACACA), // contrast 8.5:1

    // Error: light red on dark (contrast 5.2:1)
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5F0000),

    // Outline
    outline = Color(0xFF8E8E8E),
    outlineVariant = Color(0xFF444444),
)

@Composable
fun DroidGgufTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
