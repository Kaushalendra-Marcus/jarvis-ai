package ai.jarvis.app.data.tools

import ai.jarvis.app.domain.model.ToolExecutionResponse
import ai.jarvis.app.domain.tool.AgentTool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reads and writes the user's Notion workspace through an Internal Integration
 * Token (`secret_…`). The token is pasted once in Connect → Notion and read live
 * on every call, so revoking it in Notion instantly cuts access.
 *
 * Actions: 'search', 'read', 'create', 'append'. Reads are free; creates and
 * appends need a parent page the integration can see (share the page with the
 * integration inside Notion first).
 */
class NotionTool(
    private val tokenProvider: () -> String,
    private val http: OkHttpClient = defaultClient()
) : AgentTool {
    override val name: String = "notion"
    override val description: String =
        "Search, read, and write the user's Notion workspace (requires a connected Notion integration token). " +
            "Actions: 'search' (find pages), 'read' (page text by id), " +
            "'create' (new page under a parent page id with title and text), " +
            "'append' (add text to a page by id)."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val token = tokenProvider().trim()
        if (token.isBlank()) {
            return failure("Notion is not connected. Paste the Internal Integration Token in Connect → Notion first.")
        }
        if (!token.startsWith("secret_") && !token.startsWith("ntn_")) {
            return failure("That Notion token looks wrong — paste the Internal Integration Token (starts with secret_).")
        }
        return try {
            when ((arguments["action"] as? String ?: "search").lowercase().trim()) {
                "search" -> search(token, arguments["query"] as? String ?: "")
                "read" -> read(token, arguments["pageId"] as? String ?: "")
                "create" -> create(
                    token,
                    parentId = arguments["parentId"] as? String ?: "",
                    title = arguments["title"] as? String ?: "",
                    text = arguments["text"] as? String ?: ""
                )
                "append" -> append(
                    token,
                    pageId = arguments["pageId"] as? String ?: "",
                    text = arguments["text"] as? String ?: ""
                )
                else -> failure("Unknown Notion action. Use search, read, create, or append.")
            }
        } catch (error: Exception) {
            failure(notionError(error))
        }
    }

    private fun search(token: String, query: String): ToolExecutionResponse {
        if (query.isBlank()) return failure("Missing 'query' for Notion search.")
        val res = post(token, "search", JSONObject()
            .put("query", query)
            .put("page_size", 5)
            .put("filter", JSONObject().put("property", "object").put("value", "page")))
        val results = res.optJSONArray("results") ?: JSONArray()
        if (results.length() == 0) {
            return ToolExecutionResponse(name, true, "No Notion pages matched \"$query\".")
        }
        val lines = (0 until results.length()).map { i ->
            val page = results.getJSONObject(i)
            "${page.optString("id")} | ${pageTitle(page)}"
        }
        return ToolExecutionResponse(name, true, "Notion pages:\n" + lines.joinToString("\n"))
    }

    private fun read(token: String, pageId: String): ToolExecutionResponse {
        if (pageId.isBlank()) return failure("Missing 'pageId' for Notion read. Search first to get one.")
        val page = get(token, "pages/${cleanId(pageId)}")
        val title = pageTitle(page)
        val blocks = get(token, "blocks/${cleanId(pageId)}/children?page_size=50")
        val text = StringBuilder()
        val arr = blocks.optJSONArray("results") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val block = arr.getJSONObject(i)
            val type = block.optString("type")
            val data = block.optJSONObject(type) ?: continue
            val rich = data.optJSONArray("rich_text") ?: continue
            for (j in 0 until rich.length()) {
                text.append(rich.optJSONObject(j)?.optString("plain_text").orEmpty())
            }
            text.append("\n")
            if (text.length > 3000) break
        }
        val body = text.toString().trim().take(3000).ifBlank { "(Page has no readable text.)" }
        return ToolExecutionResponse(name, true, "Notion page: $title\n\n$body")
    }

    private fun create(token: String, parentId: String, title: String, text: String): ToolExecutionResponse {
        if (parentId.isBlank()) {
            return failure("Missing 'parentId' — search Notion for the parent page first, and share it with the integration inside Notion.")
        }
        if (title.isBlank()) return failure("Missing 'title' for the new Notion page.")
        val body = JSONObject()
            .put("parent", JSONObject().put("page_id", cleanId(parentId)))
            .put("properties", JSONObject().put("title", JSONArray().put(
                JSONObject().put("text", JSONObject().put("content", title)))))
        if (text.isNotBlank()) {
            body.put("children", JSONArray().put(paragraphBlock(text.take(2000))))
        }
        val created = post(token, "pages", body)
        return ToolExecutionResponse(
            name, true,
            "Created Notion page \"${pageTitle(created)}\" (id ${created.optString("id")})."
        )
    }

    private fun append(token: String, pageId: String, text: String): ToolExecutionResponse {
        if (pageId.isBlank()) return failure("Missing 'pageId' for Notion append. Search first to get one.")
        if (text.isBlank()) return failure("Missing 'text' to append.")
        post(token, "blocks/${cleanId(pageId)}/children", JSONObject()
            .put("children", JSONArray().put(paragraphBlock(text.take(2000)))))
        return ToolExecutionResponse(name, true, "Appended to the Notion page.")
    }

    private fun paragraphBlock(text: String): JSONObject = JSONObject()
        .put("object", "block")
        .put("type", "paragraph")
        .put("paragraph", JSONObject().put("rich_text", JSONArray().put(
            JSONObject().put("type", "text").put("text", JSONObject().put("content", text)))))

    private fun pageTitle(page: JSONObject): String {
        val props = page.optJSONObject("properties") ?: return "Untitled"
        val keys = props.keys()
        while (keys.hasNext()) {
            val prop = props.optJSONObject(keys.next()) ?: continue
            if (prop.optString("type") == "title") {
                val title = prop.optJSONArray("title") ?: continue
                val text = buildString {
                    for (i in 0 until title.length()) {
                        append(title.optJSONObject(i)?.optString("plain_text").orEmpty())
                    }
                }.trim()
                if (text.isNotBlank()) return text
            }
        }
        return "Untitled"
    }

    private fun cleanId(id: String): String = id.replace("-", "").trim()

    private fun get(token: String, path: String): JSONObject {
        val request = Request.Builder()
            .url("https://api.notion.com/v1/$path")
            .header("Authorization", "Bearer $token")
            .header("Notion-Version", "2022-06-28")
            .get()
            .build()
        return executeCall(request)
    }

    private fun post(token: String, path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("https://api.notion.com/v1/$path")
            .header("Authorization", "Bearer $token")
            .header("Notion-Version", "2022-06-28")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return executeCall(request)
    }

    private fun executeCall(request: Request): JSONObject {
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw IllegalStateException("UNAUTHENTICATED")
            }
            if (response.code == 404) {
                throw IllegalStateException("NOT_FOUND")
            }
            if (response.code == 429) {
                throw IllegalStateException("RATE_LIMITED")
            }
            if (response.code !in 200..299) {
                throw IllegalStateException("HTTP_${response.code}")
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }

    private fun notionError(error: Exception): String = when (error.message) {
        "UNAUTHENTICATED" -> "Notion rejected the token. Check it in Connect → Notion (revoked tokens stop working immediately)."
        "NOT_FOUND" -> "Notion could not find that page. Share the page with the integration inside Notion first, then try again."
        "RATE_LIMITED" -> "Notion rate limit hit. Try again in a moment."
        else -> if (error is java.io.IOException) "Could not reach Notion. Check your connection and try again."
        else "Notion request failed (${error.message ?: "unknown error"})."
    }

    private fun failure(message: String) = ToolExecutionResponse(name, false, message)

    private companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
