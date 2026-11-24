package ai.jarvis.app.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import ai.jarvis.app.R

/** Jarvis mark — the logo badge from design/source/jarvis-logo.png. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size)) {
        Image(
            painter = painterResource(R.drawable.jarvis_logo),
            contentDescription = "Jarvis",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().clip(CircleShape)
        )
    }
}
