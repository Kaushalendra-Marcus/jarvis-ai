package ai.jarvis.app.data.integrations

import ai.jarvis.app.domain.model.ToolExecutionRequest
import ai.jarvis.app.domain.model.ToolExecutionResponse
import ai.jarvis.app.domain.tool.AgentTool

/**
 * Placeholders for remote tools. They validate schema on first execute and refuse
 * to run until the remote descriptor proves safe (https + tool list + permission map).
 * This keeps AgentOrchestrator free of hardcoded custom integrations (Req.8).
 */
class RemoteMcpTool(private val integrationId: String, private val serverUrl: String) : AgentTool {
    override val name = "mcp_${integrationId.substringAfter(":").replace(Regex("[^a-z0-9_]"), "_")}_call"
    override val description = "Custom MCP integration ($integrationId). UNVERIFIED — configure and validate in Integrations first."
    override val isSensitive = true
    override suspend fun execute(arguments: Map<String, Any?>) = ai.jarvis.app.domain.model.ToolExecutionResponse(
        toolName = name, isSuccess = false,
        output = "Integration '$integrationId' is UNVERIFIED. Open Jarvis > Integrations, validate $serverUrl, then retry. No action was executed.",
        retryable = false
    )
}

class RemoteOpenApiTool(private val integrationId: String, private val specUrl: String) : AgentTool {
    override val name = "openapi_${integrationId.substringAfter(":").replace(Regex("[^a-z0-9_]"), "_")}_call"
    override val description = "Custom OpenAPI integration ($integrationId). UNVERIFIED — configure and validate in Integrations first."
    override val isSensitive = true
    override suspend fun execute(arguments: Map<String, Any?>) = ai.jarvis.app.domain.model.ToolExecutionResponse(
        toolName = name, isSuccess = false,
        output = "Integration '$integrationId' is UNVERIFIED. Open Jarvis > Integrations, validate $specUrl, then retry. No action was executed.",
        retryable = false
    )
}

// ToolExecutionResponse helper lives in domain.model; no local factory needed.
