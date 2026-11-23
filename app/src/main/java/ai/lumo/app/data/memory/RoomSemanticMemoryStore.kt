package ai.lumo.app.data.memory

import ai.lumo.app.data.local.memory.MemoryDao
import ai.lumo.app.data.local.memory.MemoryEntity
import ai.lumo.app.domain.memory.MemorySource
import ai.lumo.app.domain.memory.ScoredMemory
import ai.lumo.app.domain.memory.SemanticMemoryStore

/**
 * Room-backed offline store. Always available, survives restarts (Req.5).
 * Mem0 sync is layered on top by [HybridSemanticMemoryStore] when an API key is set.
 */
class RoomSemanticMemoryStore(private val dao: MemoryDao) : SemanticMemoryStore {
    override suspend fun save(key: String, content: String, category: String): String {
        val id = dao.insertMemory(
            MemoryEntity(key = key, content = content, category = category, createdAt = System.currentTimeMillis(), importance = 1)
        )
        return id.toString()
    }

    override suspend fun recall(query: String, limit: Int): List<ScoredMemory> {
        if (query.isBlank()) return emptyList()
        return dao.searchMemories(query).take(limit).map {
            ScoredMemory(it.id.toString(), it.key, it.content, it.category, 1f, MemorySource.LOCAL)
        }
    }

    override suspend fun recent(limit: Int): List<ScoredMemory> =
        dao.getRecentMemories(limit).map {
            ScoredMemory(it.id.toString(), it.key, it.content, it.category, 1f, MemorySource.LOCAL)
        }

    override suspend fun delete(id: String): Boolean {
        val row = id.toLongOrNull() ?: return false
        dao.deleteMemory(row)
        return true
    }
}
