package ai.lumo.app.data.remote

import ai.lumo.app.core.security.SecurePreferences
import ai.lumo.app.domain.model.ToolExecutionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Provider-aware agent client. Gemini keeps its native function-calling path
 * ([GeminiAgentClient]); Groq/DeepSeek go through the OpenAI chat-completions
 * dialect and Claude through the Anthropic messages dialect. Every dialect is
 * translated back into [GeminiAgentResponse], so the orchestrator never changes.
 */
class ProviderAgentClient(
    securePreferences: SecurePreferences? = null,
    private val http: OkHttpClient = defaultHttpClient()
) : GeminiAgentClient(securePreferences) {

    override suspend fun generateAgentTurn(
        systemInstruction: String,
        conversationHistory: List<GeminiConversationTurn>,
        toolsPrompt: String
    ): GeminiAgentResponse = withContext(Dispatchers.IO) {
        val prefs = securePreferences
        val provider = providerFor(prefs?.getLlmProvider())
        if (provider.apiStyle == ProviderApiStyle.GEMINI) {
            return@withContext super.generateAgentTurn(systemInstruction, conversationHistory, toolsPrompt)
        }
        val apiKey = prefs?.getProviderApiKey(provider.id).orEmpty().trim()
        if (apiKey.isBlank()) {
            return@withContext GeminiAgentResponse.Error(
                message = "${provider.label} API key is not set. Add it in Settings → AI model.",
                kind = GeminiErrorKind.MISSING_API_KEY,
                retryable = false
            )
        }
        val model = prefs?.getProviderModel(provider.id)?.trim()
            .takeIf { !it.isNullOrBlank() } ?: provider.defaultModel
        val system = "$systemInstruction\n\n$toolsPrompt"
        return@withContext when (provider.apiStyle) {
            ProviderApiStyle.OPENAI_COMPATIBLE ->
                chatCompletions(provider, apiKey, model, system, conversationHistory)
            ProviderApiStyle.ANTHROPIC ->
                anthropicMessages(provider, apiKey, model, system, conversationHistory)
            else -> super.generateAgentTurn(systemInstruction, conversationHistory, toolsPrompt)
        }
    }

    // ---- OpenAI-compatible (Groq, DeepSeek) ----

    private fun chatCompletions(
        provider: LlmProvider,
        apiKey: String,
        model: String,
        system: String,
        history: List<GeminiConversationTurn>
    ): GeminiAgentResponse {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        var callSeq = 0
        history.forEach { turn ->
            turn.text?.let { messages.put(JSONObject().put("role", turn.role).put("content", it)) }
            turn.functionCall?.let { call ->
                val callId = call.id?.takeIf(String::isNotBlank) ?: "call-${++callSeq}"
                messages.put(
                    JSONObject().put("role", "assistant").put(
                        "tool_calls",
                        JSONArray().put(
                            JSONObject().put("id", callId).put("type", "function").put(
                                "function",
                                JSONObject().put("name", call.toolName)
                                    .put("arguments", JSONObject(call.arguments).toString())
                            )
                        )
                    )
                )
            }
            turn.functionResponse?.let { result ->
                val matchId = (history.firstOrNull { it.functionCall?.toolName == result.name }?.functionCall?.id)
                    ?.takeIf { !it.isNullOrBlank() } ?: "call-$callSeq"
                messages.put(
                    JSONObject().put("role", "tool").put("tool_call_id", matchId)
                        .put("content", result.result)
                )
            }
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("tools", openAiTools())
            .put("temperature", 0.3)
            .put("max_tokens", 2048)
        val (code, raw) = try {
            postJson(
                url = "${provider.baseUrl.trimEnd('/')}/chat/completions",
                headers = mapOf("Authorization" to "Bearer $apiKey"),
                body = body.toString()
            )
        } catch (t: TimeoutMarker) {
            return networkError("The ${provider.label} request timed out.", GeminiErrorKind.TIMEOUT)
        } catch (o: OfflineMarker) {
            return networkError("Could not reach ${provider.label}. Check your internet connection.", GeminiErrorKind.NO_NETWORK)
        }
        if (code !in 200..299) return httpError(provider, code, raw)
        return try {
            val message = JSONObject(raw)
                .optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: return invalidReply(provider, "returned no choices")
            val text = message.optString("content").trim()
            val call = message.optJSONArray("tool_calls")?.optJSONObject(0)
            val fn = call?.optJSONObject("function")
            val toolCall = if (fn != null) {
                val name = fn.optString("name").takeIf(String::isNotBlank)
                    ?: return invalidReply(provider, "returned a tool call without a name")
                ToolExecutionRequest(
                    toolName = name,
                    arguments = jsonToMap(runCatching { JSONObject(fn.optString("arguments")) }.getOrNull() ?: JSONObject()),
                    id = call.optString("id").takeIf(String::isNotBlank)
                )
            } else null
            if (text.isBlank() && toolCall == null) return invalidReply(provider, "returned an empty response")
            GeminiAgentResponse.Success(
                text = text.ifBlank { "I completed the requested action." },
                toolCall = toolCall
            )
        } catch (_: Exception) {
            invalidReply(provider, "returned an unreadable response")
        }
    }

    // ---- Anthropic (Claude) ----

    private fun anthropicMessages(
        provider: LlmProvider,
        apiKey: String,
        model: String,
        system: String,
        history: List<GeminiConversationTurn>
    ): GeminiAgentResponse {
        val messages = JSONArray()
        var callSeq = 0
        history.forEach { turn ->
            turn.text?.let {
                messages.put(
                    JSONObject().put("role", if (turn.role == "model") "assistant" else "user").put(
                        "content", JSONArray().put(JSONObject().put("type", "text").put("text", it))
                    )
                )
            }
            turn.functionCall?.let { call ->
                val callId = call.id?.takeIf(String::isNotBlank) ?: "call-${++callSeq}"
                messages.put(
                    JSONObject().put("role", "assistant").put(
                        "content",
                        JSONArray().put(
                            JSONObject().put("type", "tool_use").put("id", callId)
                                .put("name", call.toolName)
                                .put("input", JSONObject(call.arguments))
                        )
                    )
                )
            }
            turn.functionResponse?.let { result ->
                val matchId = (history.firstOrNull { it.functionCall?.toolName == result.name }?.functionCall?.id)
                    ?.takeIf { !it.isNullOrBlank() } ?: "call-$callSeq"
                messages.put(
                    JSONObject().put("role", "user").put(
                        "content",
                        JSONArray().put(
                            JSONObject().put("type", "tool_result").put("tool_use_id", matchId)
                                .put("content", result.result)
                        )
                    )
                )
            }
        }
        if (messages.length() == 0) {
            messages.put(
                JSONObject().put("role", "user").put(
                    "content", JSONArray().put(JSONObject().put("type", "text").put("text", "(no input)"))
                )
            )
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 2048)
            .put("system", system)
            .put("messages", messages)
            .put("tools", anthropicTools())
        val (code, raw) = try {
            postJson(
                url = "${provider.baseUrl.trimEnd('/')}/v1/messages",
                headers = mapOf(
                    "x-api-key" to apiKey,
                    "anthropic-version" to "2023-06-01"
                ),
                body = body.toString()
            )
        } catch (t: TimeoutMarker) {
            return networkError("The ${provider.label} request timed out.", GeminiErrorKind.TIMEOUT)
        } catch (o: OfflineMarker) {
            return networkError("Could not reach ${provider.label}. Check your internet connection.", GeminiErrorKind.NO_NETWORK)
        }
        if (code !in 200..299) return httpError(provider, code, raw)
        return try {
            val blocks = JSONObject(raw).optJSONArray("content")
                ?: return invalidReply(provider, "returned no content")
            var text = ""
            var toolCall: ToolExecutionRequest? = null
            for (i in 0 until blocks.length()) {
                val block = blocks.optJSONObject(i) ?: continue
                when (block.optString("type")) {
                    "text" -> text += block.optString("text")
                    "tool_use" -> if (toolCall == null) {
                        val name = block.optString("name").takeIf(String::isNotBlank)
                            ?: return invalidReply(provider, "returned a tool call without a name")
                        toolCall = ToolExecutionRequest(
                            toolName = name,
                            arguments = jsonToMap(block.optJSONObject("input") ?: JSONObject()),
                            id = block.optString("id").takeIf(String::isNotBlank)
                        )
                    }
                }
            }
            if (text.isBlank() && toolCall == null) return invalidReply(provider, "returned an empty response")
            GeminiAgentResponse.Success(
                text = text.trim().ifBlank { "I completed the requested action." },
                toolCall = toolCall
            )
        } catch (_: Exception) {
            invalidReply(provider, "returned an unreadable response")
        }
    }

    // ---- shared plumbing ----

    private fun postJson(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        return try {
            val builder = Request.Builder().url(url).post(body.toRequestBody(JSON))
            headers.forEach { (k, v) -> builder.header(k, v) }
            http.newCall(builder.build()).execute().use { response ->
                response.code to (response.body?.string().orEmpty())
            }
        } catch (_: SocketTimeoutException) {
            throw TimeoutMarker()
        } catch (_: InterruptedIOException) {
            throw TimeoutMarker()
        } catch (_: UnknownHostException) {
            throw OfflineMarker()
        } catch (_: ConnectException) {
            throw OfflineMarker()
        } catch (e: IOException) {
            throw OfflineMarker(e.message)
        }
    }

    private class TimeoutMarker : IOException()
    private class OfflineMarker(message: String? = null) : IOException(message)

    private fun httpError(provider: LlmProvider, code: Int, body: String): GeminiAgentResponse.Error {
        val label = provider.label
        val kind = when (code) {
            400 -> GeminiErrorKind.BAD_REQUEST
            401 -> GeminiErrorKind.UNAUTHORIZED
            403 -> GeminiErrorKind.PERMISSION_DENIED
            404 -> GeminiErrorKind.MODEL_NOT_FOUND
            429 -> GeminiErrorKind.RATE_LIMITED
            in 500..599 -> GeminiErrorKind.SERVER_ERROR
            else -> GeminiErrorKind.UNKNOWN
        }
        val detail = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("error", "")
        }.getOrNull().orEmpty().takeIf(String::isNotBlank)
        val message = when (kind) {
            GeminiErrorKind.UNAUTHORIZED -> "$label rejected the API key (HTTP 401). Check the key in Settings."
            GeminiErrorKind.PERMISSION_DENIED -> "$label denied access (HTTP 403). Check key permissions."
            GeminiErrorKind.MODEL_NOT_FOUND -> "$label has no such model (HTTP 404). Pick another model in Settings."
            GeminiErrorKind.RATE_LIMITED -> "$label rate limit hit (HTTP 429). Try again later."
            GeminiErrorKind.SERVER_ERROR -> "$label is unavailable (HTTP $code). Try again later."
            else -> "$label request failed (HTTP $code)." + (detail?.let { " $it" } ?: "")
        }
        return GeminiAgentResponse.Error(message = message, kind = kind, statusCode = code)
    }

    private fun invalidReply(provider: LlmProvider, what: String) = GeminiAgentResponse.Error(
        message = "${provider.label} $what.",
        kind = GeminiErrorKind.INVALID_RESPONSE,
        retryable = false
    )

    private fun networkError(message: String, kind: GeminiErrorKind) = GeminiAgentResponse.Error(
        message = message,
        kind = kind,
        retryable = true
    )

    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = when (val v = json.get(key)) {
                JSONObject.NULL -> null
                is JSONObject -> jsonToMap(v)
                is JSONArray -> List(v.length()) { i ->
                    when (val e = v.get(i)) {
                        JSONObject.NULL -> null
                        is JSONObject -> jsonToMap(e)
                        else -> e
                    }
                }
                else -> v
            }
        }
        return map
    }

    // ---- tool schemas (mirrors GeminiAgentClient.functionDeclarations) ----

    private data class ToolDef(
        val name: String,
        val description: String,
        val properties: JSONObject,
        val required: List<String>
    )

    private fun toolDefs(): List<ToolDef> {
        fun prop(type: String, description: String) = JSONObject().put("type", type).put("description", description)
        fun obj(vararg props: Pair<String, JSONObject>) = JSONObject().apply {
            props.forEach { (k, v) -> put(k, v) }
        }
        return listOf(
            ToolDef("manage_memory", "Save, recall, update, or delete user facts and preferences.",
                obj("action" to prop("string", "save, recall, update, or delete"),
                    "key" to prop("string", "Short memory key"),
                    "content" to prop("string", "Fact to save or new value"),
                    "category" to prop("string", "Memory category"),
                    "query" to prop("string", "Recall search terms")), listOf("action")),
            ToolDef("schedule_task", "Schedule a recurring or one-time background task.",
                obj("title" to prop("string", "Concise task title"),
                    "promptAction" to prop("string", "What the agent should do when it runs"),
                    "timeExpression" to prop("string", "Local time of day, e.g. 9:00 AM"),
                    "recurrence" to prop("string", "daily, weekdays, weekends, day names, once, or cron"),
                    "isRecurring" to prop("boolean", "False for a single run")), listOf("promptAction", "timeExpression")),
            ToolDef("browse_web", "Fetch and extract public web page text or headlines.",
                obj("url" to prop("string", "Public HTTP or HTTPS URL"),
                    "action" to prop("string", "Extraction type")), listOf("url")),
            ToolDef("search_emails", "Search Gmail. Use Gmail query syntax.",
                obj("query" to prop("string", "Gmail search query"),
                    "maxResults" to prop("integer", "Max emails, 1-15")), listOf("query")),
            ToolDef("read_email", "Read one Gmail message by id from search_emails.",
                obj("messageId" to prop("string", "Message id")), listOf("messageId")),
            ToolDef("create_email_draft", "Save a Gmail draft without sending.",
                obj("recipient" to prop("string", "Recipient email"),
                    "subject" to prop("string", "Subject"),
                    "body" to prop("string", "Body")), listOf("recipient", "subject", "body")),
            ToolDef("send_email", "Send email via Gmail after user approval.",
                obj("recipient" to prop("string", "Recipient email"),
                    "subject" to prop("string", "Subject"),
                    "body" to prop("string", "Body")), listOf("recipient", "subject", "body")),
            ToolDef("open_url", "Open a public HTTPS website in the in-app browser.",
                obj("url" to prop("string", "Public HTTPS URL")), listOf("url")),
            ToolDef("search_webpage", "Search the current page, or the web if none is open.",
                obj("query" to prop("string", "Search terms"),
                    "selector" to prop("string", "Optional CSS selector")), listOf("query")),
            ToolDef("extract_links", "List links on the active browser page.", obj(), emptyList()),
            ToolDef("read_page", "Read visible text from the active browser page.", obj(), emptyList()),
            ToolDef("click_element", "Click a page element. Needs user approval.",
                obj("selector" to prop("string", "CSS selector")), listOf("selector")),
            ToolDef("enter_text", "Type into a page field without submitting. Needs approval.",
                obj("selector" to prop("string", "CSS selector"),
                    "text" to prop("string", "Text to enter")), listOf("selector", "text")),
            ToolDef("scroll_page", "Scroll the active page up or down.",
                obj("direction" to prop("string", "up or down")), listOf("direction")),
            ToolDef("navigate_back", "Back in browser history.", obj(), emptyList()),
            ToolDef("navigate_forward", "Forward in browser history.", obj(), emptyList()),
            ToolDef("get_browser_state", "Current page title, URL, loading state, text preview.", obj(), emptyList())
        )
    }

    private fun openAiTools(): JSONArray {
        val arr = JSONArray()
        toolDefs().forEach { tool ->
            arr.put(
                JSONObject().put("type", "function").put(
                    "function",
                    JSONObject().put("name", tool.name).put("description", tool.description)
                        .put("parameters", JSONObject().put("type", "object")
                            .put("properties", tool.properties)
                            .put("required", JSONArray(tool.required)))
                )
            )
        }
        return arr
    }

    private fun anthropicTools(): JSONArray {
        val arr = JSONArray()
        toolDefs().forEach { tool ->
            arr.put(
                JSONObject().put("name", tool.name).put("description", tool.description)
                    .put("input_schema", JSONObject().put("type", "object")
                        .put("properties", tool.properties)
                        .put("required", JSONArray(tool.required)))
            )
        }
        return arr
    }

    private companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
