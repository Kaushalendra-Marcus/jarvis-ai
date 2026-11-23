package ai.lumo.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import ai.lumo.app.R
import ai.lumo.app.presentation.theme.JarvisEye
import ai.lumo.app.presentation.theme.JarvisPrimary
import ai.lumo.app.presentation.theme.JarvisSpeck
import ai.lumo.app.presentation.theme.JarvisVisor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Modern voice state — drives distinct disturbances for listen vs speak. */
enum class VoiceMode { IDLE, LISTENING, SPEAKING, THINKING }

/**
 * The Jarvis voice orb — glass bubble + FoldingParticleShell-inspired disturbance.
 *
 * - LISTENING (voice-to-text, receiving): ripples fold *inward*, specks gather in,
 *   ring disturbance tight and fast — "the mic is catching you".
 * - SPEAKING (text-to-voice, talking): ripples push *outward*, halo blooms, waveform
 *   ring dances — "Jarvis is talking".
 * - THINKING: slow visor-colored swirl. IDLE: calm breathing.
 */
@Composable
fun OrbVoiceVisualizer(
    modifier: Modifier = Modifier,
    particleCount: Int = 420,
    energy: Float = 0f,
    spinSpeed: Float = 0.35f,
    dotColorTop: Color = JarvisPrimary,
    dotColorBottom: Color = JarvisEye,
    mode: VoiceMode = VoiceMode.IDLE,
) {
    val level by animateFloatAsState(
        targetValue = energy.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 220f),
        label = "orbEnergy"
    )

    val life = rememberInfiniteTransition(label = "orbLife")
    val float by life.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(7600, easing = LinearEasing)),
        label = "float"
    )
    val shimmer by life.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )
    val sway by life.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    // Mode character: direction + speed + wave shape of the disturbance.
    val rippleDir = when (mode) {
        VoiceMode.LISTENING -> -1f
        VoiceMode.SPEAKING -> 1f
        else -> 1f
    }
    val rippleSpeed = when (mode) {
        VoiceMode.LISTENING -> 1.6f
        VoiceMode.SPEAKING -> 1.25f
        VoiceMode.THINKING -> 0.5f
        VoiceMode.IDLE -> 0.7f
    }
    val waveAmp = when (mode) {
        VoiceMode.LISTENING -> 0.10f + level * 0.10f
        VoiceMode.SPEAKING -> 0.06f + level * 0.22f
        VoiceMode.THINKING -> 0.05f
        VoiceMode.IDLE -> 0.03f + level * 0.04f
    }
    val waveFreq = when (mode) {
        VoiceMode.LISTENING -> 7f
        VoiceMode.SPEAKING -> 5f
        else -> 3f
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val shortSide = min(size.width, size.height)
            val r = shortSide * 0.40f
            val glowRadius = shortSide * 0.49f
            val breathe = 0.5f + 0.5f * sin(float)
            val pulse = (0.35f + level * 0.65f) * (0.75f + 0.25f * breathe)

            // Halo blooms when speaking, tightens when listening.
            val haloScale = when (mode) {
                VoiceMode.SPEAKING -> 1f + level * 0.10f
                VoiceMode.LISTENING -> 1f - level * 0.04f
                else -> 1f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    0f to dotColorTop.copy(alpha = 0.14f + level * 0.16f),
                    0.52f to JarvisVisor.copy(alpha = 0.06f + level * 0.08f),
                    0.80f to dotColorBottom.copy(alpha = 0.035f + level * 0.06f),
                    1f to Color.Transparent,
                    center = c,
                    radius = glowRadius * haloScale
                ),
                radius = glowRadius * haloScale,
                center = c
            )

            drawCircle(
                color = dotColorTop.copy(alpha = 0.06f + level * 0.10f),
                radius = r * (1.08f + 0.03f * breathe + level * 0.03f),
                center = c,
                style = Stroke(width = 1.2f)
            )

            // Ripples fold inward for listening, outward for speaking.
            val ripples = if (mode == VoiceMode.IDLE) 2 else 3
            repeat(ripples) { i ->
                val raw = (shimmer * rippleSpeed + i * (1f / ripples)) % 1f
                val phase = if (rippleDir < 0) 1f - raw else raw
                drawCircle(
                    color = dotColorTop.copy(alpha = (1f - phase) * (0.05f + level * 0.22f)),
                    radius = r * (1.04f + phase * 0.18f),
                    center = c,
                    style = Stroke(width = 1.5f + level * 1.2f)
                )
            }

            // Waveform disturbance ring — the modern "speaking/receiving" cue.
            val dots = 64
            repeat(dots) { i ->
                val a = (i / dots.toFloat()) * (PI * 2).toFloat()
                val wob = sin(a * waveFreq + float * rippleSpeed * 2f * rippleDir + i * 0.35f)
                val rr = r * (1.16f + waveAmp * wob + 0.02f * sin(float + i))
                val point = Offset(
                    x = c.x + cos(a) * rr,
                    y = c.y + sin(a) * rr * 0.96f
                )
                val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(float * 2.2f + i * 1.3f))
                val col = when {
                    mode == VoiceMode.SPEAKING && i % 4 == 0 -> JarvisEye
                    i % 3 == 0 -> dotColorBottom
                    else -> dotColorTop
                }
                drawCircle(
                    color = col.copy(alpha = (0.10f + 0.35f * pulse) * twinkle),
                    radius = (1.3f + 1.1f * pulse) * (size.minDimension / 420f).coerceAtLeast(0.75f),
                    center = point
                )
            }

            // Speck field: gathers inward while receiving, sheds outward while talking.
            val specks = (particleCount / 36).coerceIn(6, 20)
            val spin = float * spinSpeed * (if (mode == VoiceMode.LISTENING) -1.4f else 1f)
            repeat(specks) { i ->
                val angle = (i / specks.toFloat()) * (PI * 2).toFloat() + spin
                val drift = when (mode) {
                    VoiceMode.LISTENING -> 0.92f - 0.06f * sin(float * 1.7f + i)
                    VoiceMode.SPEAKING -> 1.22f + 0.08f * sin(float * 1.7f + i)
                    else -> 1.14f + 0.04f * sin(float + i)
                }
                val tilt = 0.34f + 0.10f * sin(i * 1.9f)
                val rr = r * drift
                val point = Offset(
                    x = c.x + cos(angle) * rr,
                    y = c.y + sin(angle) * rr * tilt
                )
                drawCircle(
                    color = JarvisSpeck.copy(alpha = 0.10f + 0.25f * pulse),
                    radius = (1f + 0.8f * pulse) * (size.minDimension / 420f).coerceAtLeast(0.6f),
                    center = point
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.orb_hero),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.84f)
                .graphicsLayer {
                    val breatheScale = 1f + 0.012f * sin(float) + level * 0.03f
                    scaleX = breatheScale
                    scaleY = breatheScale
                    translationY = sin(float * 0.82f) * 5f
                    rotationZ = sway * 1.6f
                }
        )

        // A moving specular highlight sells the glass.
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = min(size.width, size.height) * 0.40f
            val sx = c.x + cos(float * 0.7f) * r * 0.26f
            val sy = c.y - r * 0.32f + sin(float * 0.7f) * r * 0.10f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.10f + 0.06f * shimmer),
                    1f to Color.Transparent,
                    center = Offset(sx, sy),
                    radius = r * 0.52f
                ),
                radius = r,
                center = c
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to dotColorBottom.copy(alpha = 0.05f + level * 0.10f),
                    1f to Color.Transparent,
                    center = Offset(c.x + r * 0.18f, c.y + r * 0.44f),
                    radius = r * 0.48f
                ),
                radius = r,
                center = c
            )
        }
    }
}
