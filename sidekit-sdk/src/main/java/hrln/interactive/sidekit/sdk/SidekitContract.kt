package hrln.interactive.sidekit.sdk

/**
 * Stable identifiers shared by the Sidekit host and Sidekit-native apps.
 *
 * The configuration contract is additive: bump [SCHEMA_VERSION] only for breaking changes, and
 * never change the meaning of an existing key.
 */
object SidekitContract {
    /** Contract revision exposed as `schema` in `getVersion` and `getConfig`. */
    const val SCHEMA_VERSION = 1

    /**
     * Normal permission the stable Sidekit build defines and every consumer is granted at install.
     *
     * The name is derived from the host's application id, because two builds of the same app that
     * are signed with different keys cannot both define one permission name. See
     * [readPermissionForPackage].
     */
    const val PERMISSION_READ_APP_CONFIG = "hrln.interactive.sidekit.permission.READ_APP_CONFIG"

    /** The same permission as defined by the dev build. */
    const val PERMISSION_READ_APP_CONFIG_DEV = "hrln.interactive.sidekit.dev.permission.READ_APP_CONFIG"

    /** Stable Sidekit build. */
    const val PACKAGE_STABLE = "hrln.interactive.sidekit"
    const val AUTHORITY_STABLE = "$PACKAGE_STABLE.config"

    /** Sidekit dev build (`hrln.interactive.sidekit.dev`). */
    const val PACKAGE_DEV = "hrln.interactive.sidekit.dev"
    const val AUTHORITY_DEV = "$PACKAGE_DEV.config"

    /**
     * Every read permission a client requests, one per Sidekit build that can host it.
     *
     * Each build defines only its own name; requesting the other is harmless, because Android
     * ignores a request for a permission nothing has defined yet.
     */
    val READ_PERMISSIONS: List<String> = listOf(PERMISSION_READ_APP_CONFIG, PERMISSION_READ_APP_CONFIG_DEV)

    /** Authorities probed by the configuration client, stable first. */
    val AUTHORITIES: List<String> = listOf(AUTHORITY_STABLE, AUTHORITY_DEV)

    /** Stable content URI path used for `registerContentObserver`. */
    const val PATH_CONFIG = "config"

    /** Provider methods. Both return a [android.os.Bundle]. */
    const val METHOD_GET_VERSION = "getVersion"
    const val METHOD_GET_CONFIG = "getConfig"

    /** Values advertised in `getVersion`'s `capabilities` array. */
    const val CAPABILITY_THEME = "theme"
    const val CAPABILITY_KEYPAD = "keypad"
    const val CAPABILITY_INPUT = "input"

    /**
     * The host decides who may read by signing certificate, not only by the read permission.
     *
     * A host that advertises this answers `SecurityException` to an app it does not trust, which a
     * client sees as [SidekitConfigClient.HostStatus.NotPermitted]. Trust is granted by the host's
     * author (a bundled certificate list), so an app that needs it has to ask to be added.
     */
    const val CAPABILITY_TRUST = "trust"

    /** Capabilities every provider built against this contract offers. */
    val CAPABILITIES: List<String> = listOf(CAPABILITY_THEME, CAPABILITY_KEYPAD, CAPABILITY_INPUT, CAPABILITY_TRUST)

    /**
     * The configuration authority of the Sidekit build whose package is [packageName].
     *
     * The stable build resolves to [AUTHORITY_STABLE] and the dev build to [AUTHORITY_DEV].
     */
    fun authorityForPackage(packageName: String): String = "$packageName.$PATH_CONFIG"

    /**
     * The read permission the Sidekit build whose package is [packageName] defines.
     *
     * The host checks this instead of a fixed name, so the stable and dev builds can be installed
     * together even though they are signed with different keys.
     */
    fun readPermissionForPackage(packageName: String): String = "$packageName.permission.READ_APP_CONFIG"

    /**
     * The package that owns [authority], or null for an authority this contract does not know.
     *
     * The client uses this to reject a launch extra that points at another app's provider.
     */
    fun packageForAuthority(authority: String): String? = when (authority) {
        AUTHORITY_STABLE -> PACKAGE_STABLE
        AUTHORITY_DEV -> PACKAGE_DEV
        else -> null
    }

    /** Whether [authority] belongs to a Sidekit build this contract knows. */
    fun isKnownAuthority(authority: String): Boolean = packageForAuthority(authority) != null

    /** Optional intent extra naming the host authority that launched the app. */
    const val EXTRA_HOST_AUTHORITY = "hrln.interactive.sidekit.extra.HOST_AUTHORITY"

    /** Optional intent extra naming the route the app should open when launched by Sidekit. */
    const val EXTRA_LAUNCH_ROUTE = "hrln.interactive.sidekit.extra.LAUNCH_ROUTE"

    /** Action advertised by Sidekit-native app activities for launcher discovery. */
    const val ACTION_NATIVE_APP = "hrln.interactive.sidekit.action.NATIVE_APP"

    /** Manifest metadata keys read from a [ACTION_NATIVE_APP] activity. */
    const val META_DATA_APP_ID = "hrln.interactive.sidekit.app.ID"
    const val META_DATA_MIN_SCHEMA = "hrln.interactive.sidekit.app.MIN_SCHEMA"
    const val META_DATA_LAUNCH_ROUTE = "hrln.interactive.sidekit.app.LAUNCH_ROUTE"
}
