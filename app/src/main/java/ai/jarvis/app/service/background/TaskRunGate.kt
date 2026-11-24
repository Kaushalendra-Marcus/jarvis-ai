package ai.jarvis.app.service.background

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Single-execution gate for scheduled runs.
 *
 * Each scheduled occurrence carries a run token (its epoch millis). The exact-alarm
 * path and the WorkManager chain can both become eligible at the same moment; the
 * first to claim the token runs, the loser exits quietly. Retries explicitly
 * release the token first, so backoff attempts can reclaim it.
 */
interface RunTokenStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
}

class PrefsRunTokenStore(context: Context) : RunTokenStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun set(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }

    companion object {
        const val PREFS_NAME = "jarvis_task_runs"
    }
}

class MemoryRunTokenStore : RunTokenStore {
    private val map = ConcurrentHashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun set(key: String, value: String) {
        map[key] = value
    }
}

object TaskRunGate {
    internal var store: RunTokenStore = MemoryRunTokenStore()

    fun init(context: Context) {
        store = PrefsRunTokenStore(context.applicationContext)
    }

    private fun key(taskId: String) = "run_$taskId"

    /** Records the token of the next expected run. Called at schedule time. */
    @Synchronized
    fun expect(taskId: String, runToken: Long) {
        if (runToken != 0L) store.set(key(taskId), runToken.toString())
    }

    /**
     * Claims the run. Returns true for the single winner; late duplicates and
     * retries of an already-claimed token return false. Token 0 (legacy/manual
     * without a token) always passes for backward compatibility.
     */
    @Synchronized
    fun claim(taskId: String, runToken: Long): Boolean {
        if (runToken == 0L) return true
        val current = store.get(key(taskId)) ?: return true
        if (current != runToken.toString()) return false
        store.set(key(taskId), "claimed:$runToken")
        return true
    }

    /** Releases a claim so a scheduled retry can reclaim the same token. */
    @Synchronized
    fun release(taskId: String, runToken: Long) {
        if (runToken == 0L) return
        if (store.get(key(taskId)) == "claimed:$runToken") {
            store.set(key(taskId), runToken.toString())
        }
    }
}
