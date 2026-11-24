package ai.jarvis.app.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared midnight-glass design tokens. */

fun midnightStageBrush(): Brush = Brush.verticalGradient(
    listOf(Color(0xFF0B1220), Color(0xFF060A13), Color(0xFF0A1428))
)

fun orbGlowBrush(alpha: Float = 0.5f): Brush = Brush.radialGradient(
    listOf(
        JarvisPrimary.copy(alpha = 0.35f * alpha),
        JarvisEye.copy(alpha = 0.18f * alpha),
        Color.Transparent
    )
)

fun limeSweepBrush(): Brush = Brush.horizontalGradient(
    listOf(JarvisPrimary, JarvisPrimaryMist, JarvisEye)
)

fun glassBrush(dark: Boolean): Brush = if (dark) {
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f)))
} else {
    Brush.verticalGradient(listOf(Color.White, Color(0xFFF4F7F2)))
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (dark) MidnightCard.copy(alpha = 0.92f) else PaperCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (dark) Color.White.copy(alpha = 0.08f) else PaperLine
        ),
        modifier = modifier,
        content = { Column(Modifier.padding(16.dp), content = content) }
    )
}

@Composable
fun SectionHeader(
    kicker: String,
    title: String,
    subtitle: String? = null,
    dark: Boolean = true
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier.size(18.dp, 3.dp).clip(RoundedCornerShape(2.dp))
                    .background(limeSweepBrush())
            )
            Spacer(Modifier.width(8.dp))
            Text(
                kicker.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = if (dark) LimePop else PineDeep
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = if (dark) InkHigh else PineInk
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dark) InkMid else Color(0xFF52606B)
            )
        }
    }
}

@Composable
fun StatusPill(text: String, dot: Color, dark: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = if (dark) Color.White.copy(alpha = 0.06f) else Color(0xFF0F766E).copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (dark) Color.White.copy(alpha = 0.09f) else PaperLine
        )
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(7.dp).background(dot, androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (dark) InkHigh else PineInk
            )
        }
    }
}
