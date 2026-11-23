package ai.lumo.app.data.local

import ai.lumo.app.domain.model.AgentMessage
import ai.lumo.app.domain.repository.ConversationInfo
import ai.lumo.app.domain.repository.ConversationLibrary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * File-backed multi-conversation library. Each thread is a [JsonConversationStore]-format
 * file (`<id>.json`); `index.json` holds titles and timestamps. Titles come from the
 * first user message so the switcher reads naturally.
 */
class FileConversationLibrary(private val dir: File) : ConversationLibrary {

    private val indexFile: File get() = File(dir, INDEX_NAME)

    @Synchronized
    override fun list(): List<ConversationInfo> {
        val entries = readIndex()
        // Drop index rows whose file vanished; adopt orphan files.
        val files = dir.listFiles { f -> f.name.endsWith(".json") && f.name != INDEX_NAME }
            .orEmpty().map { it.nameWithoutExtension }.toSet()
        val kept = entries.filter { it.id in files }
        val known = kept.map { it.id }.toSet()
        val adopted = (files - known).map { id ->
            ConversationInfo(id, titleFor(load(id)), updatedAtOf(id), previewFor(load(id)))
        }
        val all = (kept + adopted).sortedByDescending { it.updatedAt }
        if (all.size != entries.size) writeIndex(all)
        return all
    }

    @Synchronized
    override fun load(id: String): List<AgentMessage> =
        JsonConversationStore(fileFor(id)).loadMessages()

    @Synchronized
    override fun save(id: String, messages: List<AgentMessage>) {
        JsonConversationStore(fileFor(id)).saveMessages(messages)
        val now = System.currentTimeMillis()
        val entries = readIndex().toMutableList()
        val existing = entries.indexOfFirst { it.id == id }
        val info = ConversationInfo(id, titleFor(messages), now, previewFor(messages))
        if (existing >= 0) entries[existing] = info else entries.add(info)
        writeIndex(entries.sortedByDescending { it.updatedAt })
    }

    @Synchronized
    override fun create(): String {
        val id = "chat-${System.currentTimeMillis()}"
        save(id, emptyList())
        return id
    }

    @Synchronized
    override fun delete(id: String) {
        runCatching { fileFor(id).delete() }
        writeIndex(readIndex().filterNot { it.id == id })
    }

    @Synchronized
    override fun rename(id: String, title: String) {
        val entries = readIndex().toMutableList()
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) {
            entries[index] = entries[index].copy(title = title.take(60), updatedAt = System.currentTimeMillis())
            writeIndex(entries.sortedByDescending { it.updatedAt })
        }
    }

    private fun fileFor(id: String): File {
        val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifBlank { "chat" }
        return File(dir, "$safe.json")
    }

    private fun updatedAtOf(id: String): Long = fileFor(id).lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()

    private fun titleFor(messages: List<AgentMessage>): String {
        val first = messages.filterIsInstance<AgentMessage.UserPrompt>().firstOrNull()?.text?.trim()
        return first?.take(TITLE_LENGTH)?.trim().orEmpty().ifBlank { "New chat" }
    }

    private fun previewFor(messages: List<AgentMessage>): String {
        val last = messages.lastOrNull { it is AgentMessage.UserPrompt || it is AgentMessage.AgentResponse }
        val text = when (last) {
            is AgentMessage.UserPrompt -> last.text
            is AgentMessage.AgentResponse -> last.text
            else -> ""
        }.trim().replace(Regex("\\s+"), " ")
        return text.take(PREVIEW_LENGTH)
    }

    private fun readIndex(): List<ConversationInfo> = runCatching {
        val raw = indexFile.takeIf(File::exists)?.readText() ?: return emptyList()
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ConversationInfo(
                id = o.getString("id"),
                title = o.optString("title", "New chat"),
                updatedAt = o.optLong("updatedAt", 0L),
                preview = o.optString("preview", "")
            )
        }
    }.getOrDefault(emptyList())

    private fun writeIndex(entries: List<ConversationInfo>) {
        runCatching {
            dir.mkdirs()
            val arr = JSONArray()
            entries.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("title", it.title)
                        .put("updatedAt", it.updatedAt)
                        .put("preview", it.preview)
                )
            }
            val tmp = File(dir, "$INDEX_NAME.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(indexFile)) {
                indexFile.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    private companion object {
        const val INDEX_NAME = "index.json"
        const val TITLE_LENGTH = 42
        const val PREVIEW_LENGTH = 90
    }
}
