package ai.lumo.app.domain.integrations

import ai.lumo.app.domain.tool.AgentTool

/** Verification level shown in UI. Custom MCP/OpenAPI entries stay UNVERIFIED until validated. */
enum class IntegrationStatus { CONNECTED, DISCONNECTED, UNVERIFIED, ERROR }

data class IntegrationDescriptor(
    val id: String, // e.g. "gmail", "google-calendar", "mcp:notes"
    val title: String,
    val description: String,
    val status: IntegrationStatus,
    val isBuiltIn: Boolean,
    val configUrl: String? = null // MCP server URL or OpenAPI URL for custom entries
)

/**
 * Integrations platform seam.
 * Built-ins (gmail, calendar) ship as Adapters. Custom MCP/OpenAPI integrations are
 * discovered dynamically and exposed as tools without hardcoding into AgentOrchestrator.
 */
interface IntegrationRegistry {
    suspend fun descriptors(): List<IntegrationDescriptor>
    suspend fun toolsFor(integrationId: String): List<AgentTool>
    suspend fun allTools(): List<AgentTool>
    suspend fun connect(integrationId: String): Result<Unit>
    suspend fun disconnect(integrationId: String): Result<Unit>
    suspend fun addCustomMcp(id: String, title: String, serverUrl: String): Result<IntegrationDescriptor>
    suspend fun addCustomOpenApi(id: String, title: String, specUrl: String): Result<IntegrationDescriptor>
    suspend fun removeCustom(id: String): Result<Unit>
}
