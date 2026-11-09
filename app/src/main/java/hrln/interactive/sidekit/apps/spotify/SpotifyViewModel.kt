package hrln.interactive.sidekit.apps.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hrln.interactive.sidekit.apps.spotify.data.SpotifyCatalog
import hrln.interactive.sidekit.apps.spotify.data.SpotifyFailure
import hrln.interactive.sidekit.apps.spotify.data.SpotifyGatewayException
import hrln.interactive.sidekit.apps.spotify.data.SpotifyHistoryRepository
import hrln.interactive.sidekit.apps.spotify.data.SpotifyQueueRepository
import hrln.interactive.sidekit.apps.spotify.data.SpotifySession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SpotifyUiState(
    val signedIn: Boolean = false,
    val current: SpotifyMediaItem? = null,
    val upcoming: List<SpotifyMediaItem> = emptyList(),
    val history: List<SpotifyMediaItem> = emptyList(),
    val recent: List<SpotifyTrack> = emptyList(),
    val libraryItems: List<Any> = emptyList(),
    val searchResults: SpotifySearchResults = SpotifySearchResults(),
    val searchPending: Boolean = false,
    val authPending: Boolean = false,
    val libraryLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val savedTrackIds: Set<String> = emptySet(),
    val savedCollectionKeys: Set<String> = emptySet(),
    val followedArtistIds: Set<String> = emptySet(),
    val collections: Map<String, SpotifyCollection> = emptyMap(),
    val artists: Map<String, SpotifyArtist> = emptyMap(),
    val artistAlbums: Map<String, List<SpotifyAlbum>> = emptyMap(),
    val artistTracks: Map<String, List<SpotifyTrack>> = emptyMap(),
    val pendingCollections: Set<String> = emptySet(),
    val pendingArtists: Set<String> = emptySet(),
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
    private val catalog: SpotifyCatalog,
    private val history: SpotifyHistoryRepository = SpotifyHistoryRepository(),
    private val session: SpotifySession? = null,
    private val openBrowser: (suspend (String) -> Unit)? = null,
    private val clearArtworkCache: () -> Unit = {}
) : ViewModel() {
    private val _state = MutableStateFlow(SpotifyUiState(signedIn = session?.signedIn?.value ?: false))
    val state: StateFlow<SpotifyUiState> = _state.asStateFlow()

    private val queue = SpotifyQueueRepository()
    private var searchJob: Job? = null

    init {
        session?.let {
            viewModelScope.launch {
                it.signedIn.collect { signed ->
                    _state.update { state -> state.copy(signedIn = signed) }
                    if (signed) loadAccount() else clearAccount()
                }
            }
        }
        if (session == null || !session.nativeAvailable) {
            // Local fixtures and builds without the native library still need a moving progress bar.
            viewModelScope.launch {
                while (true) {
                    delay(1_000L)
                    tick()
                }
            }
        } else {
            viewModelScope.launch {
                while (true) {
                    delay(300L)
                    val raw = withContext(Dispatchers.IO) { session.pollPlaybackEvent() } ?: continue
                    handlePlaybackEvent(raw)
                }
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun refresh() = loadAccount()

    fun notice(text: String) = _state.update { it.copy(notice = text) }

    fun collection(type: SpotifyCollectionType, id: String): SpotifyCollection? = _state.value.collections[collectionKey(type, id)]

    fun artist(id: String): SpotifyArtist? = _state.value.artists[id]

    fun albumsBy(artist: SpotifyArtist): List<SpotifyAlbum> = _state.value.artistAlbums[artist.id].orEmpty()

    fun topTracks(artist: SpotifyArtist): List<SpotifyTrack> = _state.value.artistTracks[artist.id].orEmpty()

    fun ensureCollection(type: SpotifyCollectionType, id: String) {
        val key = collectionKey(type, id)
        val snapshot = _state.value
        if (key in snapshot.collections || key in snapshot.pendingCollections) return
        _state.update { it.copy(pendingCollections = it.pendingCollections + key) }
        viewModelScope.launch {
            try {
                val collection = catalog.collection(type, id)
                _state.update {
                    it.copy(
                        collections = if (collection == null) it.collections else it.collections + (key to collection),
                        pendingCollections = it.pendingCollections - key
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(pendingCollections = it.pendingCollections - key, notice = playbackNotice(e))
                }
            }
        }
    }

    fun ensureArtist(id: String) {
        val snapshot = _state.value
        if (id in snapshot.artists || id in snapshot.pendingArtists) return
        _state.update { it.copy(pendingArtists = it.pendingArtists + id) }
        viewModelScope.launch {
            try {
                val artist = catalog.artist(id)
                if (artist == null) {
                    _state.update { it.copy(pendingArtists = it.pendingArtists - id) }
                    return@launch
                }
                val albums = catalog.albumsBy(artist)
                val tracks = catalog.topTracks(artist)
                _state.update {
                    it.copy(
                        artists = it.artists + (id to artist),
                        artistAlbums = it.artistAlbums + (id to albums),
                        artistTracks = it.artistTracks + (id to tracks),
                        pendingArtists = it.pendingArtists - id
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(pendingArtists = it.pendingArtists - id, notice = playbackNotice(e))
                }
            }
        }
    }

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
        startRemote(item, 0L)
    }

    fun playItem(item: SpotifyMediaItem) = play(listOf(item), 0)

    fun playCollection(collection: SpotifyCollection, index: Int) = play(collection.items, index)

    fun playArtist(artist: SpotifyArtist) = play(topTracks(artist), 0)

    fun playLibraryTrack(track: SpotifyTrack) {
        val recent = _state.value.recent
        val at = recent.indexOfFirst { it.id == track.id }
        if (at >= 0) play(recent, at) else playItem(track)
    }

    fun togglePlayPause() {
        val s = _state.value
        val item = s.current ?: return
        val play = !s.isPlaying
        _state.update { it.copy(isPlaying = play) }
        if (item.uri == null || session == null) return
        viewModelScope.launch {
            try {
                if (play) session.resume() else session.pause()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isPlaying = false, notice = playbackNotice(e)) }
            }
        }
    }

    fun next() {
        val before = _state.value.current
        val snapshot = queue.next(auto = false)
        if (snapshot.current !== before) {
            snapshot.current?.let(::recorded)
            applyQueue(snapshot, positionMs = 0L, playing = snapshot.current?.playable == true)
            snapshot.current?.let { startRemote(it, 0L) }
        }
    }

    fun previous() {
        val s = _state.value
        if (s.current == null) return
        if (s.positionMs > 3_000L) {
            _state.update { it.copy(positionMs = 0L) }
            s.current?.uri?.let { uri ->
                session?.let { session ->
                    viewModelScope.launch { runCatching { session.seek(0L) } }
                }
            }
            return
        }
        val snapshot = queue.previous(s.positionMs)
        if (snapshot.current !== s.current) {
            snapshot.current?.let(::recorded)
            applyQueue(snapshot, positionMs = 0L, playing = snapshot.current?.playable == true)
            snapshot.current?.let { startRemote(it, 0L) }
        } else {
            _state.update { it.copy(positionMs = 0L) }
            s.current?.uri?.let { uri ->
                session?.let { session ->
                    viewModelScope.launch { runCatching { session.seek(0L) } }
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val current = _state.value.current ?: return
        val target = positionMs.coerceIn(0L, current.durationMs)
        _state.update { it.copy(positionMs = target) }
        if (current.uri != null && session != null) {
            viewModelScope.launch { runCatching { session.seek(target) } }
        }
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

    fun savedCollection(collection: SpotifyCollection): Boolean = collectionKey(collection.type, collection.id) in _state.value.savedCollectionKeys

    fun toggleSaved(collection: SpotifyCollection) = _state.update {
        val keys = it.savedCollectionKeys.toMutableSet()
        val key = collectionKey(collection.type, collection.id)
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

    fun setFilter(filter: LibraryFilter) {
        _state.update { it.copy(filter = filter) }
        loadLibrary(filter)
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

    fun setVolume(value: Float) {
        val volume = value.coerceIn(0f, 1f)
        _state.update { it.copy(volume = volume) }
        session?.let { session ->
            viewModelScope.launch { runCatching { session.setVolume(volume) } }
        }
    }

    fun clearHistory() {
        history.clear()
        val snapshot = queue.clearHistory()
        applyQueue(snapshot)
        _state.update { it.copy(recent = emptyList(), notice = "Listening history cleared") }
    }

    fun clearCache() {
        clearArtworkCache()
        _state.update { it.copy(notice = "Artwork cache cleared") }
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
            snapshot.current?.let { startRemote(it, 0L) }
        }
    }

    fun signOut() {
        session?.signOut()
        queue.stop()
        _state.update { SpotifyUiState(signedIn = false, recent = history.items().filterIsInstance<SpotifyTrack>()) }
    }

    fun signIn() {
        val session = session
        val openBrowser = openBrowser
        if (session == null || openBrowser == null) {
            _state.update { it.copy(notice = "Sign-in is unavailable in this build") }
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
        if (!_state.value.signedIn) return
        _state.update { it.copy(libraryLoading = true) }
        viewModelScope.launch {
            try {
                val recent = catalog.recentlyPlayed()
                val library = catalog.library(_state.value.filter)
                _state.update {
                    it.copy(
                        recent = recent.ifEmpty { history.items().filterIsInstance<SpotifyTrack>() },
                        libraryItems = library,
                        libraryLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(libraryLoading = false, notice = playbackNotice(e)) }
            }
        }
    }

    private fun loadLibrary(filter: LibraryFilter) {
        if (!_state.value.signedIn) return
        _state.update { it.copy(libraryLoading = true) }
        viewModelScope.launch {
            try {
                val items = catalog.library(filter)
                _state.update {
                    if (it.filter == filter) it.copy(libraryItems = items, libraryLoading = false) else it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(libraryLoading = false, notice = playbackNotice(e)) }
            }
        }
    }

    private fun clearAccount() = _state.update {
        it.copy(
            recent = emptyList(),
            libraryItems = emptyList(),
            searchResults = SpotifySearchResults(),
            collections = emptyMap(),
            artists = emptyMap(),
            artistAlbums = emptyMap(),
            artistTracks = emptyMap(),
            savedTrackIds = emptySet(),
            savedCollectionKeys = emptySet(),
            followedArtistIds = emptySet()
        )
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
            val results = try {
                if (_state.value.signedIn) catalog.search(query, scopes) else SpotifySearchResults()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(notice = playbackNotice(e)) }
                SpotifySearchResults()
            }
            _state.update {
                if (it.query == query && it.searchScopes == scopes) {
                    it.copy(searchResults = results, searchPending = false)
                } else {
                    it
                }
            }
        }
    }

    /**
     * librespot is the source of truth while it is playing: its position events keep the seek bar
     * honest through buffering and seeks, and EndOfTrack advances the Sidekit queue exactly once.
     */
    private fun handlePlaybackEvent(raw: String) {
        val event = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val position = event.optLong("positionMs", _state.value.positionMs)
        when (event.optString("type")) {
            "loading" -> if (matchesCurrent(event)) {
                _state.update { it.copy(positionMs = position) }
            }

            "playing" -> if (matchesCurrent(event)) {
                _state.update { it.copy(isPlaying = true, positionMs = position) }
            }

            "paused" -> if (matchesCurrent(event)) {
                _state.update { it.copy(isPlaying = false, positionMs = position) }
            }

            "position" -> _state.update { it.copy(positionMs = position) }

            "ended" -> if (matchesCurrent(event)) onPlaybackEnded()

            "unavailable" -> if (matchesCurrent(event)) {
                _state.update { it.copy(isPlaying = false, notice = "This track isn't available") }
            }
        }
    }

    private fun matchesCurrent(event: JSONObject): Boolean {
        val uri = event.optString("uri").takeIf { it.isNotEmpty() } ?: return true
        return _state.value.current?.uri?.equals(uri, ignoreCase = true) ?: true
    }

    private fun onPlaybackEnded() {
        val snapshot = _state.value
        val current = snapshot.current ?: return
        if (snapshot.repeat == RepeatMode.Track) {
            _state.update { it.copy(positionMs = 0L, isPlaying = true) }
            startRemote(current, 0L)
        } else {
            advance(auto = true, forceContextLoop = snapshot.repeat == RepeatMode.Context)
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
            snapshot.current?.let { startRemote(it, 0L) }
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
            snapshot.current?.let { startRemote(it, 0L) }
        } else {
            _state.update { it.copy(isPlaying = false, positionMs = 0L) }
        }
    }

    /** Starts a real Spotify item; local test fixtures have no URI and keep the local simulation. */
    private fun startRemote(item: SpotifyMediaItem, positionMs: Long) {
        val uri = item.uri ?: return
        val session = session ?: return
        viewModelScope.launch {
            try {
                session.play(uri, positionMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isPlaying = false, notice = playbackNotice(e)) }
            }
        }
    }

    private fun playbackNotice(error: Exception): String = when ((error as? SpotifyGatewayException)?.failure) {
        SpotifyFailure.Unavailable -> "Spotify playback is unavailable in this build"
        SpotifyFailure.NotConnected -> "Playback is not ready — try again"
        SpotifyFailure.AuthenticationExpired -> "Spotify session expired — sign in again"
        SpotifyFailure.Network -> "Couldn't reach Spotify"
        SpotifyFailure.RateLimited -> "Spotify is rate-limiting requests — try again shortly"
        SpotifyFailure.TrackUnavailable -> "This track isn't available"
        SpotifyFailure.Unknown, null -> error.message ?: "Playback failed"
    }

    private fun collectionKey(type: SpotifyCollectionType, id: String) = "${type.route}:$id"
}
