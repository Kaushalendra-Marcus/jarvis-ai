package ai.lumo.app.domain.repository

import ai.lumo.app.domain.model.Memory
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun getAllMemories(): Flow<List<Memory>>
    suspend fun insertMemory(memory: Memory): Long
    suspend fun deleteMemory(id: Long)
    suspend fun searchMemories(query: String): List<Memory>
    suspend fun getRecentMemories(limit: Int = 10): List<Memory>
}
