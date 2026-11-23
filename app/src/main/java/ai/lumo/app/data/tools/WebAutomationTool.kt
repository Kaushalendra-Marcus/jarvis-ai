package ai.lumo.app.data.tools

import ai.lumo.app.domain.model.ToolExecutionResponse
import ai.lumo.app.domain.tool.AgentTool
import ai.lumo.app.service.web.ControlledWebScraper

class WebAutomationTool(
    private val webScraper: ControlledWebScraper
) : AgentTool {

    override val name: String = "browse_web"
    override val description: String = "Browse a website, navigate pages, and extract clean text or headlines."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val url = arguments["url"] as? String
            ?: return ToolExecutionResponse(name, false, "Missing 'url' parameter.")
        val action = arguments["action"] as? String ?: "extract_summary"

        val extracted = webScraper.browseAndExtract(url, action)
        return ToolExecutionResponse(
            toolName = name,
            isSuccess = !extracted.startsWith("Error:"),
            output = extracted
        )
    }
}
