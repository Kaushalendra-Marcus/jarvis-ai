package ai.jarvis.app.domain.memory

/**
 * Semantic personal memory seam (Req.5).
 * Room = offline source of truth. Mem0 = semantic recall when configured.
 * AgentOrchestrator only depends on this interface, never on Room or HTTP directly.
 */
interface SemanticMemoryStore {
    suspend fun save(key: String, content: String, category: String = "general"): String
    suspend fun recall(query: String, limit: Int = 6): List<ScoredMemory>
    suspend fun recent(limit: Int = 4): List<ScoredMemory>
    suspend fun delete(id: String): Boolean
}

data class ScoredMemory(
    val id: String,
    val key: String,
    val content: String,
    val category: String,
    val score: Float = 1f,
    val source: MemorySource = MemorySource.LOCAL
)

enum class MemorySource { LOCAL, MEM0 }
