package ai.jarvis.app.presentation.agent

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ai.jarvis.app.R
import ai.jarvis.app.core.util.TimeUtils
import ai.jarvis.app.domain.model.AgentMessage
import ai.jarvis.app.domain.model.SensitiveAction
import ai.jarvis.app.domain.model.ToolStatus
import ai.jarvis.app.presentation.components.MarkdownText
import ai.jarvis.app.presentation.components.OrbVoiceVisualizer
import ai.jarvis.app.presentation.theme.ErrorRed
import ai.jarvis.app.presentation.theme.LimeInk
import ai.jarvis.app.presentation.theme.LimePop
import ai.jarvis.app.presentation.theme.SuccessGreen
import ai.jarvis.app.presentation.theme.WarningOrange

private data class Suggestion(val icon: Int, val label: String, val prompt: String)

private val CHAT_SUGGESTIONS = listOf(
    Suggestion(R.drawable.ic_orb_inbox, "Triage inbox", "Scan last 20 mails. Flag needs-me. Draft top 2."),
    Suggestion(R.drawable.ic_orb_routine, "Morning brief", "Every morning at 8: mail + top 3 tasks."),
    Suggestion(R.drawable.ic_orb_send, "Draft reply", "Draft a follow-up. Don't send."),
)

/**
 * Full-screen chat: history (new / switch / delete), speaker toggle, model badge,
 * brand app icons on tool cards, and a redesigned composer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AgentViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val voiceError by viewModel.voiceError.collectAsState()
    val voiceOn by viewModel.voiceFeedback.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activeId by viewModel.activeConversationId.collectAsState()
    val modelLabel by viewModel.llmLabel.collectAsState()

    var draft by rememberSaveable { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<ai.jarvis.app.domain.repository.ConversationInfo?>(null) }

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

    val shown = remember(messages) { messages.filterNot { it is AgentMessage.AgentReasoning } }
    val listState = rememberLazyListState()
    LaunchedEffect(shown.size, isProcessing) {
        val target = shown.size - 1 + if (isProcessing) 1 else 0
        if (target >= 0 && (shown.isNotEmpty() || isProcessing)) {
            runCatching { listState.animateScrollToItem(target) }
        }
    }
    // Clear errors after a beat so the bar never sticks.
    LaunchedEffect(voiceError) {
        if (voiceError != null) {
            kotlinx.coroutines.delay(2600)
            viewModel.clearVoiceError()
        }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header: back · title + model · speaker · history
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isListening) viewModel.stopVoiceInput()
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text("Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        modelLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { viewModel.setVoiceFeedback(!voiceOn) }) {
                    Icon(
                        if (voiceOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = if (voiceOn) "Mute spoken replies" else "Speak replies aloud",
                        tint = if (voiceOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showHistory = true }) {
                    Icon(Icons.Default.History, contentDescription = "Chats")
                }
            }
        }

        // Live voice bar
        AnimatedVisibility(visible = isListening || voiceError != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrbVoiceVisualizer(
                        Modifier.size(26.dp),
                        particleCount = 120,
                        energy = 0.3f + rmsLevel.coerceIn(0f, 1f) * 0.7f,
                        mode = ai.jarvis.app.presentation.components.VoiceMode.LISTENING
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        voiceError ?: recognizedText?.takeIf { it.isNotBlank() } ?: "Listening…",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (voiceError != null) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (voiceError != null) {
                        TextButton(onClick = { viewModel.clearVoiceError() }) { Text("OK") }
                    } else {
                        TextButton(onClick = { viewModel.stopVoiceInput() }) { Text("Stop") }
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty() && !isProcessing) {
                EmptyChat(onPick = { prompt -> viewModel.sendPrompt(prompt) })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shown, key = { it.id }) { message ->
                        ChatMessage(message = message, onRetry = { p -> viewModel.sendPrompt(p) })
                    }
                    if (isProcessing) {
                        item(key = "typing") { TypingRow() }
                    }
                }
            }
        }

        ChatComposer(
            value = draft,
            onValueChange = { draft = it },
            onMic = ::onMicTap,
            isListening = isListening,
            onSend = {
                if (draft.isNotBlank()) {
                    viewModel.sendPrompt(draft)
                    draft = ""
                }
            },
            canSend = draft.isNotBlank() && !isProcessing
        )
    }

    if (showHistory) {
        ConversationsSheet(
            conversations = conversations,
            activeId = activeId,
            onDismiss = { showHistory = false },
            onNew = {
                viewModel.newConversation()
                draft = ""
                showHistory = false
            },
            onOpen = {
                viewModel.openConversation(it)
                draft = ""
                showHistory = false
            },
            onDelete = { chatToDelete = it }
        )
    }

    chatToDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text("Delete chat?", fontWeight = FontWeight.Bold) },
            text = { Text("“${chat.title}” goes away for good.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteConversation(chat.id)
                        chatToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { chatToDelete = null }) { Text("Keep") } }
        )
    }

    pendingConfirmation?.let { action ->
        ApprovalSheet(
            action = action,
            onConfirm = { edited -> viewModel.onConfirmAction(true, edited) },
            onDismiss = { viewModel.onConfirmAction(false) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationsSheet(
    conversations: List<ai.jarvis.app.domain.repository.ConversationInfo>,
    activeId: String?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (ai.jarvis.app.domain.repository.ConversationInfo) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onNew) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New chat")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (conversations.isEmpty()) {
                Text(
                    "No chats yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                conversations.forEach { chat ->
                    val active = chat.id == activeId
                    Surface(
                        onClick = { onOpen(chat.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (active) 2.dp else 1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(chat.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (chat.preview.isNotBlank()) {
                                    Text(
                                        chat.preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    TimeUtils.formatDateTime(chat.updatedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            IconButton(onClick = { onDelete(chat) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete chat", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmptyChat(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OrbVoiceVisualizer(Modifier.size(132.dp), particleCount = 220, energy = 0.08f)
        Spacer(Modifier.height(4.dp))
        Text(
            "Ask anything",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(18.dp))
        CHAT_SUGGESTIONS.forEach { suggestion ->
            Surface(
                onClick = { onPick(suggestion.prompt) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(suggestion.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        suggestion.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onMic: () -> Unit,
    isListening: Boolean,
    onSend: () -> Unit,
    canSend: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(30.dp)
            )
            .padding(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            onClick = onMic,
            shape = CircleShape,
            color = if (isListening) ErrorRed.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_orb_mic),
                    contentDescription = if (isListening) "Stop" else "Speak",
                    tint = if (isListening) ErrorRed else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text("Message Jarvis…", style = MaterialTheme.typography.bodyMedium)
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            maxLines = 4,
            shape = RoundedCornerShape(22.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
        Spacer(Modifier.width(6.dp))
        Surface(
            onClick = { if (canSend) onSend() },
            shape = CircleShape,
            color = if (canSend) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            border = if (canSend) null
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatMessage(message: AgentMessage, onRetry: ((String) -> Unit)?) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    when (message) {
        is AgentMessage.UserPrompt -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Text(
                    message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
            }
        }

        is AgentMessage.ToolAction -> ToolCard(message)

        is AgentMessage.AgentResponse -> Column(Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 5.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                MarkdownText(
                    markdown = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
            Box(
                Modifier
                    .padding(top = 2.dp, start = 4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable {
                        clip.setText(AnnotatedString(message.text))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
            if (message.appliedMemories.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Used from memory: " + message.appliedMemories.joinToString(" · ") +
                            if (message.appliedSmartRecall) " · smart recall" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        is AgentMessage.ErrorMessage -> Surface(
            shape = RoundedCornerShape(18.dp),
            color = ErrorRed.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(start = 13.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    message.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                message.retryPrompt?.let { retry ->
                    if (onRetry != null) {
                        IconButton(onClick = { onRetry(retry) }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Internal reasoning is deliberately not shown — the chat stays clean.
        is AgentMessage.AgentReasoning -> Unit
    }
}

@Composable
private fun ToolCard(message: AgentMessage.ToolAction) {
    var open by remember { mutableStateOf(false) }
    val long = (message.resultOutput?.length ?: 0) > 140
    val brandRes = ToolPresentation.iconRes(message.toolName)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = long) { open = !open }
            .animateContentSize()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (brandRes != null) {
                    Image(
                        painterResource(brandRes),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            ToolPresentation.icon(message.toolName),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    ToolPresentation.title(message.toolName),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (message.status == ToolStatus.RUNNING) {
                    CircularProgressIndicator(
                        Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    ToolGlyph(message.status)
                }
            }
            if (!message.resultOutput.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    message.resultOutput.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (open) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ToolGlyph(status: ToolStatus) {
    val color = when (status) {
        ToolStatus.RUNNING -> WarningOrange
        ToolStatus.AWAITING_CONFIRMATION -> WarningOrange
        ToolStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        ToolStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        ToolStatus.SUCCESS -> SuccessGreen
        ToolStatus.FAILED -> ErrorRed
    }
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
private fun TypingRow() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

@Composable
fun ApprovalSheet(
    action: SensitiveAction,
    onConfirm: (Map<String, Any?>?) -> Unit,
    onDismiss: () -> Unit
) {
    var to by remember(action.actionId) {
        mutableStateOf((action as? SensitiveAction.SendEmail)?.recipient.orEmpty())
    }
    var subject by remember(action.actionId) {
        mutableStateOf((action as? SensitiveAction.SendEmail)?.subject.orEmpty())
    }
    var body by remember(action.actionId) {
        mutableStateOf((action as? SensitiveAction.SendEmail)?.body.orEmpty())
    }

    val sending = action is SensitiveAction.SendEmail

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = WarningOrange,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                if (sending) "Send email?" else "Continue?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (action is SensitiveAction.SendEmail) {
                    OutlinedTextField(
                        to, { to = it },
                        label = { Text("To") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        subject, { subject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        body, { body = it },
                        label = { Text("Message") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (action is SensitiveAction.BrowserInteraction) {
                    Text(
                        action.description,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        action.pageTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val edited: Map<String, Any?> = if (action is SensitiveAction.SendEmail) {
                        mapOf("recipient" to to, "subject" to subject, "body" to body)
                    } else {
                        mapOf("selector" to (action as SensitiveAction.BrowserInteraction).selector)
                    }
                    onConfirm(edited)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimePop,
                    contentColor = LimeInk
                )
            ) {
                Text(if (sending) "Send" else "Continue")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
