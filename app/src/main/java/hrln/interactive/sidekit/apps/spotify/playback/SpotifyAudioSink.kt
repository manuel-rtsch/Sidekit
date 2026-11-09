package hrln.interactive.sidekit.apps.spotify.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import hrln.interactive.sidekit.apps.spotify.bridge.NativeBridge
import hrln.interactive.sidekit.apps.spotify.bridge.SpotifyNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Drains PCM from librespot into a streaming [AudioTrack]. The native sink blocks when this
 * consumer stops, so the decode thread cannot run away from the device.
 */
class SpotifyAudioSink(
    private val native: SpotifyNative = NativeBridge,
    private val scope: CoroutineScope,
    private val onFailure: (Throwable) -> Unit = {}
) {
    private var track: AudioTrack? = null
    private var drain: Job? = null

    val isRunning: Boolean get() = drain?.isActive == true

    fun start() {
        if (drain?.isActive == true) return
        val format = readFormat()
        val output = createTrack(format) ?: return
        track = output
        output.play()
        drain = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val pcm = native.readAudio(READ_TIMEOUT_MS) ?: continue
                    if (pcm.isEmpty()) continue
                    val written = output.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) throw IllegalStateException("AudioTrack write failed: $written")
                }
            } catch (t: Throwable) {
                if (isActive) onFailure(t)
            } finally {
                runCatching { output.pause() }
                runCatching { output.flush() }
                runCatching { output.release() }
                if (track === output) track = null
            }
        }
    }

    fun pause() {
        drain?.cancel()
        drain = null
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        track = null
    }

    private fun readFormat(): Format {
        val raw = native.audioFormat() ?: return Format()
        return runCatching {
            val json = JSONObject(raw)
            Format(
                sampleRate = json.optInt("sampleRate", 44_100),
                channels = json.optInt("channels", 2)
            )
        }.getOrDefault(Format())
    }

    private fun createTrack(format: Format): AudioTrack? {
        val channelMask = if (format.channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            format.sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            onFailure(IllegalStateException("AudioTrack rejected the PCM format"))
            return null
        }
        return runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(format.sampleRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.onFailure(onFailure).getOrNull()
    }

    private data class Format(val sampleRate: Int = 44_100, val channels: Int = 2)

    private companion object {
        const val READ_TIMEOUT_MS = 100L
    }
}
