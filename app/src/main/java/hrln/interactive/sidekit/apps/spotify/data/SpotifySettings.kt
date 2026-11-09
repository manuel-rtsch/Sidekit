package hrln.interactive.sidekit.apps.spotify.data

import android.content.Context
import hrln.interactive.sidekit.apps.integrations.PrivateSettings
import hrln.interactive.sidekit.apps.spotify.AudioQuality

/** User preferences that survive process death and apply before the native player is created. */
data class SpotifySettings(
    val audioQuality: AudioQuality = AudioQuality.High
) {
    val bitrateKbps: Int get() = audioQuality.bitrateKbps
}

/**
 * Settings live in the same encrypted, atomic store as credentials. They are not secrets, but the
 * store already exists, needs no extra dependency and keeps all Sidetracks state in one place.
 */
class SpotifySettingsStore(
    context: Context,
    name: String = "sidetracks_settings"
) {
    private val settings = PrivateSettings(context.applicationContext, name)

    @Synchronized
    fun read(): SpotifySettings {
        val value = settings.read()
        val quality = AudioQuality.entries
            .firstOrNull { it.name == value.optString(KEY_QUALITY) }
            ?: AudioQuality.High
        return SpotifySettings(audioQuality = quality)
    }

    @Synchronized
    fun write(value: SpotifySettings) {
        settings.write(
            org.json.JSONObject()
                .put(KEY_QUALITY, value.audioQuality.name)
        )
    }

    private companion object {
        const val KEY_QUALITY = "audio_quality"
    }
}
