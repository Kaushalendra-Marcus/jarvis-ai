package ai.jarvis.app.data.local

import ai.jarvis.app.domain.model.AgentMessage
import ai.jarvis.app.domain.model.ToolStatus
import ai.jarvis.app.domain.repository.ConversationStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JsonConversationStore(private val file: File) : ConversationStore {
    override fun loadMessages(): List<AgentMessage> = runCatching {
        val json = JSONArray(file.takeIf(File::exists)?.readText() ?: return emptyList())
        buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                val id = item.getString("id")
                val timestamp = item.getLong("time")
                when (item.getString("type")) {
                    "user" -> add(AgentMessage.UserPrompt(id, timestamp, item.getString("text"), item.optBoolean("voice")))
                    "agent" -> add(
                        AgentMessage.AgentResponse(
                            id, timestamp, item.getString("text"),
                            appliedMemories = runCatching {
                                val arr = JSONArray(item.optString("applied", "[]"))
                                List(arr.length()) { arr.optString(it) }.filter(String::isNotBlank)
                            }.getOrDefault(emptyList()),
                            appliedSmartRecall = item.optBoolean("smart", false)
                        )
                    )
                    "error" -> add(AgentMessage.ErrorMessage(
                        id, timestamp, item.getString("text"),
                        item.optString("retry").takeIf(String::isNotBlank)
                    ))
                    "tool" -> add(AgentMessage.ToolAction(
                        id, timestamp, item.getString("name"), item.optString("args"),
                        runCatching { ToolStatus.valueOf(item.optString("status")) }.getOrDefault(ToolStatus.FAILED),
                        item.optString("result").takeIf(String::isNotBlank)
                    ))
                    "reasoning" -> add(AgentMessage.AgentReasoning(id, timestamp, item.getString("text")))
                }
            }
        }
    }.getOrDefault(emptyList())

    override fun saveMessages(messages: List<AgentMessage>) {
        val json = JSONArray()
        messages.takeLast(MAX_MESSAGES).forEach { message ->
            val item = JSONObject().put("id", message.id).put("time", message.timestamp)
            when (message) {
                is AgentMessage.UserPrompt -> item.put("type", "user").put("text", message.text).put("voice", message.isVoiceInput)
                is AgentMessage.AgentResponse -> item.put("type", "agent").put("text", message.text)
                    .put("applied", JSONArray(message.appliedMemories).toString())
                    .put("smart", message.appliedSmartRecall)
                is AgentMessage.ErrorMessage -> item.put("type", "error").put("text", message.error)
                    .put("retry", message.retryPrompt.orEmpty())
                is AgentMessage.ToolAction -> item.put("type", "tool").put("name", message.toolName)
                    .put("args", message.argumentsJson).put("status", message.status.name)
                    .put("result", message.resultOutput.orEmpty())
                is AgentMessage.AgentReasoning -> item.put("type", "reasoning").put("text", message.thought)
            }
            json.put(item)
        }
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.absoluteFile.parentFile, "${file.name}.tmp")
            temporary.writeText(json.toString())
            if (!temporary.renameTo(file)) {
                file.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    override fun clear() {
        runCatching { file.delete() }
    }

    companion object {
        private const val MAX_MESSAGES = 100
    }
}
