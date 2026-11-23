package ai.lumo.app.presentation.agent

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ai.lumo.app.R
import ai.lumo.app.domain.model.AgentMessage
import ai.lumo.app.domain.model.SensitiveAction
import ai.lumo.app.domain.model.ToolStatus
import ai.lumo.app.presentation.components.BrandMark
import ai.lumo.app.presentation.components.MarkdownText
import ai.lumo.app.presentation.components.OrbVoiceVisualizer
import ai.lumo.app.presentation.components.VoiceMode
import ai.lumo.app.presentation.theme.ErrorRed
import ai.lumo.app.presentation.theme.JarvisEye
import ai.lumo.app.presentation.theme.JarvisPrimary
import ai.lumo.app.presentation.theme.JarvisVisor
import ai.lumo.app.presentation.theme.LimeInk
import ai.lumo.app.presentation.theme.LimePop
import ai.lumo.app.presentation.theme.SuccessGreen
import ai.lumo.app.presentation.theme.WarningOrange
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier,
    onOpenBrowser: () -> Unit = {},
    onOpenMailbox: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenChat: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val voiceError by viewModel.voiceError.collectAsState()
    val voiceFallback by viewModel.voiceFallbackRequest.collectAsState()
    val mailSource by viewModel.mailSource.collectAsState()
    val gmailState by (viewModel.gmailConnectionState ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()
    val needsSignIn = mailSource != "DEMO" && !gmailState.startsWith("Connected as") && !gmailState.startsWith("Checking")

    // Keep the stage honest: auto-clear short voice errors.
    LaunchedEffect(voiceError) {
        if (voiceError != null) {
            delay(2200)
            viewModel.clearVoiceError()
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val list = res.data?.getStringArrayListExtra(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = list?.firstOrNull()?.trim()
            if (!text.isNullOrBlank()) viewModel.sendPrompt(text, isVoice = true)
        }
    }
    // STT fix: when inline recognition fails (offline / busy / no match), VoiceManager
    // bumps fallbackRequest — automatically open the system Google voice UI instead of
    // leaving a dead "Missed that".
    var lastFallback by remember { mutableStateOf(0) }
    LaunchedEffect(voiceFallback) {
        if (voiceFallback > lastFallback) {
            lastFallback = voiceFallback
            runCatching { googleLauncher.launch(viewModel.googleVoiceIntent("Speak to Jarvis")) }
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceInput()
    }

    fun onMicTap() {
        if (isListening) {
            viewModel.stopVoiceInput()
            return
        }
        viewModel.clearVoiceError()
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        viewModel.startVoiceInput()
    }

    fun onGoogleTap() {
        try {
            googleLauncher.launch(viewModel.googleVoiceIntent("Speak now"))
        } catch (_: Exception) {
            viewModel.startVoiceInput()
        }
    }

    fun onOrbTap() {
        when {
            isListening -> viewModel.stopVoiceInput()
            isSpeaking -> viewModel.stopSpeaking()
            else -> onMicTap()
        }
    }

    val orbEnergy = when {
        isListening -> 0.30f + rmsLevel.coerceIn(0f, 1f) * 0.70f
        isProcessing -> 0.36f
        isSpeaking -> 0.42f + rmsLevel.coerceIn(0f, 1f) * 0.2f
        else -> 0.12f
    }
    // Jarvis voice disturbances: receiving folds inward (clay), talking blooms (white).
    val voiceMode = when {
        isListening -> VoiceMode.LISTENING
        isSpeaking -> VoiceMode.SPEAKING
        isProcessing -> VoiceMode.THINKING
        else -> VoiceMode.IDLE
    }
    val orbTop = when {
        isSpeaking -> JarvisEye
        isListening -> JarvisPrimary
        isProcessing -> JarvisVisor
        else -> JarvisPrimary
    }
    val orbBottom = if (isSpeaking) JarvisPrimary else JarvisEye
    val statusText = when {
        voiceError != null -> voiceError
        isListening -> recognizedText?.takeIf { it.isNotBlank() } ?: "Listening"
        isProcessing -> "Thinking"
        isSpeaking -> "Speaking"
        messages.isEmpty() -> "Tap to talk"
        else -> null
    }
    val statusTone = when {
        voiceError != null -> ErrorRed
        isListening || isSpeaking || isProcessing -> LimePop
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StageAmbience()

        Column(Modifier.fillMaxSize()) {
            StageHeader(onOpenBrowser = onOpenBrowser)

            AnimatedVisibility(
                visible = needsSignIn,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    ConnectMailChip(onClick = onOpenSettings)
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val orbSize = minOf(maxWidth * 0.74f, maxHeight * 0.66f).coerceIn(180.dp, 340.dp)

                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(orbSize), contentAlignment = Alignment.Center) {
                        OrbVoiceVisualizer(
                            modifier = Modifier.fillMaxSize(),
                            particleCount = 520,
                            energy = orbEnergy,
                            mode = voiceMode,
                            dotColorTop = orbTop,
                            dotColorBottom = orbBottom
                        )
                        // Tap target matches the bubble itself, not the soft halo.
                        Box(
                            Modifier
                                .fillMaxSize(0.82f)
                                .semantics {
                                    contentDescription = "Voice orb. Tap to talk, double tap to chat."
                                    onClick(label = "Talk") {
                                        onOrbTap()
                                        true
                                    }
                                }
                                .pointerInput(isListening, isSpeaking) {
                                    detectTapGestures(
                                        onTap = { onOrbTap() },
                                        onDoubleTap = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onOpenChat()
                                        },
                                        onLongPress = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onGoogleTap()
                                        }
                                    )
                                }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Box(Modifier.height(38.dp), contentAlignment = Alignment.Center) {
                        StageStatusPill(
                            text = statusText,
                            tone = statusTone,
                            live = isListening || isProcessing || isSpeaking,
                            showStop = isListening || isSpeaking,
                            showGoogle = voiceError != null,
                            onStop = {
                                if (isListening) viewModel.stopVoiceInput() else viewModel.stopSpeaking()
                            },
                            onGoogle = ::onGoogleTap
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // The obvious way into conversation.
                    Button(
                        onClick = {
                            if (isListening) viewModel.stopVoiceInput()
                            onOpenChat()
                        },
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_orb_chat),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Chat with Jarvis", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                StageAction(
                    icon = R.drawable.ic_orb_inbox,
                    label = "Inbox",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onOpenMailbox,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(y = (-24).dp)
                        .padding(end = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun StageHeader(onOpenBrowser: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(size = 30.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            "Jarvis",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = onOpenBrowser,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_orb_globe),
                    contentDescription = "Browser",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectMailChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = WarningOrange.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.25f))
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_orb_inbox),
                contentDescription = null,
                tint = WarningOrange,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Connect Gmail",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun StageAction(
    icon: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = modifier.size(54.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StageStatusPill(
    text: String?,
    tone: Color,
    live: Boolean,
    showStop: Boolean,
    showGoogle: Boolean,
    onStop: () -> Unit,
    onGoogle: () -> Unit
) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 3 }
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            Row(
                Modifier.padding(
                    start = 14.dp,
                    end = if (showStop || showGoogle) 6.dp else 14.dp,
                    top = 7.dp,
                    bottom = 7.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (live) {
                    LiveDot(tone)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tone == ErrorRed) ErrorRed else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 240.dp)
                )
                if (showStop || showGoogle) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable(onClick = if (showGoogle) onGoogle else onStop),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showGoogle) {
                            Icon(
                                painterResource(R.drawable.ic_orb_spark),
                                contentDescription = "Use Google voice",
                                tint = tone,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = tone,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(720), repeatMode = RepeatMode.Reverse),
        label = "liveDotAlpha"
    )
    Box(Modifier.size(7.dp).background(color.copy(alpha = alpha), CircleShape))
}

@Composable
private fun StageAmbience() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth(0.9f)
                .height(360.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(
                        listOf(LimePop.copy(alpha = if (dark) 0.10f else 0.16f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier
                .fillMaxWidth(1.1f)
                .height(380.dp)
                .align(Alignment.BottomEnd)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.05f else 0.35f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
    }
}
