package hrln.interactive.sidekit.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Bundle
import hrln.interactive.sidekit.sdk.SidekitContract
import org.robolectric.Shadows.shadowOf

/** Registers a fake installed native app with Robolectric's package manager. */
@Suppress("DEPRECATION")
internal fun addNativeAppForTest(
    context: Context,
    packageName: String,
    label: String,
    activityName: String = "$packageName.MainActivity",
    appId: String? = null,
    minSchema: Int? = null,
    minSchemaString: String? = null,
    launchRoute: String? = null
) {
    val pkg = packageName
    val info = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.packageName = pkg
            name = activityName
            nonLocalizedLabel = label
            enabled = true
            applicationInfo = ApplicationInfo().apply {
                this.packageName = pkg
                nonLocalizedLabel = label
            }
            metaData = Bundle().apply {
                appId?.let { putString(SidekitContract.META_DATA_APP_ID, it) }
                minSchema?.let { putInt(SidekitContract.META_DATA_MIN_SCHEMA, it) }
                minSchemaString?.let { putString(SidekitContract.META_DATA_MIN_SCHEMA, it) }
                launchRoute?.let { putString(SidekitContract.META_DATA_LAUNCH_ROUTE, it) }
            }
        }
    }
    val intent = Intent(SidekitContract.ACTION_NATIVE_APP).addCategory(Intent.CATEGORY_DEFAULT)
    shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
}
