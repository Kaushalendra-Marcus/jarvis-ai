package ai.jarvis.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import ai.jarvis.app.R

private val DarkScheme = darkColorScheme(
    primary = JarvisPrimary,
    onPrimary = JarvisOnPrimary,
    primaryContainer = JarvisVisor,
    onPrimaryContainer = JarvisPrimaryMist,
    secondary = JarvisEye,
    onSecondary = JarvisVisor,
    tertiary = JarvisShadow,
    background = MidnightBg,
    onBackground = InkHigh,
    surface = MidnightStage,
    onSurface = InkHigh,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = InkMid,
    surfaceTint = JarvisPrimary,
    outline = MidnightLine,
    outlineVariant = Color(0xFF16223A),
    error = ErrorRed,
    onError = Color(0xFF2B0000)
)

private val Manrope = FontFamily(
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

private val Sora = FontFamily(
    Font(R.font.sora_light, FontWeight.Light),
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_medium, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold)
)

private val AppTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = Sora, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = Manrope),
        bodyMedium = base.bodyMedium.copy(fontFamily = Manrope),
        bodySmall = base.bodySmall.copy(fontFamily = Manrope),
        labelLarge = base.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium)
    )
}

private val LightScheme = lightColorScheme(
    primary = JarvisVisor,
    onPrimary = Color.White,
    primaryContainer = JarvisPrimaryMist,
    onPrimaryContainer = JarvisVisor,
    secondary = JarvisPrimaryDeep,
    onSecondary = Color.White,
    tertiary = JarvisShadow,
    background = PaperBg,
    onBackground = PineInk,
    surface = PaperCard,
    onSurface = PineInk,
    surfaceVariant = Color(0xFFE8EEE6),
    onSurfaceVariant = Color(0xFF52606B),
    surfaceTint = PineDeep,
    outline = Color(0xFFB9C6C2),
    outlineVariant = PaperLine,
    error = Color(0xFFDC2626),
    onError = Color.White
)

/** Midnight-glass theme. Dark-first; light stays paper-clean. */
@Composable
fun JarvisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = scheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = scheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

/** Legacy alias. */
@Composable
fun JarvisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = JarvisTheme(darkTheme, content)
