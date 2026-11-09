package hrln.interactive.sidekit.apps.spotify.data

import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.spotify.SpotifyTrack
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The Spotify Web API reads the app needs. It deliberately returns UI models, not raw JSON: the
 * screens never learn an endpoint or a field name.
 */
class SpotifyWebApi(private val accessToken: suspend () -> String?) {
    suspend fun searchTracks(query: String, limit: Int = 20): List<SpotifyTrack> {
        val token = accessToken() ?: fail(SpotifyFailure.AuthenticationExpired, "Sign in again")
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val json = get("$SEARCH?q=$encoded&type=track&limit=${limit.coerceIn(1, 50)}&market=from_token", token)
        return parseSearchTracks(json)
    }

    /** Visible for tests: maps Spotify's search envelope without touching the network. */
    internal fun parseSearchTracks(json: JSONObject): List<SpotifyTrack> {
        val items = json.optJSONObject("tracks")?.optJSONArray("items") ?: return emptyList()
        return buildList(items.length()) {
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.let(::track)?.let(::add)
            }
        }
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
            artRes = R.drawable.cover_night_signals,
            durationMs = json.optLong("duration_ms", 0L),
            explicit = json.optBoolean("explicit", false),
            playable = true,
            uri = json.optString("uri").takeIf { it.isNotEmpty() }
        )
    }

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

                status == 429 -> fail(SpotifyFailure.RateLimited, "Spotify is busy — try again")

                status !in 200..299 -> fail(SpotifyFailure.Network, "Spotify returned HTTP $status")

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

    private fun fail(failure: SpotifyFailure, message: String): Nothing = throw SpotifyGatewayException(failure, message)

    private companion object {
        const val SEARCH = "https://api.spotify.com/v1/search"
        const val TIMEOUT_MS = 15_000
    }
}
