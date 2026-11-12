package hrln.interactive.sidekit.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import hrln.interactive.sidekit.config.TrustedCallers
import hrln.interactive.sidekit.sdk.SidekitContract

/**
 * A Sidekit-native app installed as its own APK.
 *
 * The app declares an exported activity for [SidekitContract.ACTION_NATIVE_APP]; its metadata
 * supplies [appId], [minSchema] and an optional [launchRoute]. The launcher only uses the
 * descriptor for placement, label and icon: launching is an explicit intent to [component], so a
 * tile can never start anything other than the activity that advertised itself.
 *
 * Advertising the action is not enough: the app's signing certificate must be trusted (see
 * `TrustedCallers`), so an arbitrary app cannot take a tile on the home screen, and an [appId]
 * that collides with a built-in app or with another native app is ignored.
 */
data class NativeApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val appId: String = packageName,
    val minSchema: Int = 0,
    val launchRoute: String? = null
) {
    /** Stable launcher id, independent of the package label or activity name. */
    val visibilityId: String get() = "native:$packageName"

    val component: ComponentName get() = ComponentName(packageName, activityName)

    /**
     * The explicit launch intent. [hostAuthority] tells the app which Sidekit build launched it,
     * so a dev Sidekit does not make it read the stable configuration.
     */
    fun launchIntent(hostAuthority: String? = null): Intent = Intent(SidekitContract.ACTION_NATIVE_APP).apply {
        component = this@NativeApp.component
        if (!hostAuthority.isNullOrBlank()) putExtra(SidekitContract.EXTRA_HOST_AUTHORITY, hostAuthority)
        launchRoute?.takeIf { it.isNotBlank() }?.let { putExtra(SidekitContract.EXTRA_LAUNCH_ROUTE, it) }
    }
}

/** Discovers the native apps installed alongside this Sidekit build. */
object NativeApps {
    @Volatile
    private var cached: List<NativeApp>? = null

    /**
     * Every installed app advertising [SidekitContract.ACTION_NATIVE_APP] that this Sidekit
     * understands, sorted by label. One entry per package: the first advertised activity wins.
     *
     * An app whose [NativeApp.minSchema] is newer than [SidekitContract.SCHEMA_VERSION] is skipped,
     * because it has declared that this host cannot read the configuration it needs.
     */
    fun discover(
        context: Context,
        isTrusted: (packageName: String) -> Boolean = { TrustedCallers.isTrustedPackage(context, it) }
    ): List<NativeApp> {
        val intent = Intent(SidekitContract.ACTION_NATIVE_APP).addCategory(Intent.CATEGORY_DEFAULT)
        val resolved = context.packageManager.queryIntentActivities(intent)
        return resolved
            .mapNotNull { toNativeApp(context, it, isTrusted) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .distinctBy { it.appId }
    }

    /**
     * The last [discover] result, or a fresh one. Call [invalidate] when the app list may have
     * changed (for example on resume) so the next caller re-queries the package manager.
     */
    fun cached(
        context: Context,
        isTrusted: (packageName: String) -> Boolean = { TrustedCallers.isTrustedPackage(context, it) }
    ): List<NativeApp> = cached ?: discover(context, isTrusted).also { cached = it }

    /** The cached list, or null when nothing has been discovered yet. */
    fun cachedOrNull(): List<NativeApp>? = cached

    /** Drops the cached app list; the next [cached] call re-queries the package manager. */
    fun invalidate() {
        cached = null
    }

    private fun toNativeApp(
        context: Context,
        resolved: ResolveInfo,
        isTrusted: (packageName: String) -> Boolean
    ): NativeApp? {
        val activity = resolved.activityInfo ?: return null
        val packageName = activity.packageName ?: return null
        val activityName = activity.name ?: return null
        if (packageName == context.packageName || !activity.enabled) return null
        if (!isTrusted(packageName)) return null

        val minSchema = activity.metaData?.minSchema() ?: 0
        if (minSchema > SidekitContract.SCHEMA_VERSION) return null

        val label = runCatching { activity.loadLabel(context.packageManager).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: packageName
        val appId = activity.metaData?.getString(SidekitContract.META_DATA_APP_ID)?.takeIf { it.isNotBlank() } ?: packageName
        if (appId.startsWith(BUILT_IN_ID_PREFIX)) return null
        return NativeApp(
            packageName = packageName,
            activityName = activityName,
            label = label,
            appId = appId,
            minSchema = minSchema,
            launchRoute = activity.metaData?.getString(SidekitContract.META_DATA_LAUNCH_ROUTE)?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * `android:value` is a string in the manifest, but a test or a future host may put an Int in
     * the bundle. Accept both; an unreadable value means "no minimum".
     */
    private fun Bundle.minSchema(): Int = when (val value = get(SidekitContract.META_DATA_MIN_SCHEMA)) {
        is Int -> value
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }

    /**
     * Built-in apps own ids under this prefix. A native app that claims one would inherit the
     * built-in app's home position, hidden state and settings row.
     */
    private const val BUILT_IN_ID_PREFIX = "builtin:"

    private fun PackageManager.queryIntentActivities(intent: Intent): List<ResolveInfo> = runCatching {
        // GET_META_DATA fills ActivityInfo.metaData, which carries the descriptor.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            queryIntentActivities(intent, PackageManager.GET_META_DATA)
        }
    }.getOrDefault(emptyList())
}

/**
 * Launches [app] with an explicit intent and names this Sidekit build as the configuration host.
 * Returns false when the app disappeared or cannot be started, so the launcher can ignore a stale
 * tile rather than crash.
 */
fun NativeApp.launch(context: Context): Boolean = runCatching {
    val intent = launchIntent(SidekitContract.authorityForPackage(context.packageName))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
}.getOrDefault(false)
