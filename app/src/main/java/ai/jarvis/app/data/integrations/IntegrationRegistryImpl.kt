package ai.jarvis.app.data.integrations

import ai.jarvis.app.domain.integrations.IntegrationDescriptor
import ai.jarvis.app.domain.integrations.IntegrationRegistry
import ai.jarvis.app.domain.integrations.IntegrationStatus
import ai.jarvis.app.domain.tool.AgentTool
import ai.jarvis.app.data.tools.NotionTool
import java.util.concurrent.ConcurrentHashMap

/**
 * Default registry: built-in gmail tools are injected, calendar is a placeholder
 * (UNVERIFIED until OAuth scopes land). Custom MCP/OpenAPI entries and catalog
 * connections completed through the in-app authorization loop are restored from
 * [CustomIntegrationStore] and saved on every change, so user data survives restarts.
 * No hardcoding in AgentOrchestrator — it only sees [allTools].
 */
class IntegrationRegistryImpl(
    builtInTools: List<AgentTool>,
    private val gmailToolNames: Set<String> = setOf("search_emails", "read_email", "create_email_draft", "send_email"),
    private val store: CustomIntegrationStore? = null
) : IntegrationRegistry {

    private val builtIns = builtInTools.toList()
    private val saved = ConcurrentHashMap<String, CustomIntegrationStore.SavedCustom>()
    private val customTools = ConcurrentHashMap<String, List<AgentTool>>()
    private var gmailConnected: Boolean = true

    init {
        // Restore saved customs: catalog connections come back CONNECTED,
        // MCP/OpenAPI entries come back UNVERIFIED until re-validated on first use.
        store?.load().orEmpty().forEach { entry ->
            saved[entry.id] = entry
            customTools[entry.id] = toolsForEntry(entry)
        }
    }

    private fun toolsForEntry(entry: CustomIntegrationStore.SavedCustom): List<AgentTool> {
        if (entry.url.isBlank()) return emptyList()
        val slug = "${entry.type}:${entry.id}"
        return when (entry.type) {
            "openapi" -> listOf(RemoteOpenApiTool(slug, entry.url))
            else -> listOf(RemoteMcpTool(slug, entry.url))
        }
    }

    private fun describe(entry: CustomIntegrationStore.SavedCustom): IntegrationDescriptor {
        val status = if (entry.connected) IntegrationStatus.CONNECTED else IntegrationStatus.UNVERIFIED
        val description = when (entry.type) {
            "catalog" -> if (entry.url.isBlank()) {
                "Authorized in Jarvis. Add its server URL below to unlock tools."
            } else {
                "Authorized in Jarvis at ${entry.url}."
            }
            "mcp" -> "Custom MCP at ${entry.url}. Marked UNVERIFIED until tool list validates."
            else -> "Custom OpenAPI at ${entry.url}. Marked UNVERIFIED until schema validates."
        }
        return IntegrationDescriptor(
            entry.id, entry.title, description,
            status, isBuiltIn = false, configUrl = entry.url.ifBlank { null }
        )
    }

    private fun persist() {
        store?.save(saved.values.sortedBy { it.id })
    }

    override suspend fun descriptors(): List<IntegrationDescriptor> {
        val list = mutableListOf(
            IntegrationDescriptor(
                id = "gmail",
                title = "Gmail",
                description = "Search, read, draft and send via Jarvis backend (OAuth, no passwords on device).",
                status = if (gmailConnected) IntegrationStatus.CONNECTED else IntegrationStatus.DISCONNECTED,
                isBuiltIn = true
            ),
            IntegrationDescriptor(
                id = "google-calendar",
                title = "Google Calendar",
                description = "Beta — list and create events. Requires Calendar OAuth scope.",
                status = IntegrationStatus.UNVERIFIED,
                isBuiltIn = true
            )
        )
        list += saved.values.sortedBy { it.id }.map(::describe)
        return list
    }

    override suspend fun toolsFor(integrationId: String): List<AgentTool> = when (integrationId) {
        "gmail" -> builtIns.filter { it.name in gmailToolNames }
        "google-calendar" -> emptyList() // Calendar adapter lands when OAuth scope is approved.
        else -> customTools[integrationId].orEmpty()
    }

    override suspend fun allTools(): List<AgentTool> {
        val gmail = toolsFor("gmail")
        val otherBuiltIns = builtIns.filter { it.name !in gmailToolNames }
        return gmail + otherBuiltIns + customTools.values.flatten()
    }

    fun setGmailConnected(connected: Boolean) { gmailConnected = connected }

    override suspend fun connect(integrationId: String): Result<Unit> = runCatching {
        when (integrationId) {
            "gmail" -> gmailConnected = true
            "google-calendar" -> throw IllegalStateException("Google Calendar is not yet available in this build.")
            else -> throw IllegalArgumentException("Unknown integration: $integrationId")
        }
    }

    override suspend fun disconnect(integrationId: String): Result<Unit> = runCatching {
        if (integrationId == "gmail") gmailConnected = false
        else throw IllegalArgumentException("Unknown integration: $integrationId")
    }

    override suspend fun addCustomMcp(id: String, title: String, serverUrl: String): Result<IntegrationDescriptor> = runCatching {
        require(serverUrl.startsWith("https://")) { "MCP server URL must be https:// (got $serverUrl)" }
        val slug = "mcp:$id"
        val entry = CustomIntegrationStore.SavedCustom(slug, title, "mcp", serverUrl)
        saved[slug] = entry
        // Dynamic discovery happens on first use via RemoteMcpTool (schema fetch + validate).
        customTools[slug] = toolsForEntry(entry)
        persist()
        describe(entry)
    }

    override suspend fun addCustomOpenApi(id: String, title: String, specUrl: String): Result<IntegrationDescriptor> = runCatching {
        require(specUrl.startsWith("https://")) { "OpenAPI spec URL must be https:// (got $specUrl)" }
        val slug = "openapi:$id"
        val entry = CustomIntegrationStore.SavedCustom(slug, title, "openapi", specUrl)
        saved[slug] = entry
        customTools[slug] = toolsForEntry(entry)
        persist()
        describe(entry)
    }

    /**
     * Completes the one-tap loop: after the user authorizes on the vendor page,
     * the catalog app is stored CONNECTED (tools attach when a server URL is set).
     */
    override suspend fun markCatalogConnected(
        appId: String,
        title: String,
        serverUrl: String,
        authUrl: String,
        token: String
    ): Result<IntegrationDescriptor> = runCatching {
        if (serverUrl.isNotBlank()) {
            require(serverUrl.startsWith("https://")) { "Server URL must be https://" }
        }
        val entry = CustomIntegrationStore.SavedCustom(
            id = appId, title = title, type = "catalog",
            url = serverUrl.trim(), connected = true, authUrl = authUrl,
            token = token.trim()
        )
        saved[appId] = entry
        customTools[appId] = toolsForEntry(entry)
        persist()
        describe(entry)
    }

    override suspend fun catalogToken(appId: String): String =
        saved[appId]?.token.orEmpty()

    override suspend fun updateCatalogToken(appId: String, token: String): Result<Unit> = runCatching {
        val current = saved[appId] ?: throw IllegalStateException("Connect ${appId} first, then paste its token.")
        val clean = token.trim()
        require(clean.startsWith("secret_") || clean.startsWith("ntn_")) {
            "That token looks wrong — paste the Internal Integration Token (starts with secret_)."
        }
        saved[appId] = current.copy(token = clean)
        persist()
    }

    override suspend fun validateCatalogToken(appId: String): Result<String> = runCatching {
        val token = saved[appId]?.token.orEmpty()
        require(token.isNotBlank()) { "No token saved for $appId yet." }
        when (appId) {
            "notion" -> {
                val probe = NotionTool(tokenProvider = { token }).probe(token)
                val visible = probe.getOrThrow()
                if (visible > 0) "Token works — your workspace pages are visible."
                else "Token works, but no pages are shared with the integration yet. Share a page with it in Notion."
            }
            else -> throw IllegalStateException("No validator for $appId.")
        }
    }

    override suspend fun removeCustom(id: String): Result<Unit> = runCatching {
        saved.remove(id) ?: throw IllegalArgumentException("Custom integration not found: $id")
        customTools.remove(id)
        persist()
    }
}
