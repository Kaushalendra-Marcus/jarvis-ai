package ai.lumo.app.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.lumo.app.presentation.theme.*
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import ai.lumo.app.core.security.SecurePreferences
import ai.lumo.app.domain.metrics.MetricSummary
import ai.lumo.app.domain.metrics.PerformanceRecorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onConnectGmail: () -> Unit,
    onDisconnectGmail: () -> Unit,
    onOpenMailbox: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gmailConnectionState by viewModel.gmailConnectionState.collectAsState()
    val llmProvider by viewModel.llmProvider.collectAsState()
    val currentEmail by viewModel.defaultEmail.collectAsState()
    val voiceFeedback by viewModel.voiceFeedback.collectAsState()
    val storageMessage by viewModel.storageMessage.collectAsState()
    val performance by viewModel.performance.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val mailSource by viewModel.mailSource.collectAsState()
    val context = LocalContext.current

    var modelInput by remember { mutableStateOf("") }
    var keyInput by remember { mutableStateOf("") }
    var mem0Input by remember { mutableStateOf("") }
    var emailInput by remember(currentEmail) { mutableStateOf(currentEmail) }
    var keyVisible by remember { mutableStateOf(false) }
    var mem0Visible by remember { mutableStateOf(false) }
    LaunchedEffect(llmProvider) {
        modelInput = viewModel.providerModel(llmProvider)
        keyInput = viewModel.providerKey(llmProvider)
    }
    val mem0Available by viewModel.mem0Available.collectAsState()
    val mem0Saved by viewModel.mem0Key.collectAsState()
    LaunchedEffect(mem0Saved) { mem0Input = mem0Saved }
    val snackbar = remember { SnackbarHostState() }

    // Re-check the account each time Settings opens so a one-off network error does not stick.
    LaunchedEffect(Unit) { viewModel.refreshGmailStatus() }
    LaunchedEffect(storageMessage) { storageMessage?.let { snackbar.showSnackbar(it) } }

    val isConnected = gmailConnectionState.startsWith("Connected as ")
    val hasProblem = gmailConnectionState.startsWith("Could not reach") ||
        gmailConnectionState.contains("expired", ignoreCase = true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Appearance ----
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(
                        Triple("SYSTEM", "System", Icons.Default.PhoneAndroid),
                        Triple("LIGHT", "Light", Icons.Default.LightMode),
                        Triple("DARK", "Dark", Icons.Default.DarkMode)
                    )
                    options.forEachIndexed { index, (value, label, icon) ->
                        SegmentedButton(
                            selected = themeMode == value,
                            onClick = { viewModel.setThemeMode(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        ) { Text(label) }
                    }
                }
            }

            // ---- Mailbox ----
            SettingsSection(title = "Mailbox", icon = Icons.Default.Email) {
                MailboxChooser(
                    source = mailSource,
                    connectionState = gmailConnectionState,
                    onSelect = viewModel::selectMailSource,
                    onSignIn = onConnectGmail,
                    onSignOut = onDisconnectGmail,
                    onRetry = viewModel::refreshGmailStatus,
                    onOpenDemoMailbox = onOpenMailbox
                )
            }

            // ---- AI ----
            SettingsSection(title = "AI model", icon = Icons.Default.AutoAwesome) {
                ModelProviderPicker(
                    providerId = llmProvider,
                    onSelect = viewModel::setLlmProvider,
                    modelInput = modelInput,
                    onModelInput = { modelInput = it },
                    onSaveModel = { viewModel.updateProviderModel(llmProvider, modelInput) },
                    modelDirty = modelInput != viewModel.providerModel(llmProvider),
                    keyInput = keyInput,
                    onKeyInput = { keyInput = it },
                    onSaveKey = { viewModel.updateProviderKey(llmProvider, keyInput) },
                    keyDirty = keyInput != viewModel.providerKey(llmProvider),
                    keyVisible = keyVisible,
                    onToggleKeyVisible = { keyVisible = !keyVisible }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Mem0Row(
                    available = mem0Available,
                    keyInput = mem0Input,
                    onKeyInput = { mem0Input = it },
                    onSaveKey = { viewModel.updateMem0Key(mem0Input) },
                    keyDirty = mem0Input != mem0Saved,
                    keyVisible = mem0Visible,
                    onToggleKeyVisible = { mem0Visible = !mem0Visible }
                )
            }

            // ---- Preferences ----
            SettingsSection(title = "Preferences", icon = Icons.Default.Tune) {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Default recipient email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = { viewModel.updateDefaultEmail(emailInput) },
                    enabled = emailInput != currentEmail
                ) { Text("Save email") }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spoken replies", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Read answers aloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = voiceFeedback, onCheckedChange = { viewModel.setVoiceFeedback(it) })
                }
            }

            // ---- Permissions ----
            SettingsSection(title = "Permissions", icon = Icons.Default.Security) {
                Text(
                    "Microphone is only used for voice commands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open system permissions")
                }
            }

            // ---- Performance ----
            SettingsSection(title = "Performance", icon = Icons.Default.Speed) {
                PerformancePanel(performance, onClear = viewModel::clearPerformanceSamples)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
private fun PerformancePanel(metrics: List<MetricSummary>, onClear: () -> Unit) {
    Text(
        "On-device latency from real use.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (metrics.isEmpty()) {
        Text("No data yet — send a few commands.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("Metric", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2.2f))
        listOf("n", "median", "p95", "ok").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
        }
    }
    metrics.forEach { metric ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(metricLabel(metric.metric), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2.2f))
            Text("${metric.count}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
            Text(formatMs(metric.medianMs), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
            Text(formatMs(metric.p95Ms), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
            Text("${(metric.successRate * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
        }
    }
    TextButton(onClick = onClear) { Text("Reset samples") }
}

private fun metricLabel(metric: String): String = when (metric) {
    PerformanceRecorder.AGENT_TURN -> "Agent turn (end-to-end)"
    PerformanceRecorder.LLM_CALL -> "Gemini call"
    PerformanceRecorder.MEMORY_RETRIEVAL -> "Memory retrieval"
    PerformanceRecorder.SCHEDULED_RUN -> "Scheduled task run"
    else -> metric.removePrefix("tool:").let { "Tool: $it" }
}

private fun formatMs(ms: Long): String =
    if (ms < 1000) "$ms ms" else String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)

/** Provider cards (Gemini, Groq, DeepSeek, Claude) + model + key for the selected one. */
@Composable
private fun ModelProviderPicker(
    providerId: String,
    onSelect: (String) -> Unit,
    modelInput: String,
    onModelInput: (String) -> Unit,
    onSaveModel: () -> Unit,
    modelDirty: Boolean,
    keyInput: String,
    onKeyInput: (String) -> Unit,
    onSaveKey: () -> Unit,
    keyDirty: Boolean,
    keyVisible: Boolean,
    onToggleKeyVisible: () -> Unit
) {
    val context = LocalContext.current
    val selected = ai.lumo.app.data.remote.providerFor(providerId)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ai.lumo.app.data.remote.LLM_PROVIDERS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { provider ->
                    val isSelected = provider.id == selected.id
                    Surface(
                        onClick = { onSelect(provider.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    provider.label.first().toString(),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(provider.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    provider.tagline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        OutlinedTextField(
            value = modelInput,
            onValueChange = onModelInput,
            label = { Text("Model") },
            placeholder = { Text(selected.defaultModel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        if (modelDirty) {
            Button(onClick = onSaveModel) { Text("Save model") }
        }

        OutlinedTextField(
            value = keyInput,
            onValueChange = onKeyInput,
            label = { Text(selected.keyHint) },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleKeyVisible) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (keyVisible) "Hide key" else "Show key"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Stored encrypted on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(selected.keyUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) { Text("Get key") }
        }
        if (keyDirty && keyInput.isNotBlank()) {
            Button(onClick = onSaveKey) { Text("Save key") }
        }
    }
}

/** Smart recall (Mem0): status + key. Memories live on this phone regardless. */
@Composable
private fun Mem0Row(
    available: Boolean,
    keyInput: String,
    onKeyInput: (String) -> Unit,
    onSaveKey: () -> Unit,
    keyDirty: Boolean,
    keyVisible: Boolean,
    onToggleKeyVisible: () -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Smart recall", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(8.dp), color = (if (available) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.14f)) {
                Text(
                    if (available) "On" else "Off",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (available) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            "Mem0 recalls behavior and facts per question — beyond keywords. Memories stay on this phone either way.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = keyInput,
            onValueChange = onKeyInput,
            label = { Text("Mem0 API key (optional)") },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleKeyVisible) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (keyVisible) "Hide key" else "Show key"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Blank = phone-only recall.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://mem0.ai"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) { Text("Get key") }
        }
        if (keyDirty) {
            Button(onClick = onSaveKey) { Text("Save key") }
        }
    }
}
