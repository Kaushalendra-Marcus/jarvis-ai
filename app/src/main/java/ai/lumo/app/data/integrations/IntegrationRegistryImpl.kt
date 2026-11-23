package ai.lumo.app.data.integrations

import ai.lumo.app.domain.integrations.IntegrationDescriptor
import ai.lumo.app.domain.integrations.IntegrationRegistry
import ai.lumo.app.domain.integrations.IntegrationStatus
import ai.lumo.app.domain.tool.AgentTool
import java.util.concurrent.ConcurrentHashMap

/**
 * Default registry: built-in gmail tools are injected, calendar is a placeholder
 * (UNVERIFIED until OAuth scopes land), custom MCP/OpenAPI entries are restored from
 * [CustomIntegrationStore] and saved on every add/remove, so user data survives restarts.
 * No hardcoding in AgentOrchestrator — it only sees [allTools].
 */
class IntegrationRegistryImpl(
    builtInTools: List<AgentTool>,
    private val gmailToolNames: Set<String> = setOf("search_emails", "read_email", "create_email_draft", "send_email"),
    private val store: CustomIntegrationStore? = null
) : IntegrationRegistry {

    private val builtIns = builtInTools.toList()
    private val custom = ConcurrentHashMap<String, IntegrationDescriptor>()
    private val customTools = ConcurrentHashMap<String, List<AgentTool>>()
    private var gmailConnected: Boolean = true

    init {
        // Restore saved customs (UNVERIFIED until re-validated on first use).
        store?.load().orEmpty().forEach { saved ->
            val slug = "${saved.type}:${saved.id}"
            val description = if (saved.type == "mcp") {
                "Custom MCP at ${saved.url}. Marked UNVERIFIED until tool list validates."
            } else {
                "Custom OpenAPI at ${saved.url}. Marked UNVERIFIED until schema validates."
            }
            custom[slug] = IntegrationDescriptor(
                slug, saved.title, description,
                IntegrationStatus.UNVERIFIED, isBuiltIn = false, configUrl = saved.url
            )
            customTools[slug] = if (saved.type == "mcp") {
                listOf(RemoteMcpTool(slug, saved.url))
            } else {
                listOf(RemoteOpenApiTool(slug, saved.url))
            }
        }
    }

    private fun persist() {
        val s = store ?: return
        s.save(
            custom.values.sortedBy { it.id }.map {
                val type = if (it.id.startsWith("mcp:")) "mcp" else "openapi"
                CustomIntegrationStore.SavedCustom(
                    id = it.id.substringAfter(":"),
                    title = it.title,
                    type = type,
                    url = it.configUrl.orEmpty()
                )
            }
        )
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
        list += custom.values.sortedBy { it.id }
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
        val d = IntegrationDescriptor(slug, title, "Custom MCP at $serverUrl. Marked UNVERIFIED until tool list validates.", IntegrationStatus.UNVERIFIED, isBuiltIn = false, configUrl = serverUrl)
        custom[slug] = d
        // Dynamic discovery happens on first use via RemoteMcpTool (schema fetch + validate).
        customTools[slug] = listOf(RemoteMcpTool(slug, serverUrl))
        persist()
        d
    }

    override suspend fun addCustomOpenApi(id: String, title: String, specUrl: String): Result<IntegrationDescriptor> = runCatching {
        require(specUrl.startsWith("https://")) { "OpenAPI spec URL must be https:// (got $specUrl)" }
        val slug = "openapi:$id"
        val d = IntegrationDescriptor(slug, title, "Custom OpenAPI at $specUrl. Marked UNVERIFIED until schema validates.", IntegrationStatus.UNVERIFIED, isBuiltIn = false, configUrl = specUrl)
        custom[slug] = d
        customTools[slug] = listOf(RemoteOpenApiTool(slug, specUrl))
        persist()
        d
    }

    override suspend fun removeCustom(id: String): Result<Unit> = runCatching {
        custom.remove(id) ?: throw IllegalArgumentException("Custom integration not found: $id")
        customTools.remove(id)
        persist()
    }
}
