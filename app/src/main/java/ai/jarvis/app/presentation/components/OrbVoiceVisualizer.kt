package ai.jarvis.app.presentation.components

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
import ai.jarvis.app.presentation.theme.JarvisEye
import ai.jarvis.app.presentation.theme.JarvisPrimary
import ai.jarvis.app.presentation.theme.JarvisSpeck
import ai.jarvis.app.presentation.theme.JarvisVisor
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
        VoiceMode.LISTENING -> 0.16f + level * 0.16f
        VoiceMode.SPEAKING -> 0.10f + level * 0.32f
        VoiceMode.THINKING -> 0.07f
        VoiceMode.IDLE -> 0.04f + level * 0.05f
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

            // Halo blooms hard when speaking, tightens when listening.
            val haloScale = when (mode) {
                VoiceMode.SPEAKING -> 1f + level * 0.18f
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

        // Folding particle shell: deterministic dots on a breathing, crumpling
        // sphere + drifting specks + falling trail. No image assets.
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val shortSide = min(size.width, size.height)
            val r = shortSide * 0.34f
            val time = float
            val breathe2 = 0.5f + 0.5f * sin(time)
            val pulse2 = (0.35f + level * 0.65f) * (0.75f + 0.25f * breathe2)

            // Speck field.
            repeat(42) { i ->
                val h1 = hash(i * 3 + 1)
                val h2 = hash(i * 3 + 2)
                val h3 = hash(i * 3 + 3)
                val drift = (time * 0.03f * (0.5f + h1) + h2) % 1f
                val px = c.x + (drift - 0.5f) * size.width * 0.9f
                val py = c.y + ((h3 + time * 0.02f) % 1f - 0.5f) * size.height * 0.9f
                val tw = 0.3f + 0.7f * (0.5f + 0.5f * sin(time * 2f + i))
                drawCircle(JarvisSpeck.copy(alpha = 0.25f * tw), radius = 1.2f, center = Offset(px, py))
            }

            // Folding shell.
            val crumple = 0.5f + 0.5f * sin(time * 0.5f)
            val spread = when (mode) {
                VoiceMode.SPEAKING -> 1f + level * 0.25f
                VoiceMode.LISTENING -> 1f - level * 0.12f
                else -> 1f
            }
            val dir = if (mode == VoiceMode.LISTENING) -1f else 1f
            repeat(200) { i ->
                val h1 = hash(i * 7 + 11)
                val h2 = hash(i * 7 + 23)
                val h3 = hash(i * 7 + 37)
                val ang = h1 * (PI * 2).toFloat() + time * (0.15f + 0.1f * h2) * dir
                val fold = 0.55f + 0.45f * (0.5f + 0.5f * sin(time * (0.6f + h3) + i * 1.7f)) * (0.6f + 0.4f * crumple)
                val rr = r * fold * spread * (0.9f + 0.2f * h2)
                val p = Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr * 0.92f)
                val bright = (0.25f + 0.75f * h3) * (0.5f + 0.5f * pulse2 + level * 0.5f)
                val col = when {
                    i % 5 == 0 -> JarvisEye
                    i % 3 == 0 -> dotColorBottom
                    else -> dotColorTop
                }
                drawCircle(col.copy(alpha = 0.5f * bright), radius = 2.2f, center = p)
                drawCircle(col.copy(alpha = 0.9f * bright), radius = 1f, center = p)
            }

            // Falling trail.
            repeat(14) { i ->
                val h1 = hash(i * 13 + 51)
                val fall = ((time * 0.12f * (0.6f + h1) + h1 * 7f) % 1f)
                val px = c.x + (h1 - 0.5f) * r * 0.8f + sin(fall * 6f) * 4f
                val py = c.y + r * 0.7f + fall * r * 1.1f
                val a = (1f - fall) * (0.25f + level * 0.6f)
                drawCircle(JarvisEye.copy(alpha = a), radius = 1.6f, center = Offset(px, py))
            }
        }

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

/** Deterministic pseudo-random 0..1 for stable particle layouts. */
internal fun hash(i: Int): Float {
    val x = kotlin.math.sin(i * 12.9898f) * 43758.5453f
    return x - kotlin.math.floor(x)
}
