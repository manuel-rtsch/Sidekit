package hrln.interactive.sidekit.sdk.media

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RemoteArtwork.load] runs on two threads and both routinely ask for the same picture, which is
 * how two writers reached the same `AtomicFile` temp path on the Sidephone. These tests pin the
 * sharing, the cancellation and the "a failure is not remembered" rules.
 *
 * The waiting tests use virtual time so they cannot flake. The two that cancel a caller use real
 * threads: whether a cancelled coroutine resumes is not something `advanceUntilIdle` decides.
 */
class SingleFlightTest {

    @Test
    fun `concurrent callers share one run`() = runTest {
        val runs = AtomicInteger()
        val flight = SingleFlight<String>(backgroundScope)
        val produce: suspend () -> String = {
            runs.incrementAndGet()
            delay(50)
            "art"
        }

        val results = List(3) { async { flight.run("key", produce) } }
        advanceUntilIdle()

        assertEquals(listOf("art", "art", "art"), results.awaitAll())
        assertEquals(1, runs.get())
    }

    @Test
    fun `a later caller runs again once the first finished`() = runTest {
        val runs = AtomicInteger()
        val flight = SingleFlight<String>(backgroundScope)
        val produce: suspend () -> String = {
            runs.incrementAndGet()
            "art"
        }

        assertEquals("art", flight.run("key", produce))
        assertEquals("art", flight.run("key", produce))

        assertEquals(2, runs.get())
    }

    @Test
    fun `a failure is shared but not remembered`() = runTest {
        val runs = AtomicInteger()
        val flight = SingleFlight<String>(backgroundScope)
        val failing: suspend () -> String = {
            runs.incrementAndGet()
            delay(50)
            error("no artwork")
        }

        val results = List(2) { async { flight.run("key", failing) } }
        advanceUntilIdle()

        assertNull(results[0].await())
        assertNull(results[1].await())
        assertEquals(1, runs.get())

        // A picture that failed to load must be tried again, or a row would stay blank.
        val retried = AtomicInteger()
        assertEquals(
            "art",
            flight.run("key") {
                retried.incrementAndGet()
                "art"
            }
        )
        assertEquals(1, retried.get())
    }

    @Test
    fun `keys do not wait for each other`() = runTest {
        val flight = SingleFlight<String>(backgroundScope)

        val slow = async {
            flight.run("slow") {
                delay(1_000)
                "slow"
            }
        }
        val fast = async { flight.run("fast") { "fast" } }
        advanceUntilIdle()

        assertEquals(listOf("slow", "fast"), listOf(slow.await(), fast.await()))
    }

    @Test
    fun `a caller that leaves does not cancel the run another is waiting for`() = runBlocking {
        val runs = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val flight = SingleFlight<String>(this)
        val produce: suspend () -> String = {
            runs.incrementAndGet()
            started.complete(Unit)
            gate.await()
            "art"
        }

        val leaving = launch(Dispatchers.Default) { flight.run("key", produce) }
        withTimeout(TIMEOUT_MS) { started.await() }
        val waiting = async(Dispatchers.Default) { flight.run("key", produce) }
        // Let the second caller reach the flight before the first one gives up.
        delay(JOIN_MS)

        leaving.cancelAndJoin()
        gate.complete(Unit)

        assertEquals("art", withTimeout(TIMEOUT_MS) { waiting.await() })
        assertEquals(1, runs.get())
    }

    @Test
    fun `the last caller leaving cancels the run and frees the key`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val flight = SingleFlight<String>(this)
        val produce: suspend () -> String = {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }

        val only = launch(Dispatchers.Default) { flight.run("key", produce) }
        withTimeout(TIMEOUT_MS) { started.await() }

        only.cancelAndJoin()
        withTimeout(TIMEOUT_MS) { cancelled.await() }

        // The abandoned call must not leave its key occupied.
        assertEquals("fresh", withTimeout(TIMEOUT_MS) { flight.run("key") { "fresh" } })
    }

    private companion object {
        /** Nothing here does real work, so waiting this long means a call was lost. */
        const val TIMEOUT_MS = 5_000L

        /** Time for a second caller to reach a flight that is already in progress. */
        const val JOIN_MS = 200L
    }
}
