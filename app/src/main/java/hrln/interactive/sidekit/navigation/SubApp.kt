package hrln.interactive.sidekit.navigation

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.icons.SidekitIcon

/**
 * Every app the launcher can open, in launcher grid order (Launcher Grid view, 53:1129).
 *
 * [designed] is false for tiles that appear in the launcher mock but have no app mock in
 * Figma; those open [hrln.interactive.sidekit.apps.placeholder.PlaceholderAppScreen].
 * Apps after [WebBrowser] have mocks but no tile in the grid mock, so they're appended.
 *
 * [label] is the English name, a stable key that never changes with the language; what the
 * launcher shows (and searches) is [labelRes], through [displayLabel] or [label] with a Context.
 */
enum class SubApp(val label: String, @StringRes val labelRes: Int, val icon: SidekitIcon, val designed: Boolean = true) {
    Sidemessage("Sidemessage", R.string.shell_app_sidemessage, SidekitIcon.Chat),
    Sidenotes("Sidenotes", R.string.shell_app_sidenotes, SidekitIcon.Docs),
    Sideticks("Sideticks", R.string.shell_app_sideticks, SidekitIcon.Checklist),
    Phonebook("Phonebook", R.string.shell_app_phonebook, SidekitIcon.CallLog),
    Photos("Photos", R.string.shell_app_photos, SidekitIcon.PhotoLibrary),
    Translate("Translate", R.string.shell_app_translate, SidekitIcon.Translate),
    Calculator("Calculator", R.string.shell_app_calculator, SidekitIcon.Calculate),
    Mail("Mail", R.string.shell_app_mail, SidekitIcon.Mail),
    Files("Files", R.string.shell_app_files, SidekitIcon.FolderCopy),
    Maps("Maps", R.string.shell_app_maps, SidekitIcon.Map),
    Music("Music", R.string.shell_app_music, SidekitIcon.MusicNote2),
    Clock("Clock", R.string.shell_app_clock, SidekitIcon.Alarm),
    Video("Video", R.string.shell_app_video, SidekitIcon.SmartDisplay),
    Vault("Vault", R.string.shell_app_vault, SidekitIcon.Encrypted),
    TripPlanning("Trip Planning", R.string.shell_app_trip_planning, SidekitIcon.BusMapPin),
    Banking("Banking", R.string.shell_app_banking, SidekitIcon.AccountBalanceWallet),
    Settings("Settings", R.string.shell_app_settings, SidekitIcon.Settings),
    WebBrowser("Web browser", R.string.shell_app_web_browser, SidekitIcon.TravelExplore),
    Apps("Apps", R.string.shell_app_apps, SidekitIcon.Apps),
    Camera("Camera", R.string.shell_app_camera, SidekitIcon.PhotoCamera),
    Sideweather("Sideweather", R.string.shell_app_sideweather, SidekitIcon.PartlyCloudyDay),
    Calendar("Calendar", R.string.shell_app_calendar, SidekitIcon.CalendarMonth),
    Compass("Compass", R.string.shell_app_compass, SidekitIcon.Explore),
    Health("Health", R.string.shell_app_health, SidekitIcon.EcgHeart),
    Emulator("Emulator", R.string.shell_app_emulator, SidekitIcon.SmartDisplay),
    Sideport("Sideport", R.string.shell_app_sideport, SidekitIcon.DeployedCode)
    ;

    /** The app's name in the app language. */
    fun label(context: Context): String = context.getString(labelRes)

    /** The app's name in the app language. */
    @Composable
    @ReadOnlyComposable
    fun displayLabel(): String = stringResource(labelRes)

    /** Root navigation route of the app. Nested screens use "$route/...". */
    val route: String get() = "app/${name.lowercase()}"

    companion object {
        /** The blue dock pinned to the bottom of the launcher (53:1256). */
        val dock: List<SubApp> = listOf(Sidemessage, Sidenotes, Sideticks, Phonebook)
    }
}
