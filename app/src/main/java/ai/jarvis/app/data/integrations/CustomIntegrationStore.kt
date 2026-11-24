package ai.jarvis.app.data.integrations

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists user-added custom integrations (MCP / OpenAPI) across restarts.
 * The registry itself is in-memory; this store is its saved counterpart, so data
 * the user connects is kept as per the assessment's memory/persistence requirement.
 */
class CustomIntegrationStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class SavedCustom(
        val id: String,
        val title: String,
        val type: String, // "mcp" or "openapi"
        val url: String
    )

    fun load(): List<SavedCustom> {
        val raw = prefs.getString(KEY_CUSTOMS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SavedCustom(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    type = o.getString("type"),
                    url = o.getString("url")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(items: List<SavedCustom>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("type", it.type)
                    .put("url", it.url)
            )
        }
        prefs.edit().putString(KEY_CUSTOMS, arr.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "jarvis_integrations"
        const val KEY_CUSTOMS = "custom_integrations"
    }
}
