package hrln.interactive.sidekit.apps.spotify.data

import hrln.interactive.sidekit.apps.spotify.LibraryFilter
import hrln.interactive.sidekit.apps.spotify.SearchScope
import hrln.interactive.sidekit.apps.spotify.SpotifyAlbum
import hrln.interactive.sidekit.apps.spotify.SpotifyArtist
import hrln.interactive.sidekit.apps.spotify.SpotifyCollection
import hrln.interactive.sidekit.apps.spotify.SpotifyCollectionType
import hrln.interactive.sidekit.apps.spotify.SpotifyPage
import hrln.interactive.sidekit.apps.spotify.SpotifySearchResults
import hrln.interactive.sidekit.apps.spotify.SpotifyTrack

/** [SpotifyCatalog] over the Web API. */
class SpotifyWebCatalog(private val api: SpotifyWebApi) : SpotifyCatalog {
    override suspend fun recentlyPlayed(): List<SpotifyTrack> = api.recentlyPlayed()

    override suspend fun library(
        filter: LibraryFilter,
        offset: Int,
        limit: Int
    ): SpotifyPage<Any> = api.library(filter, offset, limit)

    override suspend fun collection(
        type: SpotifyCollectionType,
        id: String,
        offset: Int,
        limit: Int
    ): SpotifyCollection? = api.collection(type, id, offset, limit)

    override suspend fun artist(id: String): SpotifyArtist? = api.artist(id)

    override suspend fun albumsBy(
        artist: SpotifyArtist,
        offset: Int,
        limit: Int
    ): SpotifyPage<SpotifyAlbum> = api.albumsBy(artist, offset, limit)

    override suspend fun topTracks(
        artist: SpotifyArtist,
        offset: Int,
        limit: Int
    ): SpotifyPage<SpotifyTrack> = api.topTracks(artist, offset, limit)

    override suspend fun search(
        query: String,
        scopes: Set<SearchScope>,
        offset: Int,
        limit: Int
    ): SpotifySearchResults = api.search(query, scopes, offset, limit)
}
