package hrln.interactive.sidekit.apps.settings

import androidx.compose.runtime.Composable
import hrln.interactive.sidekit.sdk.theme.rememberSidekitThemeConfig

/**
 * The compact-layout choice, read where the theme is built (MainActivity) so toggling it in
 * Settings re-lays out the whole app on the next frame. It is observed through the SDK theme
 * store because the preference is written from another screen and has to be watched, not read
 * once.
 */
@Composable
internal fun rememberCompactLayout(prefs: SidekitPreferences): Boolean = rememberSidekitThemeConfig(prefs.theme).compact
