package ai.lumo.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ai.lumo.app.R
import ai.lumo.app.presentation.theme.JarvisEye
import ai.lumo.app.presentation.theme.JarvisPrimary
import ai.lumo.app.presentation.theme.JarvisShadow
import ai.lumo.app.presentation.theme.JarvisSkyBottom
import ai.lumo.app.presentation.theme.JarvisSkyTop
import ai.lumo.app.presentation.theme.JarvisSpeck
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Jarvis opening scene — ClayRobot studio cyclorama + FoldingParticleShell speck field.
 *
 * Palette is the ClayRobot defaults (single source of truth in Color.kt):
 * sky #909EA9 → #CCD0D3, shadow #2E4D6A, clay #B5A1FF, visor #22225E, eyes white.
 * The orb drops in from above (ClayRobot animate/delay), specks drift behind it,
 * then the screen hands over to the app.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    // Drop-in from above: ClayRobot DROP_FROM feel, eased.
    val dropPx = remember { Animatable(-420f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(400)) }
        launch { dropPx.animateTo(0f, tween(620, easing = FastOutSlowInEasing)) }
        delay(1400)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(JarvisSkyTop, JarvisSkyBottom))
            )
            .alpha(alpha.value),
        contentAlignment = Alignment.Center
    ) {
        // Speck field behind everything (FoldingParticleShell homage, cheap Canvas version).
        SplashSpecks(Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.offset(y = dropPx.value.dp / 3f)) {
                OrbVoiceVisualizer(
                    Modifier.size(320.dp),
                    particleCount = 520,
                    energy = 0.22f,
                    mode = VoiceMode.IDLE,
                )
            }
            Spacer(Modifier.height(6.dp))
            // Logo badge — the wordmark lives inside design/source/jarvis-logo.png.
            Image(
                painter = painterResource(R.drawable.jarvis_logo),
                contentDescription = "Jarvis",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(120.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(12.dp))
            // Long cool shadow raked left, like the ClayRobot still.
            Box(
                Modifier
                    .size(150.dp, 10.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(JarvisShadow.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(26.dp, 3.dp).background(JarvisPrimary))
        }
    }
}

@Composable
private fun SplashSpecks(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Deterministic pseudo-random specks gathering in soft bands.
        repeat(220) { i ->
            val u = ((i * 0.6180339887f) % 1f)
            val v = ((i * 0.3819660113f) % 1f)
            val band = 0.5f + 0.5f * sin(u * 12f + v * 7f)
            val x = u * w + cos(v * 20f + i) * 8f
            val y = v * h + sin(u * 18f) * 10f
            val r = 1f + (i % 3) * 0.7f
            drawCircle(
                color = if (i % 5 == 0) JarvisPrimary.copy(alpha = 0.5f)
                else JarvisSpeck.copy(alpha = 0.35f + 0.3f * band),
                radius = r,
                center = Offset(x, y)
            )
        }
        // Trail of bright specks falling from the orb's lower edge.
        repeat(28) { i ->
            val f = (i / 28f)
            val x = w / 2f + (i % 7 - 3) * 14f + sin(f * 9f) * 6f
            val y = h / 2f + 130f + f * 220f
            drawCircle(
                color = JarvisEye.copy(alpha = (1f - f) * 0.7f),
                radius = 2.2f * (1f - f) + 0.8f,
                center = Offset(x, y)
            )
        }
    }
}
