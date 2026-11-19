package hrln.interactive.sidekit.sdk.media

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs [produce] once per key while a call for that key is already in flight.
 *
 * Callers of the same key share one [produce] and one result; the work belongs to the flight, not
 * to whichever caller happened to arrive first. A caller that leaves only gives up its own wait,
 * and the flight is cancelled when the last one is gone, so an abandoned picture still drops its
 * socket.
 */
internal class SingleFlight<T : Any>(private val scope: CoroutineScope) {

    private val flights = ConcurrentHashMap<String, Flight<T>>()

    /** Returns [produce]'s value for [key], joining the call already running for it if any. */
    suspend fun run(key: String, produce: suspend () -> T?): T? {
        val flight = flights.compute(key) { _, current -> current ?: Flight() }!!
        start(key, flight, produce)
        return try {
            flight.result.await()
        } finally {
            if (flight.release()) flight.job?.cancel()
        }
    }

    private fun start(key: String, flight: Flight<T>, produce: suspend () -> T?) {
        synchronized(flight) {
            flight.retain()
            if (flight.job != null) return
            flight.job = scope.launch {
                try {
                    flight.result.complete(produce())
                } catch (e: CancellationException) {
                    // A cancelled flight must not leave late joiners waiting forever.
                    flight.result.complete(null)
                    throw e
                } catch (_: Throwable) {
                    flight.result.complete(null)
                } finally {
                    // Two-argument remove: a newer flight for the same key must survive.
                    flights.remove(key, flight)
                }
            }
        }
    }

    private class Flight<T : Any> {
        val result = CompletableDeferred<T?>()

        /** Callers currently waiting; the work runs while this is above zero. */
        private val waiters = AtomicInteger()

        var job: Job? = null

        fun retain() {
            waiters.incrementAndGet()
        }

        /** Returns true when the caller that just left was the last one. */
        fun release(): Boolean = waiters.decrementAndGet() <= 0
    }
}
