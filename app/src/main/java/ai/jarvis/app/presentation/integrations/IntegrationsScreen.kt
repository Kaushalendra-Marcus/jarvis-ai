package ai.jarvis.app.presentation.integrations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.jarvis.app.domain.integrations.IntegrationDescriptor
import ai.jarvis.app.domain.integrations.IntegrationRegistry
import ai.jarvis.app.domain.integrations.IntegrationStatus
import ai.jarvis.app.presentation.theme.SuccessGreen
import ai.jarvis.app.presentation.theme.WarningOrange
import kotlinx.coroutines.launch

/**
 * Links — clean catalog UI.
 * Top-10 colorful app grid, a step-by-step connect guide, per-app detail with its
 * data story, and custom MCP/OpenAPI add that persists across restarts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsScreen(
    registry: IntegrationRegistry,
    onConnectGmail: () -> Unit,
    onAuthorizeApp: (String) -> Unit = {},
    // Live backend truth: the registry's gmail flag is only a local toggle, so the
    // real connection state is injected and always wins for the Gmail row.
    isGmailLive: Boolean = false,
    onDisconnectGmail: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<IntegrationDescriptor>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<CatalogApp?>(null) }
    var showGuide by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var mcpUrl by remember { mutableStateOf("") }
    var openApiUrl by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            runCatching { registry.descriptors() }
                .onSuccess { list ->
                    items = list.map { d ->
                        if (d.id == "gmail") {
                            d.copy(
                                status = if (isGmailLive) IntegrationStatus.CONNECTED
                                else IntegrationStatus.DISCONNECTED
                            )
                        } else d
                    }
                }
                .onFailure { error = it.message }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    // Re-evaluate the moment the backend connection flips (e.g. OAuth return).
    LaunchedEffect(isGmailLive) { refresh() }

    val statusOf = { id: String -> items.find { it.id == id }?.status }
    val connectedCount = items.count { it.status == IntegrationStatus.CONNECTED }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Connect", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (connectedCount == 0) "Connect apps so Jarvis can act on them."
                        else "$connectedCount connected — Jarvis can use ${if (connectedCount == 1) "it" else "them"} now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showGuide = true }) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guide")
                }
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Text("Popular", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TOP_APPS.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { app ->
                        AppTile(
                            app = app,
                            status = statusOf(app.id),
                            modifier = Modifier.weight(1f),
                            onClick = { selected = app }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Connected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCustom = !showCustom }) {
                    Text(if (showCustom) "Hide custom" else "+ Add custom")
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { d ->
                    ConnectionRow(
                        d = d,
                        onConnectGmail = onConnectGmail,
                        onDisconnectGmail = { onDisconnectGmail(); refresh() },
                        onRemove = { scope.launch { registry.removeCustom(d.id); refresh() } }
                    )
                }
            }
        }

        if (showCustom) {
            item {
                CustomAddCard(
                    name = customName,
                    onName = { customName = it },
                    mcpUrl = mcpUrl,
                    onMcpUrl = { mcpUrl = it },
                    openApiUrl = openApiUrl,
                    onOpenApiUrl = { openApiUrl = it },
                    onAddMcp = {
                        scope.launch {
                            val title = customName.ifBlank { "Custom MCP" }
                            runCatching { registry.addCustomMcp("custom-${System.currentTimeMillis()}", title, mcpUrl.trim()) }
                                .onSuccess { customName = ""; mcpUrl = ""; refresh() }
                                .onFailure { error = it.message }
                        }
                    },
                    onAddOpenApi = {
                        scope.launch {
                            val title = customName.ifBlank { "Custom API" }
                            runCatching { registry.addCustomOpenApi("custom-${System.currentTimeMillis()}", title, openApiUrl.trim()) }
                                .onSuccess { customName = ""; openApiUrl = ""; refresh() }
                                .onFailure { error = it.message }
                        }
                    }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    selected?.let { app ->
        AppDetailSheet(
            app = app,
            status = statusOf(app.id),
            onDismiss = { selected = null },
            onConnectGmail = { selected = null; onConnectGmail() },
            onDisconnectGmail = { onDisconnectGmail(); refresh(); selected = null },
            onAuthorize = { url -> onAuthorizeApp(url) },
            onConfirmConnected = { serverUrl, token ->
                runCatching { registry.markCatalogConnected(app.id, app.name, serverUrl.trim(), app.authUrl, token.trim()) }
                    .fold(
                        onSuccess = {
                            if (!app.needsToken) return@fold null
                            registry.validateCatalogToken(app.id).fold(
                                onSuccess = { null },
                                onFailure = { v ->
                                    runCatching { registry.removeCustom(app.id) }
                                    v.message ?: "Validation failed."
                                }
                            )
                        },
                        onFailure = { it.message ?: "Could not save the connection." }
                    ).also { if (it == null) refresh() }
            },
            onConfirmed = {
                selected = null
                refresh()
            },
            onDisconnectApp = { scope.launch { registry.removeCustom(app.id); refresh(); selected = null } },
            onOpenGuide = { selected = null; showGuide = true }
        )
    }

    if (showGuide) {
        ConnectGuideSheet(onDismiss = { showGuide = false })
    }
}

@Composable
private fun AppTile(app: CatalogApp, status: IntegrationStatus?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Image(
                painter = painterResource(app.iconRes),
                contentDescription = app.name,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            val dot = when (status) {
                IntegrationStatus.CONNECTED -> SuccessGreen
                IntegrationStatus.UNVERIFIED, IntegrationStatus.ERROR, IntegrationStatus.DISCONNECTED ->
                    if (app.kind == CatalogKind.CUSTOM_SOON) null else WarningOrange
                null -> null
            }
            if (dot != null) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .size(12.dp)
                        .background(dot, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            app.name, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 1
        )
    }
}

@Composable
private fun ConnectionRow(
    d: IntegrationDescriptor,
    onConnectGmail: () -> Unit,
    onDisconnectGmail: () -> Unit,
    onRemove: () -> Unit
) {
    val app = catalogFor(d.id)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app != null) {
                Image(
                    painter = painterResource(app.iconRes), contentDescription = null,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    StatusPill(d.status)
                }
                if (d.status == IntegrationStatus.UNVERIFIED && !d.isBuiltIn) {
                    Text(
                        "Saved on this phone. Tools unlock after validation.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                d.id == "gmail" && d.status == IntegrationStatus.CONNECTED ->
                    TextButton(onClick = onDisconnectGmail) { Text("Disconnect") }
                d.id == "gmail" ->
                    Button(onClick = onConnectGmail) { Text("Connect") }
                !d.isBuiltIn ->
                    TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun StatusPill(status: IntegrationStatus) {
    val (label, color) = when (status) {
        IntegrationStatus.CONNECTED -> "Live" to SuccessGreen
        IntegrationStatus.DISCONNECTED -> "Off" to MaterialTheme.colorScheme.onSurfaceVariant
        IntegrationStatus.UNVERIFIED -> "Beta" to WarningOrange
        IntegrationStatus.ERROR -> "Error" to MaterialTheme.colorScheme.error
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = color, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailSheet(
    app: CatalogApp,
    status: IntegrationStatus?,
    onDismiss: () -> Unit,
    onConnectGmail: () -> Unit,
    onDisconnectGmail: () -> Unit,
    onAuthorize: (String) -> Unit,
    onConfirmConnected: suspend (serverUrl: String, token: String) -> String?,
    onConfirmed: () -> Unit,
    onDisconnectApp: () -> Unit,
    onOpenGuide: () -> Unit
) {
    var serverUrl by remember(app.id) { mutableStateOf(app.serverHint) }
    var apiToken by remember(app.id) { mutableStateOf("") }
    var checking by remember(app.id) { mutableStateOf(false) }
    var tokenError by remember(app.id) { mutableStateOf<String?>(null) }
    val sheetScope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(app.iconRes), contentDescription = null,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                if (status != null) StatusPill(status)
                else if (app.kind == CatalogKind.CUSTOM_SOON) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("Soon", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                app.dataUses, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            when (app.kind) {
                CatalogKind.GMAIL -> if (status == IntegrationStatus.CONNECTED) {
                    OutlinedButton(onClick = onDisconnectGmail, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
                } else {
                    Button(onClick = onConnectGmail, modifier = Modifier.fillMaxWidth()) { Text("Connect Gmail") }
                }
                CatalogKind.CALENDAR -> {
                    Text(
                        "Native support lands with the Calendar OAuth scope.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) { Text("How connecting works") }
                }
                CatalogKind.CUSTOM_SOON -> {
                    if (status == IntegrationStatus.CONNECTED) {
                        Text(
                            "Authorized in Jarvis — Jarvis can use it now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onDisconnectApp, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
                    } else {
                        if (app.needsToken) {
                            Text(
                                "Browser login alone grants nothing — Notion's API needs its own token.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningOrange,
                                textAlign = TextAlign.Center
                            )
                        }
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Server URL (optional) — https://…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = apiToken,
                            onValueChange = { apiToken = it; tokenError = null },
                            label = { Text("API token (for apps like Notion) — secret_…") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        tokenError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        }
                        Button(
                            onClick = { onAuthorize(app.authUrl.ifBlank { serverUrl.trim() }) },
                            enabled = !checking && (app.authUrl.isNotBlank() || serverUrl.trim().startsWith("https://")),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Authorize ${app.name}") }
                        Text(
                            if (app.needsToken) "1 Authorize opens Integrations  2 New integration  3 Paste the token above, then confirm."
                            else "Opens ${app.name}'s authorization page here in Jarvis. Sign in, come back, then confirm below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(
                            onClick = {
                                if (app.needsToken && apiToken.trim().isBlank()) {
                                    tokenError = "Paste the token — browser login alone doesn't grant access."
                                } else {
                                    sheetScope.launch {
                                        checking = true
                                        tokenError = null
                                        tokenError = onConfirmConnected(serverUrl, apiToken)
                                        checking = false
                                        if (tokenError == null) onConfirmed()
                                    }
                                }
                            },
                            enabled = !checking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (checking) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("I've authorized → Connect")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CustomAddCard(
    name: String, onName: (String) -> Unit,
    mcpUrl: String, onMcpUrl: (String) -> Unit,
    openApiUrl: String, onOpenApiUrl: (String) -> Unit,
    onAddMcp: () -> Unit, onAddOpenApi: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add custom", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Saved on this phone and restored on restart.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name, onValueChange = onName, label = { Text("Name (e.g. Notes MCP)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = mcpUrl, onValueChange = onMcpUrl, label = { Text("MCP server URL — https://…") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Button(onClick = onAddMcp, enabled = mcpUrl.startsWith("https://"), modifier = Modifier.fillMaxWidth()) { Text("Add MCP") }
            OutlinedTextField(
                value = openApiUrl, onValueChange = onOpenApiUrl, label = { Text("OpenAPI spec URL — https://…/openapi.json") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Button(onClick = onAddOpenApi, enabled = openApiUrl.startsWith("https://"), modifier = Modifier.fillMaxWidth()) { Text("Add API") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectGuideSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("How connecting works", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Any app follows the same four steps.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // Little flow visual: App ⋯⋯ Key ⋯⋯ ✓ ⋯⋯ Jarvis
            FlowVisual()
            Spacer(Modifier.height(12.dp))
            GuideStep(1, "Pick an app", "Tap its icon above. Live apps connect now; the rest set up guided.")
            GuideStep(2, "Approve access", "Sign in with Google or paste the server URL. Jarvis never sees passwords.")
            GuideStep(3, "Confirm what it can touch", "Each card says exactly which data Jarvis may read or write.")
            GuideStep(4, "Ask Jarvis to use it", "“Summarize my inbox” or “post this to Slack”. Sending always asks first.")
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FlowVisual() {
    val primary = MaterialTheme.colorScheme.primary
    val nodes = listOf("App", "Key", "✓", "Jarvis")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        nodes.forEachIndexed { i, label ->
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(primary.copy(alpha = if (i == 3) 1f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (i == 2) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
                } else {
                    Text(
                        label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (i == 3) MaterialTheme.colorScheme.onPrimary else primary
                    )
                }
            }
            if (i < nodes.size - 1) {
                Canvas(Modifier.weight(1f).height(2.dp)) {
                    drawLine(
                        color = primary,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f))
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, title: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (number == 4) Icons.Default.AutoAwesome else Icons.Default.Check,
            contentDescription = null,
            tint = if (number == 4) MaterialTheme.colorScheme.primary else SuccessGreen,
            modifier = Modifier.size(18.dp).align(Alignment.CenterVertically)
        )
    }
    if (number < 4) {
        Canvas(Modifier.height(10.dp).width(26.dp)) {
            drawLine(
                color = Color.Gray,
                start = androidx.compose.ui.geometry.Offset(size.width / 2, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width / 2, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f))
            )
        }
    }
}
