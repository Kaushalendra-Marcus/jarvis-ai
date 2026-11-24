package ai.jarvis.app.presentation.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.jarvis.app.presentation.components.BrandMark
import ai.jarvis.app.presentation.components.CenteredScroll
import ai.jarvis.app.presentation.components.LimeBuddy
import ai.jarvis.app.presentation.components.OrbVoiceVisualizer
import ai.jarvis.app.presentation.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    Surface(color = MidnightBg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(midnightStageBrush()).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (pagerState.currentPage < 2) TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(2) } }) {
                    Text("Skip", color = InkMid)
                } else Spacer(Modifier.height(48.dp))
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> AskPage()
                    1 -> ControlPage()
                    else -> LanePage(onFinish)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    val c by animateColorAsState(if (pagerState.currentPage == i) LimePop else InkLow, label = "dot")
                    Box(Modifier.padding(4.dp).size(if (pagerState.currentPage == i) 22.dp else 8.dp, 8.dp).background(c, CircleShape))
                }
            }
            if (pagerState.currentPage < 2) {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LimePop, contentColor = LimeInk),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(if (pagerState.currentPage == 0) "Show me Jarvis" else "How do I stay in charge?", fontWeight = FontWeight.Bold) }
            } else Spacer(Modifier.height(54.dp))
        }
    }
}

@Composable
private fun AskPage() {
    CenteredScroll(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        OrbVoiceVisualizer(Modifier.fillMaxWidth().height(220.dp), particleCount = 800, energy = 0.4f)
        Spacer(Modifier.height(8.dp))
        BrandMark(size = 54.dp)
        Spacer(Modifier.height(12.dp))
        Text("Talk. Jarvis handles it.", color = InkHigh, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("One mic, one globe, one buddy. It triages mail, keeps memories, runs routines and scouts the web while you get on with life.", color = InkMid, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ControlPage() {
    CenteredScroll(Modifier.fillMaxSize()) {
        LimeBuddy(Modifier.fillMaxWidth().height(190.dp), joy = 0.7f, speaking = true)
        Spacer(Modifier.height(12.dp))
        Text("Nothing sends without you", color = InkHigh, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        Feature(Icons.Default.Shield, "You approve", "Emails and browser clicks pause for your tap. Edit the draft inline.")
        Feature(Icons.Default.Schedule, "It runs headless", "\"Every morning at 8\" keeps working with the app closed.")
        Feature(Icons.Default.Psychology, "It actually remembers", "Preferences stick across chats. Delete any memory in one tap.")
        Feature(Icons.Default.Mic, "Voice in, voice out", "The orb listens, Jarvis talks back. Mute anytime.")
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(LimePop.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = LimePop)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = InkHigh, fontWeight = FontWeight.Bold)
            Text(text, color = InkMid, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LanePage(onFinish: (String) -> Unit) {
    CenteredScroll(Modifier.fillMaxSize()) {
        Text("Pick your lane", color = InkHigh, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text("Swap anytime in Settings → Mailbox. Demo never touches a real inbox.", color = InkMid, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onFinish("OWN") }, modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LimePop, contentColor = LimeInk)) { Text("Use my Gmail", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onFinish("DEMO") }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Explore the demo", color = InkHigh) }
    }
}
