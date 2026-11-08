package hrln.interactive.sidekit.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UndoWindowTest {
    @Test
    fun `expires after five seconds of visible time`() = runTest {
        var expired = 0
        val window = UndoWindow(this, elapsed = { testScheduler.currentTime }) { expired++ }
        window.start()
        assertTrue(window.isPending)
        advanceTimeBy(4_999)
        assertEquals(0, expired)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, expired)
        assertFalse(window.isPending)
    }

    @Test
    fun `pausing stops the clock and resuming spends only the time that was left`() = runTest {
        var expired = 0
        val window = UndoWindow(this, elapsed = { testScheduler.currentTime }) { expired++ }
        window.start()
        advanceTimeBy(3_000)
        window.pause()
        advanceTimeBy(60_000)
        assertEquals(0, expired)
        assertTrue(window.isPending)
        window.resume()
        advanceTimeBy(1_999)
        assertEquals(0, expired)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, expired)
    }

    @Test
    fun `every pause resumes with the same remaining time`() = runTest {
        var expired = 0
        val window = UndoWindow(this, elapsed = { testScheduler.currentTime }) { expired++ }
        window.start()
        advanceTimeBy(2_000)
        window.pause()
        window.resume()
        advanceTimeBy(1_000)
        window.pause()
        advanceTimeBy(10_000)
        window.resume()
        advanceTimeBy(1_999)
        assertEquals(0, expired)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, expired)
    }

    @Test
    fun `cancelling drops the offer without expiring it`() = runTest {
        var expired = 0
        val window = UndoWindow(this, elapsed = { testScheduler.currentTime }) { expired++ }
        window.start()
        advanceTimeBy(1_000)
        window.cancel()
        advanceTimeBy(10_000)
        assertEquals(0, expired)
        assertFalse(window.isPending)
    }

    @Test
    fun `a second deletion replaces the first`() = runTest {
        var expired = 0
        val window = UndoWindow(this, elapsed = { testScheduler.currentTime }) { expired++ }
        window.start()
        advanceTimeBy(3_000)
        window.start()
        advanceTimeBy(3_000)
        assertEquals(0, expired)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, expired)
    }

    @Test
    fun `resume without an offer does nothing`() = runTest {
        var expired = 0
        val window = UndoWindow(this, elapsed = { testScheduler.currentTime }) { expired++ }
        window.resume()
        advanceTimeBy(10_000)
        assertEquals(0, expired)
        assertFalse(window.isPending)
    }
}
