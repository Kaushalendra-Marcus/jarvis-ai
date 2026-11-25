package ai.jarvis.app.service.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ai.jarvis.app.JarvisApplication
import ai.jarvis.app.core.security.SecurePreferences
import ai.jarvis.app.data.local.FileConversationLibrary
import ai.jarvis.app.domain.model.AgentMessage
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Hourly memory consolidation (issue: build memory from Room chats on a schedule).
 *
 * Reads user prompts written since the last run, extracts durable facts with
 * [MemoryConsolidation], and saves them through the hybrid store: Room always,
 * Mem0 when a key is set. Offline-safe and silent: a run with nothing new, or
 * with no stored key, simply succeeds so WorkManager keeps the hourly rhythm.
 */
class MemoryConsolidationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? JarvisApplication ?: return Result.failure()
        return try {
            val since = prefs().getLong(KEY_LAST_RUN, 0L)
            val now = System.currentTimeMillis()

            val library = FileConversationLibrary(File(app.filesDir, CHATS_DIR))
            val recentPrompts = runCatching {
                library.list()
                    .sortedByDescending { it.updatedAt }
                    .take(THREADS_PER_RUN)
                    .flatMap { library.load(it.id) }
                    .filterIsInstance<AgentMessage.UserPrompt>()
                    .filter { it.timestamp > since }
                    .sortedBy { it.timestamp }
                    .takeLast(PROMPTS_PER_RUN)
                    .map { it.text }
            }.getOrDefault(emptyList())

            if (recentPrompts.isNotEmpty()) {
                val existing = runCatching {
                    app.memoryRepository.getRecentMemories(100).map { it.key }.toSet()
                }.getOrDefault(emptySet())
                val candidates = MemoryConsolidation.extractCandidates(recentPrompts, existing)
                for (candidate in candidates) {
                    runCatching {
                        app.semanticMemoryStore.save(candidate.key, candidate.content, candidate.category)
                    }
                }
            }

            prefs().edit().putLong(KEY_LAST_RUN, now).apply()
            Result.success()
        } catch (error: Exception) {
            Log.w(TAG, "Memory consolidation failed, will retry", error)
            Result.retry()
        }
    }

    private fun prefs() = applicationContext.getSharedPreferences(
        SecurePreferences.PREFS_SETTINGS_FILE, Context.MODE_PRIVATE
    )

    companion object {
        const val WORK_NAME = "jarvis-memory-consolidation"
        private const val TAG = "MemoryConsolidation"
        private const val KEY_LAST_RUN = "memory_consolidation_last_run"
        private const val CHATS_DIR = "jarvis-chats"
        private const val THREADS_PER_RUN = 5
        private const val PROMPTS_PER_RUN = 50

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
