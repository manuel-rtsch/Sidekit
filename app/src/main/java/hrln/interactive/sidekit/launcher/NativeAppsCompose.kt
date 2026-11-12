package hrln.interactive.sidekit.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Discovers native apps off the main thread and keeps the result for the composition.
 *
 * The package-manager query and label loading are disk/binder work. [refreshOnResume] drops the
 * cache on every resume so an app installed while the launcher is open appears without a restart.
 * The previous list stays on screen while the new one loads.
 */
@Composable
internal fun rememberNativeApps(refreshOnResume: Boolean = false): List<NativeApp> {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var apps by remember(context) { mutableStateOf(NativeApps.cachedOrNull().orEmpty()) }
    if (refreshOnResume) {
        LifecycleResumeEffect(Unit) {
            NativeApps.invalidate()
            revision++
            onPauseOrDispose {}
        }
    }
    LaunchedEffect(context, revision) {
        apps = withContext(Dispatchers.IO) { NativeApps.cached(context) }
    }
    return apps
}
