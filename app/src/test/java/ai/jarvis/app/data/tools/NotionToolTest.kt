package ai.jarvis.app.data.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotionToolTest {

    @Test
    fun missingToken_guidesToConnectTab() = runBlocking {
        val tool = NotionTool(tokenProvider = { "" })
        val result = tool.execute(mapOf("action" to "search", "query" to "notes"))

        assertFalse(result.isSuccess)
        assertTrue(result.output.contains("Connect"))
    }

    @Test
    fun malformedToken_rejectedBeforeNetwork() = runBlocking {
        val tool = NotionTool(tokenProvider = { "random-string" })
        val result = tool.execute(mapOf("action" to "search", "query" to "notes"))

        assertFalse(result.isSuccess)
        assertTrue(result.output.contains("secret_"))
    }

    @Test
    fun missingActionArguments_failFast() = runBlocking {
        val tool = NotionTool(tokenProvider = { "secret_test" })

        assertFalse(tool.execute(mapOf("action" to "search")).isSuccess)
        assertFalse(tool.execute(mapOf("action" to "read")).isSuccess)
        assertFalse(tool.execute(mapOf("action" to "create", "title" to "T")).isSuccess)
        assertFalse(tool.execute(mapOf("action" to "append", "pageId" to "x")).isSuccess)
        assertFalse(tool.execute(mapOf("action" to "explode")).isSuccess)
    }
}
