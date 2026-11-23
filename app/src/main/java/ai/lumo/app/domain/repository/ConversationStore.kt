package ai.lumo.app.domain.repository

import ai.lumo.app.domain.model.AgentMessage

interface ConversationStore {
    fun loadMessages(): List<AgentMessage>
    fun saveMessages(messages: List<AgentMessage>)
    fun clear()
}

/** One chat thread in the conversation list. */
data class ConversationInfo(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val preview: String = ""
)

/**
 * Multi-conversation library. The active thread's messages drive the agent;
 * the index drives the switcher (new / switch / delete).
 */
interface ConversationLibrary {
    fun list(): List<ConversationInfo>
    fun load(id: String): List<AgentMessage>
    fun save(id: String, messages: List<AgentMessage>)
    fun create(): String
    fun delete(id: String)
    fun rename(id: String, title: String)
}
