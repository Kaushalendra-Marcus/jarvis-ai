package ai.jarvis.app.service.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskRunGateTest {

    @Before
    fun resetStore() {
        TaskRunGate.store = MemoryRunTokenStore()
    }

    @Test
    fun firstClaimWinsDuplicateLoses() {
        TaskRunGate.expect("task-a", 1_000L)
        assertTrue(TaskRunGate.claim("task-a", 1_000L))
        assertFalse(TaskRunGate.claim("task-a", 1_000L))
    }

    @Test
    fun staleTokenNeverRuns() {
        TaskRunGate.expect("task-a", 2_000L)
        assertFalse(TaskRunGate.claim("task-a", 1_000L))
        assertTrue(TaskRunGate.claim("task-a", 2_000L))
    }

    @Test
    fun releaseLetsRetryReclaim() {
        TaskRunGate.expect("task-a", 3_000L)
        assertTrue(TaskRunGate.claim("task-a", 3_000L))
        TaskRunGate.release("task-a", 3_000L)
        assertTrue(TaskRunGate.claim("task-a", 3_000L))
    }

    @Test
    fun releaseOfOtherTokenIsIgnored() {
        TaskRunGate.expect("task-a", 4_000L)
        assertTrue(TaskRunGate.claim("task-a", 4_000L))
        TaskRunGate.release("task-a", 9_999L)
        assertFalse(TaskRunGate.claim("task-a", 4_000L))
    }

    @Test
    fun zeroTokenAlwaysPassesForBackwardCompatibility() {
        assertTrue(TaskRunGate.claim("legacy-task", 0L))
        assertTrue(TaskRunGate.claim("legacy-task", 0L))
    }

    @Test
    fun newExpectationReplacesOld() {
        TaskRunGate.expect("task-a", 5_000L)
        TaskRunGate.expect("task-a", 6_000L)
        assertFalse(TaskRunGate.claim("task-a", 5_000L))
        assertTrue(TaskRunGate.claim("task-a", 6_000L))
    }
}
