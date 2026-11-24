package ai.jarvis.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ai.jarvis.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Jarvis opening scene — pure black, white floating particles, the logo
 * opening with a scale + fade-in.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(500)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 120f)) }
        delay(1500)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(alpha.value),
        contentAlignment = Alignment.Center
    ) {
        FloatingParticles(Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.jarvis_logo),
                contentDescription = "Jarvis",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .scale(scale.value)
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .size(120.dp, 3.dp)
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }
    }
}

/** Slow white dots drifting upward, twinkling. */
@Composable
private fun FloatingParticles(modifier: Modifier = Modifier) {
    val life = rememberInfiniteTransition(label = "splashFloat")
    val t by life.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "t"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        repeat(90) { i ->
            val h1 = hash(i * 5 + 3)
            val h2 = hash(i * 5 + 17)
            val h3 = hash(i * 5 + 41)
            val rise = ((h2 + t * (0.04f + h1 * 0.08f)) % 1f)
            val x = (h1 * 1.13f % 1f) * w + kotlin.math.sin((t * 6.28f + i) * 0.7f) * 10f
            val y = (1f - rise) * h
            val tw = 0.25f + 0.75f * (0.5f + 0.5f * kotlin.math.sin(t * 12.56f + i * 2.1f))
            drawCircle(
                color = Color.White.copy(alpha = (0.15f + 0.55f * h3) * tw),
                radius = 0.8f + h2 * 1.8f,
                center = Offset(x, y)
            )
        }
    }
}
