package hrln.interactive.sidekit.sdk.config

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import hrln.interactive.sidekit.sdk.SidekitContract

/** What the installed Sidekit host can offer this app. */
enum class HostStatus {
    /** No Sidekit build is installed (or none is visible to this app). */
    NotInstalled,

    /** Sidekit is installed, but it does not expose a configuration this SDK understands. */
    InstalledUnsupported,

    /**
     * Sidekit is installed and readable, but it does not trust this app's signing certificate.
     *
     * Hosts advertise [SidekitContract.CAPABILITY_TRUST]; ask the Sidekit author to add this app's
     * certificate digest. Local stores keep working in the meantime.
     */
    NotPermitted,

    /** A compatible host provider answered and can be read. */
    Available
}

/**
 * Discovers the Sidekit host and reads its read-only configuration.
 *
 * A Sidekit-native app calls [status] once at startup to decide between host-backed, read-only
 * stores and its own local settings. It then uses [read] or [observe] through [source] to keep the
 * UI in step with Sidekit.
 *
 * The client never writes, and it exposes only the config contract in [SidekitConfig]. Personal
 * data such as credentials, messages, hidden apps and home order is not part of the channel.
 */
object SidekitConfigClient {
    /**
     * The host authority a launcher passed in its launch intent, or null.
     *
     * Pass the result to [status], [read], [observe] or [source]. A dev Sidekit that launched the
     * app then stays the host, instead of the client silently falling back to the stable build.
     */
    fun authorityFrom(intent: Intent?): String? = intent
        ?.getStringExtra(SidekitContract.EXTRA_HOST_AUTHORITY)
        ?.takeIf { it.isNotBlank() && SidekitContract.isKnownAuthority(it) }

    /**
     * Probes the Sidekit host.
     *
     * Without [hostAuthority] the stable build is preferred, then the dev build. With an explicit
     * known authority that build is preferred, so a launcher can keep a dev session on the dev
     * host; if it has gone away, the other known builds are tried before reporting no host.
     */
    fun status(context: Context, hostAuthority: String? = null): HostStatus = resolve(context, hostAuthority).status

    /** The authority of the compatible host that [status] would use, or null. */
    fun hostAuthority(context: Context, hostAuthority: String? = null): String? = resolve(context, hostAuthority).authority

    /** Reads the host configuration, or null when no compatible host is available. */
    fun read(context: Context, hostAuthority: String? = null): SidekitConfig? {
        val resolved = resolve(context, hostAuthority)
        return if (resolved.status == HostStatus.Available) readFrom(context, resolved.authority!!) else null
    }

    /**
     * Calls [onChange] whenever the host configuration changes.
     *
     * Returns a function that stops observing. Nothing is registered when no compatible host is
     * available, so the app can call this unconditionally.
     */
    fun observe(
        context: Context,
        onChange: (SidekitConfig) -> Unit,
        hostAuthority: String? = null
    ): () -> Unit {
        val resolved = resolve(context, hostAuthority)
        if (resolved.status != HostStatus.Available) return {}
        return observeFrom(context, resolved.authority!!, onChange)
    }

    /**
     * A [SidekitConfigSource] backed by the installed host.
     *
     * It reads through this client on every call, so a host-backed store built from it reflects a
     * Sidekit update without the app re-resolving the authority.
     */
    fun source(context: Context, hostAuthority: String? = null): SidekitConfigSource {
        val appContext = context.applicationContext
        return object : SidekitConfigSource {
            override fun read(): SidekitConfig? = SidekitConfigClient.read(appContext, hostAuthority)

            override fun observe(onChange: (SidekitConfig?) -> Unit): () -> Unit = SidekitConfigClient.observe(appContext, { onChange(it) }, hostAuthority)
        }
    }

    private data class Resolved(val authority: String?, val status: HostStatus)

    private fun resolve(context: Context, hostAuthority: String?): Resolved {
        // The first host that exists but cannot serve is the one worth reporting; strict priority
        // ordering would let an unrelated build's refusal mask a schema problem in the preferred
        // one.
        var failure = HostStatus.NotInstalled
        if (hostAuthority != null) {
            val status = probe(context, hostAuthority)
            if (status == HostStatus.Available) return Resolved(hostAuthority, status)
            // A dev build can be uninstalled while the app is still running. Fall back to the
            // other known Sidekit builds, but never to an authority this contract does not know.
            if (!SidekitContract.isKnownAuthority(hostAuthority)) return Resolved(null, status)
            failure = status.takeIf { it != HostStatus.NotInstalled } ?: failure
        }

        for (authority in SidekitContract.AUTHORITIES) {
            if (authority == hostAuthority) continue
            val status = probe(context, authority)
            if (status == HostStatus.Available) return Resolved(authority, status)
            if (failure == HostStatus.NotInstalled) failure = status.takeIf { it != HostStatus.NotInstalled } ?: failure
        }
        return Resolved(null, failure)
    }

    private fun probe(context: Context, authority: String): HostStatus {
        val provider = providerInfo(context, authority) ?: return HostStatus.NotInstalled
        val expectedPackage = SidekitContract.packageForAuthority(authority)
        if (expectedPackage != null && provider.packageName != expectedPackage) return HostStatus.NotInstalled
        val version = try {
            context.contentResolver.call(configUri(authority), SidekitContract.METHOD_GET_VERSION, null, null)
        } catch (e: SecurityException) {
            // The provider is there but this app may not read it: either it is not trusted by the
            // host's signer policy or it lacks the permission. Both are "ask the host", not
            // "not installed".
            return HostStatus.NotPermitted
        } catch (e: IllegalArgumentException) {
            return HostStatus.InstalledUnsupported
        } catch (e: RuntimeException) {
            return HostStatus.InstalledUnsupported
        }
        val config = SidekitConfigCodec.decodeVersion(version) ?: return HostStatus.InstalledUnsupported
        return if (config.isCompatible()) HostStatus.Available else HostStatus.InstalledUnsupported
    }

    private fun providerInfo(context: Context, authority: String): android.content.pm.ProviderInfo? = try {
        context.packageManager.resolveContentProvider(authority, 0)
    } catch (e: RuntimeException) {
        null
    }

    private fun readFrom(context: Context, authority: String): SidekitConfig? {
        val bundle = try {
            context.contentResolver.call(configUri(authority), SidekitContract.METHOD_GET_CONFIG, null, null)
        } catch (e: RuntimeException) {
            return null
        }
        return SidekitConfigCodec.decodeConfig(bundle)?.takeIf { it.isCompatible() }
    }

    private fun observeFrom(context: Context, authority: String, onChange: (SidekitConfig) -> Unit): () -> Unit {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                readFrom(context, authority)?.let(onChange)
            }
        }
        val stop = { context.contentResolver.unregisterContentObserver(observer) }
        return try {
            context.contentResolver.registerContentObserver(configUri(authority), true, observer)
            stop
        } catch (e: RuntimeException) {
            {}
        }
    }

    private fun configUri(authority: String): Uri = Uri.parse("content://$authority/${SidekitContract.PATH_CONFIG}")
}
