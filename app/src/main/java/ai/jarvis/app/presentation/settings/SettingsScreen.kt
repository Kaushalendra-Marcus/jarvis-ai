package ai.jarvis.app.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.jarvis.app.presentation.theme.*
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import ai.jarvis.app.core.security.SecurePreferences
import ai.jarvis.app.domain.metrics.MetricSummary
import ai.jarvis.app.domain.metrics.PerformanceRecorder

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
    val accountEmail by viewModel.accountEmail.collectAsState()
    val accountBusy by viewModel.accountBusy.collectAsState()
    val accountError by viewModel.accountError.collectAsState()

    var accountMode by remember { mutableStateOf("login") }
    var accountEmailInput by remember { mutableStateOf("") }
    var accountPasswordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    LaunchedEffect(accountEmail) {
        if (accountEmail.isNotBlank()) {
            accountEmailInput = ""
            accountPasswordInput = ""
        }
    }
    var emailInput by remember(currentEmail) { mutableStateOf(currentEmail) }
    var keyVisible by remember { mutableStateOf(false) }
    LaunchedEffect(llmProvider) {
        modelInput = viewModel.providerModel(llmProvider)
        keyInput = viewModel.providerKey(llmProvider)
    }
    val snackbar = remember { SnackbarHostState() }
    // Accordion: only the tapped section opens.
    var expanded by remember { mutableStateOf<String?>(null) }
    fun toggle(key: String) { expanded = if (expanded == key) null else key }

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
            // ---- Account ----
            SettingsSection(
                title = "Account", icon = Icons.Default.Person,
                expanded = expanded == "account", onExpand = { toggle("account") }
            ) {
                if (accountEmail.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            accountEmail,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "Signed in on this device. Logging out ends this session; your Gmail grant and memories stay on the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = viewModel::logout,
                        enabled = !accountBusy
                    ) { Text(if (accountBusy) "Logging out…" else "Log out") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = accountMode == "login",
                            onClick = { accountMode = "login"; viewModel.clearAccountError() },
                            label = { Text("Log in") }
                        )
                        FilterChip(
                            selected = accountMode == "signup",
                            onClick = { accountMode = "signup"; viewModel.clearAccountError() },
                            label = { Text("Create account") }
                        )
                    }
                    OutlinedTextField(
                        value = accountEmailInput,
                        onValueChange = { accountEmailInput = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = accountPasswordInput,
                        onValueChange = { accountPasswordInput = it },
                        label = { Text("Password (min 8 characters)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    accountError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            if (accountMode == "signup") viewModel.signUp(accountEmailInput, accountPasswordInput)
                            else viewModel.login(accountEmailInput, accountPasswordInput)
                        },
                        enabled = !accountBusy && accountEmailInput.isNotBlank() && accountPasswordInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (accountBusy) "Please wait…" else if (accountMode == "signup") "Create account" else "Log in") }
                    Text(
                        "One account per device. Gmail stays separate under Mailbox — connect it after signing in to keep both on one session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- AI ----
            SettingsSection(
                title = "AI model", icon = Icons.Default.AutoAwesome,
                expanded = expanded == "ai", onExpand = { toggle("ai") }
            ) {
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
            }

            // ---- Appearance ----
            SettingsSection(
                title = "Appearance", icon = Icons.Default.Palette,
                expanded = expanded == "appearance", onExpand = { toggle("appearance") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(Modifier.padding(4.dp)) {
                            ThemeModeIcon(
                                selected = themeMode == "LIGHT",
                                icon = Icons.Default.LightMode,
                                description = "Bright",
                                onClick = { viewModel.setThemeMode("LIGHT") }
                            )
                            ThemeModeIcon(
                                selected = themeMode == "DARK",
                                icon = Icons.Default.DarkMode,
                                description = "Dark",
                                onClick = { viewModel.setThemeMode("DARK") }
                            )
                        }
                    }
                }
            }

            // ---- Mailbox ----
            SettingsSection(
                title = "Mailbox", icon = Icons.Default.Email,
                expanded = expanded == "mailbox", onExpand = { toggle("mailbox") }
            ) {
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

            // ---- Performance ----
            SettingsSection(
                title = "Performance", icon = Icons.Default.Speed,
                expanded = expanded == "perf", onExpand = { toggle("perf") }
            ) {
                PerformancePanel(performance, onClear = viewModel::clearPerformanceSamples)
            }

            // ---- Permissions ----
            SettingsSection(
                title = "Permissions", icon = Icons.Default.Security,
                expanded = expanded == "perms", onExpand = { toggle("perms") }
            ) {
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

            // ---- Preferences ----
            SettingsSection(
                title = "Preferences", icon = Icons.Default.Tune,
                expanded = expanded == "prefs", onExpand = { toggle("prefs") }
            ) {
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

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpand: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(16.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/** Small bright/dark toggle icon. No text, no system mode. */
@Composable
private fun ThemeModeIcon(
    selected: Boolean,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
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
    val selected = ai.jarvis.app.data.remote.providerFor(providerId)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ai.jarvis.app.data.remote.LLM_PROVIDERS.chunked(2).forEach { row ->
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
                            Image(
                                painter = painterResource(provider.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                            )
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
