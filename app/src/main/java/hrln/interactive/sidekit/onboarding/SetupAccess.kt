package hrln.interactive.sidekit.onboarding

import android.Manifest.permission.*
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import hrln.interactive.sidekit.R

internal data class SetupAccess(
    @StringRes val title: Int,
    @StringRes val reason: Int,
    val permissions: List<String> = emptyList(),
    val enabled: (Context) -> Boolean,
    val intent: ((Context) -> Intent)? = null
)

internal fun roleAccess(role: String, @StringRes title: Int, @StringRes reason: Int) = SetupAccess(
    title,
    reason,
    enabled = { it.getSystemService(RoleManager::class.java).isRoleHeld(role) },
    intent = { context ->
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(role)) {
            roles.createRequestRoleIntent(role)
        } else {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
    }
)

internal val homeAccess = roleAccess(RoleManager.ROLE_HOME, R.string.onboarding_set_home, R.string.onboarding_set_home_reason)

internal fun setupAccess(): List<SetupAccess> {
    fun runtime(@StringRes title: Int, @StringRes reason: Int, vararg permissions: String) = SetupAccess(
        title,
        reason,
        permissions.toList(),
        enabled = { c -> permissions.all { c.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } }
    )
    fun special(@StringRes title: Int, @StringRes reason: Int, action: String, packageUri: Boolean = false, check: (Context) -> Boolean) = SetupAccess(title, reason, enabled = check, intent = { c -> Intent(action).apply { if (packageUri) data = Uri.parse("package:${c.packageName}") } })
    fun accessibility(suffix: String): (Context) -> Boolean = { c ->
        Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').mapNotNull(ComponentName::unflattenFromString)
            .any { it.packageName == c.packageName && it.className.endsWith(suffix) }
    }
    return buildList {
        add(special(R.string.onboarding_access_back_key, R.string.onboarding_access_back_key_reason, Settings.ACTION_ACCESSIBILITY_SETTINGS, check = accessibility("KeypadAccessibilityService")))
        add(roleAccess(RoleManager.ROLE_SMS, R.string.onboarding_access_sms_app, R.string.onboarding_access_sms_app_reason))
        add(runtime(R.string.onboarding_access_sms, R.string.onboarding_access_sms_reason, READ_SMS, SEND_SMS, RECEIVE_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH))
        add(runtime(R.string.onboarding_access_contacts, R.string.onboarding_access_contacts_reason, READ_CONTACTS, READ_CALL_LOG, CALL_PHONE))
        if (Build.VERSION.SDK_INT >= 33) add(runtime(R.string.onboarding_access_notifications, R.string.onboarding_access_notifications_reason, POST_NOTIFICATIONS))
        add(special(R.string.onboarding_access_alarms, R.string.onboarding_access_alarms_reason, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true) { it.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() })
        if (Build.VERSION.SDK_INT >= 34) add(special(R.string.onboarding_access_full_screen, R.string.onboarding_access_full_screen_reason, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, true) { it.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() })
        add(runtime(R.string.onboarding_access_camera, R.string.onboarding_access_camera_reason, CAMERA))
        add(runtime(R.string.onboarding_access_microphone, R.string.onboarding_access_microphone_reason, RECORD_AUDIO))
        if (Build.VERSION.SDK_INT >= 33) {
            add(runtime(R.string.onboarding_access_photos, R.string.onboarding_access_photos_reason, READ_MEDIA_IMAGES, READ_MEDIA_VIDEO))
            add(runtime(R.string.onboarding_access_music, R.string.onboarding_access_music_reason, READ_MEDIA_AUDIO))
        } else {
            add(runtime(R.string.onboarding_access_media, R.string.onboarding_access_media_reason, READ_EXTERNAL_STORAGE))
        }
        add(runtime(R.string.onboarding_access_calendar, R.string.onboarding_access_calendar_reason, READ_CALENDAR, WRITE_CALENDAR))
        add(runtime(R.string.onboarding_access_location, R.string.onboarding_access_location_reason, ACCESS_COARSE_LOCATION))
        add(runtime(R.string.onboarding_access_activity, R.string.onboarding_access_activity_reason, ACTIVITY_RECOGNITION))
        add(runtime(R.string.onboarding_access_nearby, R.string.onboarding_access_nearby_reason, BLUETOOTH_CONNECT))
        add(special(R.string.onboarding_access_system, R.string.onboarding_access_system_reason, Settings.ACTION_MANAGE_WRITE_SETTINGS, true) { Settings.System.canWrite(it) })
        add(special(R.string.onboarding_access_dnd, R.string.onboarding_access_dnd_reason, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) { it.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted })
        add(special(R.string.onboarding_access_sideport, R.string.onboarding_access_sideport_reason, Settings.ACTION_ACCESSIBILITY_SETTINGS, check = accessibility("SideportService")))
        add(special(R.string.onboarding_access_sideport_notifications, R.string.onboarding_access_sideport_notifications_reason, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) { NotificationManagerCompat.getEnabledListenerPackages(it).contains(it.packageName) })
        add(special(R.string.onboarding_access_install, R.string.onboarding_access_install_reason, Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, true) { it.packageManager.canRequestPackageInstalls() })
    }
}
