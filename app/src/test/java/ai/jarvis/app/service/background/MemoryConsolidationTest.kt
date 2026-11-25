package ai.jarvis.app.service.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryConsolidationTest {

    @Test
    fun nameAndContactFacts_areExtracted() {
        val out = MemoryConsolidation.extractCandidates(
            listOf("Hi, my name is Priya Sharma.", "My email is priya@example.com")
        )
        assertEquals("name", out[0].key)
        assertEquals("Priya Sharma", out[0].content)
        assertEquals("email", out[1].key)
        assertEquals("priya@example.com", out[1].content)
    }

    @Test
    fun preferencesAndRemember_areExtracted() {
        val out = MemoryConsolidation.extractCandidates(
            listOf("I love filter coffee in the morning", "Remember that my flight is on Friday")
        )
        assertTrue(out.any { it.category == "preference" && it.content.contains("filter coffee") })
        assertTrue(out.any { it.key == "note" && it.content.contains("flight") })
    }

    @Test
    fun smallTalk_producesNothing() {
        assertTrue(
            MemoryConsolidation.extractCandidates(
                listOf("ok", "thanks", "what time is it?")
            ).isEmpty()
        )
    }

    @Test
    fun alreadyStoredKeys_areSkipped() {
        val out = MemoryConsolidation.extractCandidates(
            listOf("My name is Priya Sharma"),
            existingKeys = setOf("NAME")
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun duplicatesAndCaps_areRespected() {
        val texts = List(20) { "My name is Priya Sharma" }
        val out = MemoryConsolidation.extractCandidates(texts, maxCandidates = 3)
        assertEquals(1, out.size)
    }
}
