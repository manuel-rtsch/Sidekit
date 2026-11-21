package hrln.interactive.sidekit

import android.app.Application
import android.content.Context
import hrln.interactive.sidekit.locale.AppLanguage

/**
 * Applies Settings → Language to the application context on Android 12, so notifications,
 * services and workers (which resolve text through it) use the chosen language too. Android 13+
 * applies the per-app language to every context itself, and [AppLanguage.wrap] is a no-op there.
 */
class SidekitApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }
}
