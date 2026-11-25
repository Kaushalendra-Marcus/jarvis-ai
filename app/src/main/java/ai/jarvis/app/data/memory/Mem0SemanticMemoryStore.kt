package ai.jarvis.app.data.memory

import ai.jarvis.app.domain.memory.MemorySource
import ai.jarvis.app.domain.memory.ScoredMemory
import ai.jarvis.app.domain.memory.SemanticMemoryStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
                parseMemories(res, query)
            }
        }.getOrElse { error ->
            Log.w(TAG, "Mem0 recall failed, using on-device memories only", error)
            emptyList()
        }
    }

    override suspend fun recent(limit: Int): List<ScoredMemory> {
        val key_ = apiKeyProvider().trim()
        if (key_.isBlank()) return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                val user = URLEncoder.encode(userIdProvider(), "UTF-8")
                val n = limit.coerceIn(1, 20)
                val url = URL("https://api.mem0.ai/v1/memories/?user_id=$user&limit=$n")
                val res = get(url, key_)
                parseMemories(res, "")
            }
        }.getOrElse { error ->
            Log.w(TAG, "Mem0 recent failed, showing on-device memories only", error)
            emptyList()
        }
    }

    override suspend fun delete(id: String): Boolean {
        // Local-only rows and failed saves never reached Mem0, nothing remote to remove.
        if (id.isBlank() || id == "local-only" || id == "mem0-error") return true
        if (id.toLongOrNull() != null && !id.startsWith("mem0-")) return true
        val key_ = apiKeyProvider().trim()
        if (key_.isBlank()) return false
        return runCatching {
            withContext(Dispatchers.IO) {
                val safe = URLEncoder.encode(id, "UTF-8")
                val url = URL("https://api.mem0.ai/v1/memories/$safe/")
                deleteReq(url, key_)
            }
        }.getOrElse { error ->
            Log.w(TAG, "Mem0 delete failed for $id", error)
            false
        }
    }

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
        val code = c.responseCode
        val text = try { c.inputStream.bufferedReader().readText() } catch (_: Exception) {
            c.errorStream?.bufferedReader()?.readText().orEmpty()
        }
        c.disconnect()
        if (code !in 200..299) throw java.io.IOException("Mem0 returned HTTP $code")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun get(url: URL, apiKey: String): JSONObject {
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Authorization", "Token $apiKey")
        }
        val code = c.responseCode
        val text = try { c.inputStream.bufferedReader().readText() } catch (_: Exception) {
            c.errorStream?.bufferedReader()?.readText().orEmpty()
        }
        c.disconnect()
        if (code !in 200..299) throw java.io.IOException("Mem0 returned HTTP $code")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun deleteReq(url: URL, apiKey: String): Boolean {
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Authorization", "Token $apiKey")
        }
        val code = c.responseCode
        runCatching { c.inputStream.close() }
        runCatching { c.errorStream?.close() }
        c.disconnect()
        return code in 200..299
    }

    private fun parseMemories(res: JSONObject, query: String): List<ScoredMemory> {
        val arr: JSONArray = res.optJSONArray("results") ?: res.optJSONArray("memories") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val content = o.optString("memory", o.optString("text", "")).trim()
            if (content.isEmpty()) return@mapNotNull null
            ScoredMemory(
                id = o.optString("id", "mem0-$i"),
                key = o.optString("key", query.ifBlank { "memory" }),
                content = content,
                category = o.optJSONObject("metadata")?.optString("category", "general") ?: "general",
                score = o.optDouble("score", 0.8).toFloat(),
                source = MemorySource.MEM0
            )
        }
    }

    private companion object {
        const val TAG = "Mem0Memory"
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

    override suspend fun recent(limit: Int): List<ScoredMemory> {
        val l = local.recent(limit)
        val r = runCatching { remote.recent(limit) }.getOrDefault(emptyList())
        return (l + r).distinctBy { it.id + it.content }.take(limit)
    }

    override suspend fun delete(id: String): Boolean {
        val localOk = local.delete(id)
        // Room is the source of truth; a cloud failure is logged by the remote store
        // but must not resurrect a memory the user just deleted on this phone.
        runCatching { remote.delete(id) }
        return localOk
    }
}
