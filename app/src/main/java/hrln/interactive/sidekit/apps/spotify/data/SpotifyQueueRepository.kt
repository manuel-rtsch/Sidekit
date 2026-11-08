package hrln.interactive.sidekit.apps.spotify.data

import hrln.interactive.sidekit.apps.spotify.RepeatMode
import hrln.interactive.sidekit.apps.spotify.SpotifyMediaItem
import kotlin.random.Random

/**
 * The queue is owned by Sidekit, not librespot: librespot plays one item at a time and this
 * repository decides what comes next.
 *
 * History and upcoming are plain ordered lists. Shuffle is a view over upcoming only, so the
 * current item and history never move. The repository is deliberately free of Android and
 * Compose types; playback, persistence and the UI observe it through [snapshot].
 */
class SpotifyQueueRepository(private val random: Random = Random.Default) {
    data class Snapshot(
        val current: SpotifyMediaItem? = null,
        val upcoming: List<SpotifyMediaItem> = emptyList(),
        val history: List<SpotifyMediaItem> = emptyList(),
        val shuffle: Boolean = false,
        val repeat: RepeatMode = RepeatMode.Off,
        val context: List<SpotifyMediaItem> = emptyList()
    )

    var snapshot: Snapshot = Snapshot()
        private set

    private var shuffleBackup: List<SpotifyMediaItem>? = null

    /**
     * Starts playback from [index] within [items], replacing the queue. The rows after the
     * chosen item become upcoming; the item that was playing moves into history.
     */
    fun play(items: List<SpotifyMediaItem>, index: Int): Snapshot {
        if (index !in items.indices) return snapshot
        val item = items[index]
        if (!item.playable) return snapshot

        val previous = snapshot.current
        val nextUpcoming = items.drop(index + 1).let { if (snapshot.shuffle) it.shuffled(random) else it }
        snapshot = snapshot.copy(
            current = item,
            upcoming = nextUpcoming,
            history = if (previous == null || previous.id == item.id) {
                snapshot.history
            } else {
                (listOf(previous) + snapshot.history).take(HISTORY_LIMIT)
            },
            context = items
        )
        shuffleBackup = if (snapshot.shuffle) items.drop(index + 1) else null
        return snapshot
    }

    fun playItem(item: SpotifyMediaItem): Snapshot = play(listOf(item), 0)

    /** Stops without clearing the queue; the current item stays so the user can resume. */
    fun stop(): Snapshot {
        snapshot = snapshot.copy(current = null, upcoming = emptyList(), history = emptyList(), context = emptyList())
        shuffleBackup = null
        return snapshot
    }

    fun toggleShuffle(): Snapshot {
        if (snapshot.shuffle) {
            snapshot = snapshot.copy(shuffle = false, upcoming = shuffleBackup ?: snapshot.upcoming)
            shuffleBackup = null
        } else {
            shuffleBackup = snapshot.upcoming
            snapshot = snapshot.copy(shuffle = true, upcoming = snapshot.upcoming.shuffled(random))
        }
        return snapshot
    }

    fun clearHistory(): Snapshot {
        snapshot = snapshot.copy(history = emptyList())
        return snapshot
    }

    fun cycleRepeat(): Snapshot {
        snapshot = snapshot.copy(repeat = snapshot.repeat.next())
        return snapshot
    }

    fun setRepeat(mode: RepeatMode): Snapshot {
        snapshot = snapshot.copy(repeat = mode)
        return snapshot
    }

    /**
     * Moves to the next upcoming item. [auto] distinguishes the end of a track from the user
     * pressing Next: an empty queue only stops automatically.
     */
    fun next(auto: Boolean): Snapshot {
        val current = snapshot.current ?: return snapshot
        if (auto && snapshot.repeat == RepeatMode.Track) return snapshot
        val next = snapshot.upcoming.firstOrNull()
        if (next != null) {
            snapshot = snapshot.copy(
                current = next,
                upcoming = snapshot.upcoming.drop(1),
                history = (listOf(current) + snapshot.history).take(HISTORY_LIMIT)
            )
            return snapshot
        }
        if (!auto) return snapshot
        return when (snapshot.repeat) {
            RepeatMode.Context -> replayContext()

            RepeatMode.Track -> snapshot

            RepeatMode.Off -> {
                snapshot = snapshot.copy(current = null, history = snapshot.history)
                snapshot
            }
        }
    }

    /** User pressed Previous. Restarts when past the 3 s threshold. */
    fun previous(positionMs: Long): Snapshot {
        if (snapshot.current == null) return snapshot
        if (positionMs > RESTART_THRESHOLD_MS) return snapshot
        val previous = snapshot.history.firstOrNull() ?: return snapshot
        snapshot = snapshot.copy(
            current = previous,
            upcoming = listOfNotNull(snapshot.current) + snapshot.upcoming,
            history = snapshot.history.drop(1)
        )
        return snapshot
    }

    fun jumpTo(id: String): Snapshot {
        val current = snapshot.current ?: return snapshot
        if (current.id == id) return snapshot
        val at = snapshot.upcoming.indexOfFirst { it.id == id }
        if (at >= 0) {
            val skipped = snapshot.upcoming.take(at)
            val target = snapshot.upcoming[at]
            snapshot = snapshot.copy(
                current = target,
                upcoming = snapshot.upcoming.drop(at + 1),
                history = (listOf(current) + skipped + snapshot.history).take(HISTORY_LIMIT)
            )
            return snapshot
        }
        snapshot.history.firstOrNull { it.id == id }?.let { playItem(it) }
        return snapshot
    }

    /** Inserts directly after the current item; an id already upcoming is moved, not duplicated. */
    fun playNext(item: SpotifyMediaItem): Snapshot {
        if (item.id == snapshot.current?.id) return snapshot
        val remaining = snapshot.upcoming.filterNot { it.id == item.id }
        snapshot = snapshot.copy(upcoming = listOf(item) + remaining)
        return snapshot
    }

    fun addToQueue(item: SpotifyMediaItem): Snapshot {
        snapshot = snapshot.copy(upcoming = snapshot.upcoming + item)
        return snapshot
    }

    fun removeUpcoming(item: SpotifyMediaItem): Snapshot {
        snapshot = snapshot.copy(upcoming = snapshot.upcoming.filterNot { it.id == item.id })
        return snapshot
    }

    /** Removes every upcoming item and returns them so the caller can offer Undo. */
    fun clearUpcoming(): List<SpotifyMediaItem> {
        val removed = snapshot.upcoming
        snapshot = snapshot.copy(upcoming = emptyList())
        shuffleBackup = null
        return removed
    }

    /** Restores a cleared tail in front of whatever was appended in the meantime. */
    fun restoreUpcoming(items: List<SpotifyMediaItem>): Snapshot {
        snapshot = snapshot.copy(upcoming = items + snapshot.upcoming)
        return snapshot
    }

    private fun replayContext(): Snapshot {
        val items = snapshot.context
        if (items.isEmpty()) return snapshot.copy(current = null)
        val first = items.first()
        snapshot = snapshot.copy(
            current = first,
            upcoming = items.drop(1).let { if (snapshot.shuffle) it.shuffled(random) else it },
            history = snapshot.history
        )
        shuffleBackup = if (snapshot.shuffle) items.drop(1) else null
        return snapshot
    }

    private companion object {
        const val HISTORY_LIMIT = 20
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
