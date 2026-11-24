package ai.jarvis.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Totally new companion: Lime Buddy.
 * Nothing reused from the old clay SVG — lime capsule body, midnight glass
 * visor, twin glow eyes that blink / look around / turn happy (^ ^) when
 * the agent speaks. Used for voice OUTPUT.
 */
@Composable
fun LimeBuddy(
    modifier: Modifier = Modifier,
    lookX: Float = 0f, // -1..1
    lookY: Float = 0f, // -1..1
    speaking: Boolean = false,
    joy: Float = 0f, // 0..1 happy eyes
    hop: Float = 0f, // 0..1 hop lift
) {
    val inf = rememberInfiniteTransition(label = "buddy")
    val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Restart), label = "bob")
    val blinkPhase by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4200), RepeatMode.Restart), label = "blink")
    val open = if (blinkPhase > 0.94f) 0.12f else 1f

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val u = minOf(w / 10f, h / 12f)
        val cx = w / 2f
        val lift = (kotlin.math.sin(bob * Math.PI.toFloat()) * u * 0.08f) + hop * u * 0.6f
        val cy = h * 0.52f - lift

        // shadow
        drawOval(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(cx - u * 2.6f, cy + u * 3.1f),
            size = Size(u * 5.2f, u * 0.6f)
        )
        // halo when speaking
        if (speaking || joy > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF2F7CF6).copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(cx, cy), radius = u * 4.2f
                ),
                radius = u * 4.2f, center = Offset(cx, cy)
            )
        }
        val lime = Color(0xFF2F7CF6)
        val limeHi = Color(0xFFF0FFB0)
        val limeSh = Color(0xFF8FAE1F)
        val midnight = Color(0xFF0B1220)

        // antenna
        drawLine(limeSh, Offset(cx, cy - u * 3.6f), Offset(cx, cy - u * 2.9f), u * 0.16f)
        drawCircle(if (speaking) Color(0xFF7DF9FF) else lime, u * 0.22f, Offset(cx, cy - u * 3.7f))
        // ears
        drawCircle(limeSh, u * 0.5f, Offset(cx - u * 2.2f, cy - u * 1.2f))
        drawCircle(lime, u * 0.36f, Offset(cx - u * 2.2f, cy - u * 1.2f))
        drawCircle(limeSh, u * 0.5f, Offset(cx + u * 2.2f, cy - u * 1.2f))
        drawCircle(lime, u * 0.36f, Offset(cx + u * 2.2f, cy - u * 1.2f))
        // head
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(limeHi, lime, limeSh)),
            topLeft = Offset(cx - u * 1.9f, cy - u * 2.9f),
            size = Size(u * 3.8f, u * 2.6f),
            cornerRadius = CornerRadius(u * 0.9f)
        )
        // visor — midnight glass, totally different shape: wide + tilted shine
        drawRoundRect(
            color = midnight,
            topLeft = Offset(cx - u * 1.55f, cy - u * 2.35f),
            size = Size(u * 3.1f, u * 1.5f),
            cornerRadius = CornerRadius(u * 0.7f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(cx - u * 1.4f, cy - u * 2.25f),
            size = Size(u * 2.8f, u * 0.4f),
            cornerRadius = CornerRadius(u * 0.2f)
        )
        // eyes
        val ex = lookX.coerceIn(-1f, 1f) * u * 0.28f
        val ey = lookY.coerceIn(-1f, 1f) * u * 0.18f
        val eyeY = cy - u * 1.55f + ey
        if (joy > 0.5f) {
            // happy ^ ^
            val style = Stroke(width = u * 0.14f)
            drawArc(Color.White, 180f, 180f, false, Offset(cx - u * 1.0f + ex, eyeY), Size(u * 0.6f, u * 0.6f), style = style)
            drawArc(Color.White, 180f, 180f, false, Offset(cx + u * 0.4f + ex, eyeY), Size(u * 0.6f, u * 0.6f), style = style)
        } else {
            drawOval(Color.White, Offset(cx - u * 1.0f + ex, eyeY), Size(u * 0.6f, u * 0.34f * open))
            drawOval(Color.White, Offset(cx + u * 0.4f + ex, eyeY), Size(u * 0.6f, u * 0.34f * open))
            drawCircle(Color(0xFF0B1220), u * 0.09f, Offset(cx - u * 0.7f + ex * 1.2f, eyeY + u * 0.17f * open))
            drawCircle(Color(0xFF0B1220), u * 0.09f, Offset(cx + u * 0.7f + ex * 1.2f, eyeY + u * 0.17f * open))
        }
        // body
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(lime, limeSh)),
            topLeft = Offset(cx - u * 1.3f, cy + u * 0.1f),
            size = Size(u * 2.6f, u * 2.2f),
            cornerRadius = CornerRadius(u * 0.7f)
        )
        // chest light
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White, Color(0xFF7DF9FF))),
            radius = u * 0.28f, center = Offset(cx, cy + u * 1.0f)
        )
        // arms
        drawOval(lime, Offset(cx - u * 2.05f, cy + u * 0.35f), Size(u * 0.7f, u * 1.5f))
        drawOval(limeSh, Offset(cx + u * 1.35f, cy + u * 0.35f), Size(u * 0.7f, u * 1.5f))
        // feet
        drawOval(midnight, Offset(cx - u * 1.05f, cy + u * 3.05f), Size(u * 0.85f, u * 0.5f))
        drawOval(midnight, Offset(cx + u * 0.2f, cy + u * 3.05f), Size(u * 0.85f, u * 0.5f))
    }
}
