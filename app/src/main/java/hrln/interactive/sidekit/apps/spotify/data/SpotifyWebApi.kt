package hrln.interactive.sidekit.apps.spotify.data

import hrln.interactive.sidekit.apps.spotify.LibraryFilter
import hrln.interactive.sidekit.apps.spotify.SearchScope
import hrln.interactive.sidekit.apps.spotify.SpotifyAlbum
import hrln.interactive.sidekit.apps.spotify.SpotifyArtist
import hrln.interactive.sidekit.apps.spotify.SpotifyCollection
import hrln.interactive.sidekit.apps.spotify.SpotifyCollectionType
import hrln.interactive.sidekit.apps.spotify.SpotifyEpisode
import hrln.interactive.sidekit.apps.spotify.SpotifyPlaylist
import hrln.interactive.sidekit.apps.spotify.SpotifySearchResults
import hrln.interactive.sidekit.apps.spotify.SpotifyShow
import hrln.interactive.sidekit.apps.spotify.SpotifyTrack
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Spotify Web API reads the app needs. It deliberately returns UI models, not raw JSON: the
 * screens never learn an endpoint or a field name.
 */
class SpotifyWebApi(private val accessToken: suspend () -> String?) {
    suspend fun recentlyPlayed(limit: Int = 10): List<SpotifyTrack> {
        val json = get("$ME_RECENT?limit=${limit.coerceIn(1, 50)}", token())
        val items = json.optJSONArray("items") ?: return emptyList()
        return buildList(items.length()) {
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.optJSONObject("track")?.let(::track)?.let(::add)
            }
        }
    }

    suspend fun library(filter: LibraryFilter): List<Any> = when (filter) {
        LibraryFilter.LikedSongs -> tracks("$ME_TRACKS?limit=50")
        LibraryFilter.Playlists -> playlists("$ME_PLAYLISTS?limit=50")
        LibraryFilter.Albums -> savedAlbums()
        LibraryFilter.Artists -> followedArtists()
        LibraryFilter.Shows -> savedShows()
    }

    suspend fun collection(type: SpotifyCollectionType, id: String): SpotifyCollection? = when (type) {
        SpotifyCollectionType.Album -> runCatching { get("$ALBUMS/${enc(id)}", token()) }.getOrNull()?.let(::albumCollection)
        SpotifyCollectionType.Playlist -> runCatching { get("$PLAYLISTS/${enc(id)}", token()) }.getOrNull()?.let(::playlistCollection)
        SpotifyCollectionType.Show -> runCatching { get("$SHOWS/${enc(id)}", token()) }.getOrNull()?.let(::showCollection)
    }

    suspend fun search(query: String, scopes: Set<SearchScope>, limit: Int = 20): SpotifySearchResults {
        if (query.trim().length < 2) return SpotifySearchResults()
        val types = buildList {
            if (SearchScope.Tracks in scopes) add("track")
            if (SearchScope.Artists in scopes) add("artist")
            if (SearchScope.Albums in scopes) add("album")
            if (SearchScope.Playlists in scopes) add("playlist")
            if (SearchScope.Shows in scopes) add("show")
        }
        if (types.isEmpty()) return SpotifySearchResults()
        val json = get(
            "$SEARCH?q=${enc(query.trim())}&type=${types.joinToString(",")}&limit=${limit.coerceIn(1, 50)}&market=from_token",
            token()
        )
        return SpotifySearchResults(
            tracks = json.optJSONObject("tracks")?.optJSONArray("items").tracks(),
            artists = json.optJSONObject("artists")?.optJSONArray("items").artists(),
            albums = json.optJSONObject("albums")?.optJSONArray("items").albums(),
            playlists = json.optJSONObject("playlists")?.optJSONArray("items").playlists(),
            shows = json.optJSONObject("shows")?.optJSONArray("items").shows()
        )
    }

    suspend fun artist(id: String): SpotifyArtist? = runCatching {
        artist(get("$ARTISTS/${enc(id)}", token()))
    }.getOrNull()

    suspend fun albumsBy(artist: SpotifyArtist): List<SpotifyAlbum> = runCatching {
        val json = get("$ARTISTS/${enc(artist.id)}/albums?include_groups=album,single&limit=50&market=from_token", token())
        json.optJSONArray("items").albums()
    }.getOrDefault(emptyList())

    suspend fun topTracks(artist: SpotifyArtist): List<SpotifyTrack> = runCatching {
        val json = get("$ARTISTS/${enc(artist.id)}/top-tracks?market=from_token", token())
        json.optJSONArray("tracks").tracks()
    }.getOrDefault(emptyList())

    /** Visible for tests: maps Spotify's search envelope without touching the network. */
    internal fun parseSearchTracks(json: JSONObject): List<SpotifyTrack> = json.optJSONObject("tracks")?.optJSONArray("items").tracks()

    private suspend fun tracks(url: String): List<SpotifyTrack> {
        val items = get(url, token()).optJSONArray("items") ?: return emptyList()
        return buildList(items.length()) {
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.optJSONObject("track")?.let(::track)?.let(::add)
            }
        }
    }

    private suspend fun playlists(url: String): List<SpotifyPlaylist> = get(url, token()).optJSONArray("items").playlists()

    private suspend fun savedAlbums(): List<SpotifyAlbum> {
        val items = get("$ME_ALBUMS?limit=50", token()).optJSONArray("items") ?: return emptyList()
        return buildList(items.length()) {
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.optJSONObject("album")?.let(::album)?.let(::add)
            }
        }
    }

    private suspend fun followedArtists(): List<SpotifyArtist> {
        val json = get("$ME_FOLLOWING?type=artist&limit=50", token())
        return json.optJSONObject("artists")?.optJSONArray("items").artists()
    }

    private suspend fun savedShows(): List<SpotifyShow> {
        val items = get("$ME_SHOWS?limit=50", token()).optJSONArray("items") ?: return emptyList()
        return buildList(items.length()) {
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.optJSONObject("show")?.let(::show)?.let(::add)
            }
        }
    }

    private fun albumCollection(json: JSONObject): SpotifyCollection? {
        val album = album(json) ?: return null
        val tracks = json.optJSONObject("tracks")?.optJSONArray("items").tracks()
        return SpotifyCollection(
            type = SpotifyCollectionType.Album,
            id = album.id,
            title = album.title,
            subtitle = "${album.artist} · ${album.year} · ${album.trackCount} tracks",
            artRes = 0,
            items = tracks,
            artistId = album.artistId,
            artUrl = album.artUrl
        )
    }

    private fun playlistCollection(json: JSONObject): SpotifyCollection? {
        val playlist = playlist(json) ?: return null
        val items = json.optJSONObject("tracks")?.optJSONArray("items") ?: JSONArray()
        val tracks = buildList(items.length()) {
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.optJSONObject("track")?.let(::track)?.let(::add)
            }
        }
        return SpotifyCollection(
            type = SpotifyCollectionType.Playlist,
            id = playlist.id,
            title = playlist.name,
            subtitle = "${playlist.owner} · ${playlist.trackCount} tracks",
            artRes = 0,
            items = tracks,
            artistId = playlist.ownerId,
            artUrl = playlist.artUrl
        )
    }

    private fun showCollection(json: JSONObject): SpotifyCollection? {
        val show = show(json) ?: return null
        val items = json.optJSONObject("episodes")?.optJSONArray("items") ?: JSONArray()
        val episodes = buildList(items.length()) {
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.let { episode(it, show) }?.let(::add)
            }
        }
        return SpotifyCollection(
            type = SpotifyCollectionType.Show,
            id = show.id,
            title = show.name,
            subtitle = "${show.publisher} · ${show.episodeCount} episodes",
            artRes = 0,
            items = episodes,
            artUrl = show.artUrl
        )
    }

    private fun track(json: JSONObject): SpotifyTrack? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val album = json.optJSONObject("album")
        val artist = json.optJSONArray("artists")?.optJSONObject(0)
        return SpotifyTrack(
            id = id,
            title = json.optString("name", "Unknown track"),
            artist = artist?.optString("name").orEmpty().ifEmpty { "Unknown artist" },
            artistId = artist?.optString("id").orEmpty(),
            albumId = album?.optString("id").orEmpty(),
            artRes = 0,
            durationMs = json.optLong("duration_ms", 0L),
            explicit = json.optBoolean("explicit", false),
            playable = json.optBoolean("is_playable", true),
            uri = json.optString("uri").takeIf { it.isNotEmpty() },
            artUrl = imageUrl(album)
        )
    }

    private fun episode(json: JSONObject, show: SpotifyShow? = null): SpotifyEpisode? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val images = imageUrl(json) ?: show?.artUrl
        return SpotifyEpisode(
            id = id,
            title = json.optString("name", "Unknown episode"),
            show = show?.name ?: json.optString("show").ifEmpty { "Podcast" },
            showId = show?.id ?: json.optJSONObject("show")?.optString("id").orEmpty(),
            published = json.optString("release_date").ifEmpty { "Podcast episode" },
            artRes = 0,
            durationMs = json.optLong("duration_ms", 0L),
            video = false,
            uri = json.optString("uri").takeIf { it.isNotEmpty() },
            artUrl = images
        )
    }

    private fun album(json: JSONObject): SpotifyAlbum? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val artist = json.optJSONArray("artists")?.optJSONObject(0)
        val tracks = json.optJSONObject("tracks")?.optJSONArray("items").tracks()
        return SpotifyAlbum(
            id = id,
            title = json.optString("name", "Unknown album"),
            artist = artist?.optString("name").orEmpty().ifEmpty { "Unknown artist" },
            artistId = artist?.optString("id").orEmpty(),
            year = json.optString("release_date").take(4).toIntOrNull() ?: 0,
            artRes = 0,
            tracks = tracks,
            artUrl = imageUrl(json),
            trackCount = json.optJSONObject("tracks")?.optInt("total", tracks.size) ?: tracks.size
        )
    }

    private fun playlist(json: JSONObject): SpotifyPlaylist? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val owner = json.optJSONObject("owner")
        return SpotifyPlaylist(
            id = id,
            name = json.optString("name", "Unknown playlist"),
            owner = owner?.optString("display_name").orEmpty().ifEmpty { "Spotify" },
            ownerId = owner?.optString("id"),
            artRes = 0,
            tracks = emptyList(),
            artUrl = imageUrl(json),
            trackCount = json.optJSONObject("tracks")?.optInt("total", 0) ?: 0
        )
    }

    private fun show(json: JSONObject): SpotifyShow? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val artUrl = imageUrl(json)
        val base = SpotifyShow(
            id = id,
            name = json.optString("name", "Unknown show"),
            publisher = json.optString("publisher").ifEmpty { "Podcast" },
            artRes = 0,
            episodes = emptyList(),
            artUrl = artUrl
        )
        val episodes = json.optJSONObject("episodes")?.optJSONArray("items").episodes(this, base)
        return base.copy(
            episodes = episodes,
            episodeCount = json.optJSONObject("episodes")?.optInt("total", episodes.size) ?: episodes.size
        )
    }

    private fun artist(json: JSONObject): SpotifyArtist? {
        val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
        return SpotifyArtist(
            id = id,
            name = json.optString("name", "Unknown artist"),
            artRes = 0,
            albumIds = emptyList(),
            artUrl = imageUrl(json)
        )
    }

    private fun JSONArray?.tracks(): List<SpotifyTrack> = buildList {
        if (this@tracks == null) return@buildList
        for (i in 0 until this@tracks.length()) {
            this@tracks.optJSONObject(i)?.let(::track)?.let(::add)
        }
    }

    private fun JSONArray?.albums(): List<SpotifyAlbum> = buildList {
        if (this@albums == null) return@buildList
        for (i in 0 until this@albums.length()) {
            this@albums.optJSONObject(i)?.let(::album)?.let(::add)
        }
    }

    private fun JSONArray?.artists(): List<SpotifyArtist> = buildList {
        if (this@artists == null) return@buildList
        for (i in 0 until this@artists.length()) {
            this@artists.optJSONObject(i)?.let(::artist)?.let(::add)
        }
    }

    private fun JSONArray?.playlists(): List<SpotifyPlaylist> = buildList {
        if (this@playlists == null) return@buildList
        for (i in 0 until this@playlists.length()) {
            this@playlists.optJSONObject(i)?.let(::playlist)?.let(::add)
        }
    }

    private fun JSONArray?.shows(): List<SpotifyShow> = buildList {
        if (this@shows == null) return@buildList
        for (i in 0 until this@shows.length()) {
            this@shows.optJSONObject(i)?.let(::show)?.let(::add)
        }
    }

    private fun JSONArray?.episodes(api: SpotifyWebApi, show: SpotifyShow): List<SpotifyEpisode> = buildList {
        if (this@episodes == null) return@buildList
        for (i in 0 until this@episodes.length()) {
            this@episodes.optJSONObject(i)?.let { api.episode(it, show) }?.let(::add)
        }
    }

    private fun imageUrl(json: JSONObject?): String? {
        val images = json?.optJSONArray("images") ?: return null
        var best: String? = null
        var bestWidth = Int.MAX_VALUE
        for (i in 0 until images.length()) {
            val image = images.optJSONObject(i) ?: continue
            val url = image.optString("url").takeIf { it.isNotEmpty() } ?: continue
            val width = image.optInt("width", Int.MAX_VALUE)
            if (width >= 64 && width < bestWidth) {
                best = url
                bestWidth = width
            }
        }
        return best ?: images.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() }
    }

    private suspend fun token(): String = accessToken() ?: fail(SpotifyFailure.AuthenticationExpired, "Sign in again")

    private suspend fun get(url: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                status == 401 -> fail(SpotifyFailure.AuthenticationExpired, "Sign in again")

                status == 429 -> {
                    android.util.Log.w("SidetracksApi", "$url -> 429 retryAfter=${connection.getHeaderField("Retry-After")} ${text.take(300)}")
                    fail(SpotifyFailure.RateLimited, "Spotify is rate-limiting requests")
                }

                status !in 200..299 -> {
                    android.util.Log.w("SidetracksApi", "$url -> $status ${text.take(300)}")
                    fail(SpotifyFailure.Network, "Spotify returned HTTP $status")
                }

                else -> runCatching { JSONObject(text) }
                    .getOrElse { fail(SpotifyFailure.Unknown, "Spotify returned an unreadable response") }
            }
        } catch (e: SpotifyGatewayException) {
            throw e
        } catch (e: IOException) {
            throw SpotifyGatewayException(SpotifyFailure.Network, "Couldn't reach Spotify", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun fail(failure: SpotifyFailure, message: String): Nothing = throw SpotifyGatewayException(failure, message)

    private companion object {
        const val API = "https://api.spotify.com/v1"
        const val SEARCH = "$API/search"
        const val ME_RECENT = "$API/me/player/recently-played"
        const val ME_TRACKS = "$API/me/tracks"
        const val ME_PLAYLISTS = "$API/me/playlists"
        const val ME_ALBUMS = "$API/me/albums"
        const val ME_FOLLOWING = "$API/me/following"
        const val ME_SHOWS = "$API/me/shows"
        const val ALBUMS = "$API/albums"
        const val ARTISTS = "$API/artists"
        const val PLAYLISTS = "$API/playlists"
        const val SHOWS = "$API/shows"
        const val TIMEOUT_MS = 15_000
    }
}
