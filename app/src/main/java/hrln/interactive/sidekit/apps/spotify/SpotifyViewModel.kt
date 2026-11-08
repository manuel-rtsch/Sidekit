package hrln.interactive.sidekit.apps.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hrln.interactive.sidekit.apps.spotify.data.SpotifyCatalog
import hrln.interactive.sidekit.apps.spotify.data.SpotifyHistoryRepository
import hrln.interactive.sidekit.apps.spotify.data.SpotifyQueueRepository
import hrln.interactive.sidekit.apps.spotify.data.SpotifySession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val recent: List<SpotifyTrack> = emptyList(),
    val libraryItems: List<Any> = emptyList(),
    val searchResults: SpotifySearchResults = SpotifySearchResults(),
    val searchPending: Boolean = false,
    val authPending: Boolean = false,
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
 * State holder for the Sidetracks UI. Catalog reads, queue ordering and local history live in
 * their own domain objects; this class only maps them to the single immutable screen state and
 * implements the optimistic save/follow behaviour the action bars expect.
 */
class SpotifyViewModel(
    private val catalog: SpotifyCatalog = MockSpotifyDataSource,
    private val history: SpotifyHistoryRepository = SpotifyHistoryRepository(),
    private val session: SpotifySession? = null,
    private val openBrowser: (suspend (String) -> Unit)? = null
) : ViewModel() {
    private val _state = MutableStateFlow(SpotifyUiState(signedIn = session?.signedIn?.value ?: true))
    val state: StateFlow<SpotifyUiState> = _state.asStateFlow()

    private val queue = SpotifyQueueRepository()
    private var searchJob: Job? = null

    init {
        session?.let {
            viewModelScope.launch {
                it.signedIn.collect { signed -> _state.update { state -> state.copy(signedIn = signed) } }
            }
        }
        if (history.items().isEmpty()) {
            // Seed the reference account in the same order the fixture lists it: most recent first.
            catalog.recentlyPlayed().asReversed().forEach { history.record(it, 0L) }
        }
        loadAccount()
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                tick()
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun notice(text: String) = _state.update { it.copy(notice = text) }

    fun collection(type: SpotifyCollectionType, id: String): SpotifyCollection? = catalog.collection(type, id)

    fun artist(id: String): SpotifyArtist? = catalog.artist(id)

    fun albumsBy(artist: SpotifyArtist): List<SpotifyAlbum> = catalog.albumsBy(artist)

    fun topTracks(artist: SpotifyArtist): List<SpotifyTrack> = catalog.topTracks(artist)

    fun play(items: List<SpotifyMediaItem>, index: Int) {
        if (index !in items.indices) return
        val item = items[index]
        if (!item.playable) {
            notice("Not available on this account")
            return
        }
        val snapshot = queue.play(items, index)
        recorded(item)
        applyQueue(snapshot, positionMs = 0L, playing = true)
        _state.update { it.copy(notice = null) }
    }

    fun playItem(item: SpotifyMediaItem) = play(listOf(item), 0)

    fun playCollection(collection: SpotifyCollection, index: Int) = play(collection.items, index)

    fun playArtist(artist: SpotifyArtist) = play(catalog.topTracks(artist), 0)

    fun playLibraryTrack(track: SpotifyTrack) {
        val recent = _state.value.recent
        val at = recent.indexOfFirst { it.id == track.id }
        if (at >= 0) play(recent, at) else playItem(track)
    }

    fun togglePlayPause() = _state.update {
        if (it.current == null) it else it.copy(isPlaying = !it.isPlaying)
    }

    fun next() {
        val before = _state.value.current
        val snapshot = queue.next(auto = false)
        if (snapshot.current !== before) {
            snapshot.current?.let(::recorded)
            applyQueue(snapshot, positionMs = 0L, playing = snapshot.current?.playable == true)
        }
    }

    fun previous() {
        val s = _state.value
        if (s.current == null) return
        if (s.positionMs > 3_000L) {
            _state.update { it.copy(positionMs = 0L) }
            return
        }
        val snapshot = queue.previous(s.positionMs)
        if (snapshot.current !== s.current) {
            snapshot.current?.let(::recorded)
            applyQueue(snapshot, positionMs = 0L, playing = snapshot.current?.playable == true)
        } else {
            _state.update { it.copy(positionMs = 0L) }
        }
    }

    fun seekTo(positionMs: Long) = _state.update {
        val duration = it.current?.durationMs ?: 0L
        it.copy(positionMs = positionMs.coerceIn(0L, duration))
    }

    fun toggleShuffle() = applyQueue(queue.toggleShuffle())

    fun cycleRepeat() = applyQueue(queue.cycleRepeat())

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

    fun setFilter(filter: LibraryFilter) = _state.update {
        it.copy(filter = filter, libraryItems = catalog.library(filter))
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        search(debounceMs = 250L)
    }

    fun clearQuery() {
        searchJob?.cancel()
        _state.update { it.copy(query = "", searchResults = SpotifySearchResults(), searchPending = false) }
    }

    fun toggleScope(scope: SearchScope) {
        _state.update {
            val scopes = if (scope in it.searchScopes) it.searchScopes - scope else it.searchScopes + scope
            it.copy(searchScopes = scopes)
        }
        search(debounceMs = 0L)
    }

    fun cycleAudioQuality() = _state.update { it.copy(audioQuality = it.audioQuality.next()) }

    fun setVolume(value: Float) = _state.update { it.copy(volume = value.coerceIn(0f, 1f)) }

    fun clearHistory() {
        history.clear()
        val snapshot = queue.clearHistory()
        applyQueue(snapshot)
        _state.update { it.copy(recent = emptyList(), notice = "Listening history cleared") }
    }

    fun clearCache() = _state.update { it.copy(notice = "Artwork cache cleared") }

    fun addCurrentToPlaylist() = _state.update {
        it.copy(notice = "Added to Late Night Drive")
    }

    fun moveToNext(item: SpotifyMediaItem) {
        applyQueue(queue.playNext(item))
        _state.update { it.copy(notice = "Playing next") }
    }

    fun removeUpcoming(item: SpotifyMediaItem) {
        applyQueue(queue.removeUpcoming(item))
        _state.update { it.copy(notice = "Removed from queue") }
    }

    fun addToQueue(item: SpotifyMediaItem) {
        applyQueue(queue.addToQueue(item))
        _state.update { it.copy(notice = "Added to queue") }
    }

    fun clearUpcoming(): List<SpotifyMediaItem> {
        val removed = queue.clearUpcoming()
        applyQueue(queue.snapshot)
        return removed
    }

    fun restoreUpcoming(items: List<SpotifyMediaItem>) = applyQueue(queue.restoreUpcoming(items))

    fun jumpTo(id: String) {
        val s = _state.value
        if (s.current?.id == id) {
            _state.update { it.copy(positionMs = 0L, isPlaying = true) }
            return
        }
        val snapshot = queue.jumpTo(id)
        if (snapshot.current !== s.current) {
            snapshot.current?.let(::recorded)
            applyQueue(snapshot, positionMs = 0L, playing = snapshot.current?.playable == true)
        }
    }

    fun signOut() {
        session?.signOut()
        queue.stop()
        _state.update {
            SpotifyUiState(
                signedIn = false,
                savedTrackIds = emptySet(),
                savedCollectionKeys = emptySet(),
                followedArtistIds = emptySet(),
                recent = history.items().filterIsInstance<SpotifyTrack>()
            )
        }
    }

    fun signIn() {
        val session = session
        val openBrowser = openBrowser
        if (session == null || openBrowser == null) {
            // Screenshot and unit builds keep the local reference account.
            _state.update { SpotifyUiState(signedIn = true) }
            loadAccount()
            return
        }
        if (_state.value.authPending) return
        _state.update { it.copy(authPending = true, notice = null) }
        viewModelScope.launch {
            try {
                session.signIn(openBrowser)
                _state.update { it.copy(signedIn = true, authPending = false) }
                loadAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(authPending = false, notice = e.message ?: "Sign-in failed")
                }
            }
        }
    }

    private fun loadAccount() {
        _state.update {
            it.copy(
                recent = history.items().filterIsInstance<SpotifyTrack>().ifEmpty { catalog.recentlyPlayed() },
                libraryItems = catalog.library(it.filter)
            )
        }
    }

    private fun applyQueue(
        snapshot: SpotifyQueueRepository.Snapshot,
        positionMs: Long? = null,
        playing: Boolean? = null
    ) = _state.update {
        it.copy(
            current = snapshot.current,
            upcoming = snapshot.upcoming,
            history = snapshot.history,
            shuffle = snapshot.shuffle,
            repeat = snapshot.repeat,
            positionMs = positionMs ?: it.positionMs,
            isPlaying = playing ?: it.isPlaying
        )
    }

    private fun recorded(item: SpotifyMediaItem) {
        if (item !is SpotifyTrack) return
        history.record(item, System.currentTimeMillis())
        _state.update { it.copy(recent = history.items().filterIsInstance<SpotifyTrack>()) }
    }

    private fun search(debounceMs: Long) {
        searchJob?.cancel()
        val query = _state.value.query
        if (query.trim().length < 2) {
            _state.update { it.copy(searchResults = SpotifySearchResults(), searchPending = false) }
            return
        }
        _state.update { it.copy(searchPending = true) }
        searchJob = viewModelScope.launch {
            delay(debounceMs)
            val scopes = _state.value.searchScopes
            val results = catalog.search(query, scopes)
            _state.update {
                if (it.query == query && it.searchScopes == scopes) {
                    it.copy(searchResults = results, searchPending = false)
                } else {
                    it
                }
            }
        }
    }

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
                RepeatMode.Context -> advance(auto = true, forceContextLoop = true)
                RepeatMode.Off -> advance(auto = true, forceContextLoop = false)
            }
        }
    }

    private fun advance(auto: Boolean, forceContextLoop: Boolean) {
        val s = _state.value
        val current = s.current ?: return
        val hasNext = s.upcoming.isNotEmpty()
        if (!hasNext && forceContextLoop) {
            val snapshot = queue.next(auto = true)
            snapshot.current?.let(::recorded)
            applyQueue(snapshot, positionMs = 0L, playing = snapshot.current?.playable == true)
            return
        }
        if (!hasNext && !auto) return
        if (!hasNext) {
            _state.update { it.copy(isPlaying = false, positionMs = 0L) }
            return
        }
        val snapshot = queue.next(auto = true)
        if (snapshot.current !== current) {
            snapshot.current?.let(::recorded)
            applyQueue(snapshot, positionMs = 0L, playing = snapshot.current?.playable == true)
        } else {
            _state.update { it.copy(isPlaying = false, positionMs = 0L) }
        }
    }

    private fun collectionKey(collection: SpotifyCollection) = "${collection.type.route}:${collection.id}"
}
