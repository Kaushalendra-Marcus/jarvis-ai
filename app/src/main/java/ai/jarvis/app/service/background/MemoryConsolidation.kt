package ai.jarvis.app.service.background

/**
 * Turns recent chat lines into durable memory candidates.
 *
 * Pure and deterministic so it can run on a schedule and be unit tested:
 * the worker feeds it recent user prompts, it returns facts worth keeping.
 * Saving itself stays in [MemoryConsolidationWorker] via the hybrid store,
 * so Room always gets the fact and Mem0 gets it when a key is set.
 */
data class MemoryCandidate(
    val key: String,
    val content: String,
    val category: String
)

object MemoryConsolidation {

    private data class Pattern(
        val regex: Regex,
        val keyOf: (MatchResult) -> String,
        val contentOf: (MatchResult) -> String,
        val category: String
    )

    private val PATTERNS = listOf(
        Pattern(
            Regex("my name is ([A-Za-z][A-Za-z .'-]{1,60})", RegexOption.IGNORE_CASE),
            { "name" }, { it.groupValues[1].trim() }, "identity"
        ),
        Pattern(
            Regex("i (?:live in|am from|'m from) ([A-Za-z][A-Za-z .,'-]{1,80})", RegexOption.IGNORE_CASE),
            { "location" }, { it.groupValues[1].trim() }, "identity"
        ),
        Pattern(
            Regex("my email is ([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})", RegexOption.IGNORE_CASE),
            { "email" }, { it.groupValues[1].trim() }, "contact"
        ),
        Pattern(
            Regex("my phone(?: number)? is ([+()\\-\\d ]{6,25})", RegexOption.IGNORE_CASE),
            { "phone" }, { it.groupValues[1].trim() }, "contact"
        ),
        Pattern(
            Regex("my birthday is ([A-Za-z0-9 ,/-]{3,40})", RegexOption.IGNORE_CASE),
            { "birthday" }, { it.groupValues[1].trim() }, "identity"
        ),
        Pattern(
            Regex("my favo[u]?rite (\\w[\\w ]{0,30}?) is ([^.!?]{2,120})", RegexOption.IGNORE_CASE),
            { "favorite ${it.groupValues[1].trim().lowercase()}" },
            { it.groupValues[2].trim() }, "preference"
        ),
        Pattern(
            Regex("i (?:like|love|prefer|enjoy) ([^.!?]{3,140})", RegexOption.IGNORE_CASE),
            { "preference" }, { m -> "Likes ${m.groupValues[1].trim()}" }, "preference"
        ),
        Pattern(
            Regex("remember (?:that )?([^.!?]{4,200})", RegexOption.IGNORE_CASE),
            { "note" }, { it.groupValues[1].trim() }, "general"
        )
    )

    /**
     * @param userTexts recent user prompts, oldest first.
     * @param existingKeys keys already stored (any case), skipped to avoid duplicates.
     */
    fun extractCandidates(
        userTexts: List<String>,
        existingKeys: Set<String> = emptySet(),
        maxCandidates: Int = 10
    ): List<MemoryCandidate> {
        val known = existingKeys.map { it.lowercase() }.toSet()
        val seen = mutableSetOf<String>()
        val out = mutableListOf<MemoryCandidate>()
        for (raw in userTexts) {
            val text = raw.trim().replace(Regex("\\s+"), " ")
            if (text.length < 8) continue
            for (pattern in PATTERNS) {
                val match = pattern.regex.find(text) ?: continue
                val key = pattern.keyOf(match).trim().take(60)
                val content = pattern.contentOf(match).trim().trimEnd('.', '!', '?', ';', ',').take(200)
                if (key.isEmpty() || content.length < 2) continue
                val dedupe = "${key.lowercase()}|${content.lowercase()}"
                if (key.lowercase() in known || dedupe in seen) continue
                seen.add(dedupe)
                out.add(MemoryCandidate(key, content, pattern.category))
                if (out.size >= maxCandidates) return out
                break
            }
        }
        return out
    }
}
