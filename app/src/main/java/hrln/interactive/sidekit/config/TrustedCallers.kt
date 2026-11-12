package hrln.interactive.sidekit.config

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import hrln.interactive.sidekit.R
import java.security.MessageDigest

/**
 * Which apps may read Sidekit's configuration and appear as Sidekit-native apps.
 *
 * The read permission is `normal`, so Android grants it to every app that asks for it; it proves
 * intent, not identity. Trust is therefore decided by signing certificate:
 *
 * - an APK signed with Sidekit's own certificate is first-party and trusted;
 * - any other app is trusted only when its certificate digest is listed in
 *   `res/values/trusted_apps.xml` (`sidekit_trusted_app_digests`).
 *
 * There is no runtime approval: adding an app is one line in that resource and a Sidekit release.
 */
object TrustedCallers {
    /** Whether the process that is currently calling into Sidekit is trusted. */
    fun isTrustedCaller(context: Context): Boolean = policy(context).isTrustedUid(Binder.getCallingUid())

    /** Whether the installed app [packageName] is trusted. */
    fun isTrustedPackage(context: Context, packageName: String): Boolean = policy(context).isTrustedPackage(packageName)

    /** The policy for this host build, built once per process. */
    fun policy(context: Context): TrustPolicy = policy ?: synchronized(this) {
        policy ?: createPolicy(context).also { policy = it }
    }

    @Volatile
    private var policy: TrustPolicy? = null

    /** Drops the cached policy; the next caller re-reads certificates and the allowlist. */
    fun invalidate() {
        policy = null
    }

    private fun createPolicy(context: Context): TrustPolicy {
        val app = context.applicationContext
        val own = PackageCertificates.of(app, app.packageName)
        val allowed = app.resources.getStringArray(R.array.sidekit_trusted_app_digests)
            .mapNotNull(::normalizeDigest)
            .toSet()
        return TrustPolicy(
            ownDigests = own,
            allowedDigests = allowed,
            digestsOf = { packageName -> PackageCertificates.of(app, packageName) },
            packagesOf = { uid -> app.packageManager.getPackagesForUid(uid)?.toSet().orEmpty() }
        )
    }

    /** `AA:BB:…` from `apksigner verify --print-certs` and `AB:CD:…` from keytool. */
    internal fun normalizeDigest(value: String): String? {
        val digest = value.replace(":", "").replace(" ", "").uppercase()
        return digest.takeIf { it.length == 64 && it.all { char -> char in "0123456789ABCDEF" } }
    }
}

/**
 * The trust decision, separated from the Android lookups so it can be tested with fake
 * certificates.
 */
class TrustPolicy(
    private val ownDigests: Set<String>,
    private val allowedDigests: Set<String>,
    private val digestsOf: (packageName: String) -> Set<String>,
    private val packagesOf: (uid: Int) -> Set<String>
) {
    /**
     * Calls from Sidekit's own process are trusted without a certificate lookup: the provider is
     * also used in-process by previews, tests and any future same-process client.
     */
    fun isTrustedUid(uid: Int): Boolean {
        if (uid == Process.myUid()) return true
        val packages = packagesOf(uid)
        return packages.isNotEmpty() && packages.all(::isTrustedPackage)
    }

    /**
     * Every package sharing the uid must be trusted, so a shared-uid app cannot borrow another
     * package's certificate.
     */
    fun isTrustedPackage(packageName: String): Boolean {
        val digests = digestsOf(packageName)
        if (digests.isEmpty()) return false
        return digests.all { it in ownDigests || it in allowedDigests }
    }
}

/** Reads the SHA-256 digests of a package's signing certificates. */
internal object PackageCertificates {
    fun of(context: Context, packageName: String): Set<String> = runCatching {
        val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        info.signingInfo?.apkContentsSigners.orEmpty().map(::sha256).toSet()
    }.getOrDefault(emptySet())

    private fun sha256(signature: android.content.pm.Signature): String = MessageDigest.getInstance("SHA-256")
        .digest(signature.toByteArray())
        .joinToString("") { byte -> "%02X".format(byte) }
}
