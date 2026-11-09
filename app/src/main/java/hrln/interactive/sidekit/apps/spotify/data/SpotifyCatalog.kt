package hrln.interactive.sidekit.apps.spotify.data

import hrln.interactive.sidekit.apps.spotify.LibraryFilter
import hrln.interactive.sidekit.apps.spotify.SearchScope
import hrln.interactive.sidekit.apps.spotify.SpotifyAlbum
import hrln.interactive.sidekit.apps.spotify.SpotifyArtist
import hrln.interactive.sidekit.apps.spotify.SpotifyCollection
import hrln.interactive.sidekit.apps.spotify.SpotifyCollectionType
import hrln.interactive.sidekit.apps.spotify.SpotifySearchResults
import hrln.interactive.sidekit.apps.spotify.SpotifyTrack

/**
 * Read side of the Spotify account, as the UI needs it. Native and Web API implementations will
 * sit behind this interface; the mockup uses the local catalog. No screen or ViewModel may call
 * the mock data source directly.
 */
interface SpotifyCatalog {
    fun recentlyPlayed(): List<SpotifyTrack>
    fun library(filter: LibraryFilter): List<Any>
    fun collection(type: SpotifyCollectionType, id: String): SpotifyCollection?
    fun artist(id: String): SpotifyArtist?
    fun albumsBy(artist: SpotifyArtist): List<SpotifyAlbum>
    fun topTracks(artist: SpotifyArtist): List<SpotifyTrack>
    fun search(query: String, scopes: Set<SearchScope>): SpotifySearchResults
}
