package hrln.interactive.sidekit.apps.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpotifyUiState(
    val signedIn: Boolean = true,
    val current: SpotifyMediaItem? = null,
    val upcoming: List<SpotifyMediaItem> = emptyList(),
    val history: List<SpotifyMediaItem> = emptyList(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val savedTrackIds: Set<String> = MockSpotifyDataSource.liked.map { it.id }.toSet(),
    val savedCollectionKeys: Set<String> = setOf("playlist:late_night", "album:night_signals"),
    val followedArtistIds: Set<String> = setOf("aurora", "mira"),
    val filter: LibraryFilter = LibraryFilter.LikedSongs,
    val searchScopes: Set<SearchScope> = SearchScope.entries.toSet(),
    val query: String = "",
    val audioQuality: AudioQuality = AudioQuality.High,
    val volume: Float = 0.72f,
    val notice: String? = null
) {
    val savedCurrent: Boolean get() = current?.let { it.id in savedTrackIds } ?: false
}

/**
 * One state holder for the mockup. It owns the queue, a reasonable simulation of playback and
 * every optimistic save/follow, so navigation never resets the visible account.
 */
class SpotifyViewModel : ViewModel() {
    private val _state = MutableStateFlow(SpotifyUiState())
    val state: StateFlow<SpotifyUiState> = _state.asStateFlow()

    private var context: List<SpotifyMediaItem> = emptyList()
    private var contextIndex = 0

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                tick()
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun notice(text: String) = _state.update { it.copy(notice = text) }

    fun play(items: List<SpotifyMediaItem>, index: Int) {
        if (index !in items.indices) return
        val item = items[index]
        if (!item.playable) {
            notice("Not available on this account")
            return
        }
        val previous = _state.value.current
        context = items
        contextIndex = index
        _state.update {
            it.copy(
                current = item,
                upcoming = items.drop(index + 1),
                history = if (previous == null || previous.id == item.id) it.history else (listOf(previous) + it.history).take(HISTORY_LIMIT),
                isPlaying = true,
                positionMs = 0L,
                notice = null
            )
        }
    }

    fun playItem(item: SpotifyMediaItem) = play(listOf(item), 0)

    fun playCollection(collection: SpotifyCollection, index: Int) = play(collection.items, index)

    fun playArtist(artist: SpotifyArtist) = play(MockSpotifyDataSource.topTracks(artist), 0)

    fun playLibraryTrack(track: SpotifyTrack) {
        val recent = MockSpotifyDataSource.recentlyPlayed
        val at = recent.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        play(recent, at)
    }

    fun togglePlayPause() = _state.update {
        if (it.current == null) it else it.copy(isPlaying = !it.isPlaying)
    }

    fun next() = advance(auto = false)

    fun previous() {
        val s = _state.value
        if (s.current == null) return
        if (s.positionMs > 3_000L) {
            _state.update { it.copy(positionMs = 0L) }
            return
        }
        val previous = s.history.firstOrNull()
        if (previous == null) {
            _state.update { it.copy(positionMs = 0L) }
            return
        }
        _state.update {
            it.copy(
                current = previous,
                upcoming = listOfNotNull(s.current) + it.upcoming,
                history = it.history.drop(1),
                positionMs = 0L,
                isPlaying = previous.playable
            )
        }
    }

    fun seekTo(positionMs: Long) = _state.update {
        val duration = it.current?.durationMs ?: 0L
        it.copy(positionMs = positionMs.coerceIn(0L, duration))
    }

    fun toggleShuffle() = _state.update {
        val on = !it.shuffle
        it.copy(shuffle = on, upcoming = if (on) it.upcoming.shuffled() else it.upcoming)
    }

    fun cycleRepeat() = _state.update { it.copy(repeat = it.repeat.next()) }

    fun toggleSaveCurrent() {
        val item = _state.value.current ?: return
        _state.update {
            val saved = it.savedTrackIds.toMutableSet()
            if (!saved.add(item.id)) saved.remove(item.id)
            it.copy(savedTrackIds = saved, notice = if (item.id in saved) "Saved to Liked Songs" else "Removed from Liked Songs")
        }
    }

    fun toggleSaved(track: SpotifyTrack) = _state.update {
        val saved = it.savedTrackIds.toMutableSet()
        if (!saved.add(track.id)) saved.remove(track.id)
        it.copy(savedTrackIds = saved)
    }

    fun savedCollection(collection: SpotifyCollection): Boolean = collectionKey(collection) in _state.value.savedCollectionKeys

    fun toggleSaved(collection: SpotifyCollection) = _state.update {
        val keys = it.savedCollectionKeys.toMutableSet()
        val key = collectionKey(collection)
        val wasSaved = keys.contains(key)
        if (!keys.add(key)) keys.remove(key)
        it.copy(
            savedCollectionKeys = keys,
            notice = when (collection.type) {
                SpotifyCollectionType.Album -> if (wasSaved) "Removed from Library" else "Saved to Library"
                SpotifyCollectionType.Playlist -> if (wasSaved) "Removed from Library" else "Saved to Library"
                SpotifyCollectionType.Show -> if (wasSaved) "Removed from Library" else "Saved to Library"
            }
        )
    }

    fun followed(artist: SpotifyArtist): Boolean = artist.id in _state.value.followedArtistIds

    fun toggleFollow(artist: SpotifyArtist) = _state.update {
        val ids = it.followedArtistIds.toMutableSet()
        val wasFollowing = !ids.add(artist.id)
        if (wasFollowing) ids.remove(artist.id)
        it.copy(followedArtistIds = ids, notice = if (wasFollowing) "Unfollowed ${artist.name}" else "Following ${artist.name}")
    }

    fun setFilter(filter: LibraryFilter) = _state.update { it.copy(filter = filter) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun clearQuery() = _state.update { it.copy(query = "") }

    fun toggleScope(scope: SearchScope) = _state.update {
        val scopes = if (scope in it.searchScopes) it.searchScopes - scope else it.searchScopes + scope
        it.copy(searchScopes = scopes)
    }

    fun cycleAudioQuality() = _state.update { it.copy(audioQuality = it.audioQuality.next()) }

    fun setVolume(value: Float) = _state.update { it.copy(volume = value.coerceIn(0f, 1f)) }

    fun clearHistory() = _state.update {
        it.copy(history = emptyList(), notice = "Listening history cleared")
    }

    fun clearCache() = _state.update { it.copy(notice = "Artwork cache cleared") }

    fun addCurrentToPlaylist() = _state.update {
        it.copy(notice = "Added to Late Night Drive")
    }

    fun moveToNext(item: SpotifyMediaItem) = _state.update {
        if (item.id == it.current?.id) it else it.copy(upcoming = listOf(item) + it.upcoming.filterNot { queued -> queued.id == item.id })
    }

    fun removeUpcoming(item: SpotifyMediaItem) = _state.update {
        it.copy(upcoming = it.upcoming.filterNot { queued -> queued.id == item.id }, notice = "Removed from queue")
    }

    fun addToQueue(item: SpotifyMediaItem) = _state.update {
        it.copy(upcoming = it.upcoming + item, notice = "Added to queue")
    }

    fun clearUpcoming(): List<SpotifyMediaItem> {
        val removed = _state.value.upcoming
        _state.update { it.copy(upcoming = emptyList()) }
        return removed
    }

    fun restoreUpcoming(items: List<SpotifyMediaItem>) = _state.update { it.copy(upcoming = items + it.upcoming) }

    fun jumpTo(id: String) {
        val s = _state.value
        if (s.current?.id == id) {
            _state.update { it.copy(positionMs = 0L, isPlaying = true) }
            return
        }
        val at = s.upcoming.indexOfFirst { it.id == id }
        if (at >= 0) {
            val skipped = s.upcoming.take(at)
            val target = s.upcoming[at]
            _state.update {
                it.copy(
                    current = target,
                    upcoming = it.upcoming.drop(at + 1),
                    history = (listOfNotNull(it.current) + skipped).take(HISTORY_LIMIT),
                    positionMs = 0L,
                    isPlaying = target.playable
                )
            }
            return
        }
        s.history.firstOrNull { it.id == id }?.let { playItem(it) }
    }

    fun signOut() {
        _state.update {
            SpotifyUiState(
                signedIn = false,
                savedTrackIds = emptySet(),
                savedCollectionKeys = emptySet(),
                followedArtistIds = emptySet()
            )
        }
    }

    fun signIn() = _state.update { SpotifyUiState(signedIn = true) }

    private fun tick() {
        val s = _state.value
        val current = s.current ?: return
        if (!s.isPlaying) return
        val nextPosition = s.positionMs + 1_000L
        if (nextPosition < current.durationMs) {
            _state.update { it.copy(positionMs = nextPosition) }
        } else {
            when (s.repeat) {
                RepeatMode.Track -> _state.update { it.copy(positionMs = 0L) }
                RepeatMode.Context -> if (context.isNotEmpty()) play(context, 0) else advance(auto = true)
                RepeatMode.Off -> advance(auto = true)
            }
        }
    }

    private fun advance(auto: Boolean) {
        val s = _state.value
        val current = s.current ?: return
        val next = s.upcoming.firstOrNull()
        if (next != null) {
            _state.update {
                it.copy(
                    current = next,
                    upcoming = it.upcoming.drop(1),
                    history = (listOf(current) + it.history).take(HISTORY_LIMIT),
                    positionMs = 0L,
                    isPlaying = next.playable
                )
            }
            return
        }
        if (!auto) return
        when (s.repeat) {
            RepeatMode.Context -> if (context.isNotEmpty()) play(context, 0)
            RepeatMode.Track -> _state.update { it.copy(positionMs = 0L) }
            RepeatMode.Off -> _state.update { it.copy(isPlaying = false, positionMs = 0L) }
        }
    }

    private fun collectionKey(collection: SpotifyCollection) = "${collection.type.route}:${collection.id}"

    private companion object {
        const val HISTORY_LIMIT = 20
    }
}
