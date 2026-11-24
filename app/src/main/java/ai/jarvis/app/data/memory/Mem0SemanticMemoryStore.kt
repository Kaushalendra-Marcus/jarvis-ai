package ai.jarvis.app.data.memory

import ai.jarvis.app.domain.memory.MemorySource
import ai.jarvis.app.domain.memory.ScoredMemory
import ai.jarvis.app.domain.memory.SemanticMemoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mem0 semantic recall (https://api.mem0.ai). Optional — when [apiKey] is blank,
 * behaves as a no-op so Room remains the source of truth. Never crashes the agent.
 */
class Mem0SemanticMemoryStore(
    private val apiKeyProvider: () -> String,
    private val userIdProvider: () -> String = { "jarvis-android-user" }
) : SemanticMemoryStore {

    override suspend fun save(key: String, content: String, category: String): String {
        val key_ = apiKeyProvider().trim()
        if (key_.isBlank()) return "local-only"
        return runCatching {
            withContext(Dispatchers.IO) {
                val url = URL("https://api.mem0.ai/v1/memories/")
                val body = JSONObject()
                    .put("user_id", userIdProvider())
                    .put("text", "$key: $content")
                    .put("metadata", JSONObject().put("category", category).put("app", "jarvis-ai"))
                post(url, key_, body.toString())
                "mem0:${System.currentTimeMillis()}"
            }
        }.getOrDefault("mem0-error")
    }

    override suspend fun recall(query: String, limit: Int): List<ScoredMemory> {
        val key_ = apiKeyProvider().trim()
        if (key_.isBlank() || query.isBlank()) return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                val url = URL("https://api.mem0.ai/v1/memories/search/")
                val body = JSONObject()
                    .put("user_id", userIdProvider())
                    .put("query", query)
                    .put("top_k", limit.coerceIn(1, 10))
                val res = post(url, key_, body.toString())
                val arr: JSONArray = res.optJSONArray("results") ?: res.optJSONArray("memories") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    ScoredMemory(
                        id = o.optString("id", "mem0-$i"),
                        key = o.optString("key", query),
                        content = o.optString("memory", o.optString("text", "")),
                        category = o.optJSONObject("metadata")?.optString("category", "general") ?: "general",
                        score = o.optDouble("score", 0.8).toFloat(),
                        source = MemorySource.MEM0
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun recent(limit: Int): List<ScoredMemory> = emptyList()
    override suspend fun delete(id: String): Boolean = true

    private fun post(url: URL, apiKey: String, json: String): JSONObject {
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Authorization", "Token $apiKey")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        c.outputStream.use { it.write(json.toByteArray()) }
        val text = try { c.inputStream.bufferedReader().readText() } catch (_: Exception) {
            c.errorStream?.bufferedReader()?.readText().orEmpty()
        }
        c.disconnect()
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}

/** Hybrid: Room first (offline-safe), Mem0 semantic hits merged in when available. */
class HybridSemanticMemoryStore(
    private val local: SemanticMemoryStore,
    private val remote: SemanticMemoryStore
) : SemanticMemoryStore {
    override suspend fun save(key: String, content: String, category: String): String {
        val localId = local.save(key, content, category)
        remote.save(key, content, category)
        return localId
    }

    override suspend fun recall(query: String, limit: Int): List<ScoredMemory> {
        val l = local.recall(query, limit)
        val r = remote.recall(query, limit)
        return (r + l).distinctBy { it.id + it.content }.take(limit)
    }

    override suspend fun recent(limit: Int): List<ScoredMemory> = local.recent(limit)
    override suspend fun delete(id: String): Boolean = local.delete(id)
}
