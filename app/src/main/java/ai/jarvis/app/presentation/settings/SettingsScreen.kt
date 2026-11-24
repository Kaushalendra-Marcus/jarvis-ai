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
    val taskNotifications by viewModel.taskNotifications.collectAsState()
    val taskAlarmSound by viewModel.taskAlarmSound.collectAsState()
    val taskVibrate by viewModel.taskVibrate.collectAsState()
    var route by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var confirmSignOutEverywhere by remember { mutableStateOf(false) }

    val destinations = remember(accountEmail, llmProvider, themeMode) {
        listOf(
            SettingsDestination(
                "account", "Account",
                if (accountEmail.isNotBlank()) accountEmail else "Log in or create an account",
                Icons.Default.Person, "login logout signup sign out email password account"
            ),
            SettingsDestination(
                "mailbox", "Mailbox",
                if (mailSource == SecurePreferences.SOURCE_DEMO) "Demo mailbox" else gmailConnectionState,
                Icons.Default.Email, "gmail demo inbox mail sign in google"
            ),
            SettingsDestination(
                "ai", "AI model", llmProvider.replaceFirstChar { it.uppercase() },
                Icons.Default.AutoAwesome, "model gemini groq deepseek claude api key"
            ),
            SettingsDestination(
                "notifications", "Notifications",
                if (taskNotifications) "Routine alarms on" else "Routine alarms off",
                Icons.Default.Notifications, "alarm sound vibrate notify routine task"
            ),
            SettingsDestination(
                "appearance", "Appearance", themeMode.lowercase().replaceFirstChar { it.uppercase() },
                Icons.Default.Palette, "theme dark light display"
            ),
            SettingsDestination(
                "preferences", "Preferences", "Default email",
                Icons.Default.Tune, "email default voice"
            ),
            SettingsDestination(
                "performance", "Performance", "On-device latency stats",
                Icons.Default.Speed, "stats metrics latency"
            ),
            SettingsDestination(
                "permissions", "Permissions", "Microphone",
                Icons.Default.Security, "permission mic microphone"
            ),
            SettingsDestination(
                "about", "About", "Version and backend",
                Icons.Default.Info, "version backend about"
            )
        )
    }
    val groups = remember(destinations) {
        listOf(
            "General" to listOf("account", "mailbox", "ai", "notifications"),
            "Personalize" to listOf("appearance", "preferences", "performance"),
            "Support" to listOf("permissions", "about")
        ).map { (header, ids) -> header to destinations.filter { it.id in ids } }
    }
    val filtered = remember(query, destinations) {
        val q = query.trim().lowercase()
        if (q.isBlank()) emptyList()
        else destinations.filter {
            it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q) || it.keywords.contains(q)
        }
    }

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
        ) {
            if (route == null) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search settings") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp)
                ) {
                    if (query.isBlank()) {
                        groups.forEach { (header, rows) ->
                            Text(
                                header,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                            rows.forEach { dest ->
                                SettingsRow(
                                    title = dest.title,
                                    subtitle = dest.subtitle,
                                    icon = dest.icon,
                                    onClick = { route = dest.id }
                                )
                            }
                        }
                    } else if (filtered.isEmpty()) {
                        Text(
                            "No settings match \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    } else {
                        filtered.forEach { dest ->
                            SettingsRow(
                                title = dest.title,
                                subtitle = dest.subtitle,
                                icon = dest.icon,
                                onClick = { route = dest.id }
                            )
                        }
                    }
                }
            } else {
                val current = destinations.firstOrNull { it.id == route }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = { route = null; query = "" }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to settings")
                    }
                    Text(
                        current?.title ?: "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (route) {
                        "account" -> AccountBody(
                            accountEmail = accountEmail,
                            accountBusy = accountBusy,
                            accountError = accountError,
                            accountMode = accountMode,
                            onModeChange = { accountMode = it; viewModel.clearAccountError() },
                            accountEmailInput = accountEmailInput,
                            onEmailInput = { accountEmailInput = it },
                            accountPasswordInput = accountPasswordInput,
                            onPasswordInput = { accountPasswordInput = it },
                            passwordVisible = passwordVisible,
                            onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                            onSubmit = {
                                if (accountMode == "signup") viewModel.signUp(accountEmailInput, accountPasswordInput)
                                else viewModel.login(accountEmailInput, accountPasswordInput)
                            },
                            onLogout = viewModel::logout,
                            onSignOutEverywhere = { confirmSignOutEverywhere = true }
                        )
                        "mailbox" -> MailboxChooser(
                            source = mailSource,
                            connectionState = gmailConnectionState,
                            onSelect = viewModel::selectMailSource,
                            onSignIn = onConnectGmail,
                            onSignOut = onDisconnectGmail,
                            onRetry = viewModel::refreshGmailStatus,
                            onOpenDemoMailbox = onOpenMailbox
                        )
                        "ai" -> ModelProviderPicker(
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
                        "notifications" -> {
                            SwitchRow(
                                title = "Routine notifications",
                                subtitle = "Sound an alarm when scheduled routines finish.",
                                checked = taskNotifications,
                                onCheckedChange = viewModel::setTaskNotifications
                            )
                            SwitchRow(
                                title = "Alarm sound",
                                subtitle = "Play the notification sound with the alarm.",
                                checked = taskAlarmSound,
                                onCheckedChange = viewModel::setTaskAlarmSound,
                                enabled = taskNotifications
                            )
                            SwitchRow(
                                title = "Vibrate",
                                subtitle = "Vibrate with the routine alarm.",
                                checked = taskVibrate,
                                onCheckedChange = viewModel::setTaskVibrate,
                                enabled = taskNotifications
                            )
                            SwitchRow(
                                title = "Spoken replies",
                                subtitle = "Read answers aloud.",
                                checked = voiceFeedback,
                                onCheckedChange = viewModel::setVoiceFeedback
                            )
                        }
                        "appearance" -> Row(
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
                        "preferences" -> {
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
                        }
                        "performance" -> PerformancePanel(performance, onClear = viewModel::clearPerformanceSamples)
                        "permissions" -> {
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
                        "about" -> {
                            AboutRow(label = "Version", value = ai.jarvis.app.BuildConfig.VERSION_NAME)
                            AboutRow(label = "Backend", value = ai.jarvis.app.BuildConfig.API_BASE_URL)
                            Text(
                                "Jarvis runs on-device with an optional backend for Gmail. Secrets stay in encrypted storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        if (confirmSignOutEverywhere) {
            AlertDialog(
                onDismissRequest = { confirmSignOutEverywhere = false },
                title = { Text("Sign out everywhere?", fontWeight = FontWeight.Bold) },
                text = { Text("Ends your Jarvis sessions on every device. You will stay signed in nowhere until you log in again.") },
                confirmButton = {
                    Button(onClick = {
                        confirmSignOutEverywhere = false
                        viewModel.signOutEverywhere()
                    }) { Text("Sign out everywhere") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmSignOutEverywhere = false }) { Text("Cancel") }
                }
            )
        }
    }
}

/** X-style settings navigation: icon + title + subtitle + chevron. */
private data class SettingsDestination(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val keywords: String
)

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AccountBody(
    accountEmail: String,
    accountBusy: Boolean,
    accountError: String?,
    accountMode: String,
    onModeChange: (String) -> Unit,
    accountEmailInput: String,
    onEmailInput: (String) -> Unit,
    accountPasswordInput: String,
    onPasswordInput: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    onSubmit: () -> Unit,
    onLogout: () -> Unit,
    onSignOutEverywhere: () -> Unit
) {
    if (accountEmail.isNotBlank()) {
        Text(
            accountEmail,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Signed in on this device. Logging out ends this session; your Gmail grant and memories stay on the server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLogout, enabled = !accountBusy) {
                Text(if (accountBusy) "Logging out…" else "Log out")
            }
            OutlinedButton(onClick = onSignOutEverywhere, enabled = !accountBusy) {
                Text("Sign out everywhere")
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = accountMode == "login",
                onClick = { onModeChange("login") },
                label = { Text("Log in") }
            )
            FilterChip(
                selected = accountMode == "signup",
                onClick = { onModeChange("signup") },
                label = { Text("Create account") }
            )
        }
        OutlinedTextField(
            value = accountEmailInput,
            onValueChange = onEmailInput,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = accountPasswordInput,
            onValueChange = onPasswordInput,
            label = { Text("Password (min 8 characters)") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisible) {
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
            onClick = onSubmit,
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
