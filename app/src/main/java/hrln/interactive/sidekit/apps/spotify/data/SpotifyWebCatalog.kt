package hrln.interactive.sidekit.apps.spotify.data

import hrln.interactive.sidekit.apps.spotify.LibraryFilter
import hrln.interactive.sidekit.apps.spotify.SearchScope
import hrln.interactive.sidekit.apps.spotify.SpotifyAlbum
import hrln.interactive.sidekit.apps.spotify.SpotifyArtist
import hrln.interactive.sidekit.apps.spotify.SpotifyCollection
import hrln.interactive.sidekit.apps.spotify.SpotifyCollectionType
import hrln.interactive.sidekit.apps.spotify.SpotifySearchResults
import hrln.interactive.sidekit.apps.spotify.SpotifyTrack

/** [SpotifyCatalog] over the Web API. */
class SpotifyWebCatalog(private val api: SpotifyWebApi) : SpotifyCatalog {
    override suspend fun recentlyPlayed(): List<SpotifyTrack> = api.recentlyPlayed()

    override suspend fun library(filter: LibraryFilter): List<Any> = api.library(filter)

    override suspend fun collection(type: SpotifyCollectionType, id: String): SpotifyCollection? = api.collection(type, id)

    override suspend fun artist(id: String): SpotifyArtist? = api.artist(id)

    override suspend fun albumsBy(artist: SpotifyArtist): List<SpotifyAlbum> = api.albumsBy(artist)

    override suspend fun topTracks(artist: SpotifyArtist): List<SpotifyTrack> = api.topTracks(artist)

    override suspend fun search(query: String, scopes: Set<SearchScope>): SpotifySearchResults = api.search(query, scopes)
}
