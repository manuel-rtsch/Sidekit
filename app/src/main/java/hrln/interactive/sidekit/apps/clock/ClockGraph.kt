package hrln.interactive.sidekit.apps.clock

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.undoBar
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.navigation.SubApp
import hrln.interactive.sidekit.sdk.ui.UndoWindowLifecycle
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

fun NavGraphBuilder.clockGraph(nav: NavHostController) {
    composable(SubApp.Clock.route) {
        ClockScreen(onEdit = { nav.navigate("${SubApp.Clock.route}/edit/$it") })
    }
    composable("${SubApp.Clock.route}/edit/{id}") { entry ->
        ClockEditor(entry.arguments?.getString("id") ?: "new", onClose = { nav.popBackStack() })
    }
}

/** The only ticker is lifecycle-bound; all timekeeping and notifications use stored timestamps. */
@Composable
private fun visibleTime(active: Boolean, monotonic: Boolean = false): Long {
    var time by remember { mutableLongStateOf(if (monotonic) SystemClock.elapsedRealtime() else System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(active, monotonic, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                time = if (monotonic) SystemClock.elapsedRealtime() else System.currentTimeMillis()
                if (active) delay(1000)
            } while (active)
        }
    }
    return time
}

fun durationText(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}

/** A tab's name on screen; the tab itself is kept by its English key. */
@Composable
private fun tabLabel(tab: String) = stringResource(
    when (tab) {
        "Timer" -> R.string.clock_tab_timer
        "Stopwatch" -> R.string.clock_tab_stopwatch
        else -> R.string.clock_tab_alarms
    }
)

@Composable
internal fun ClockScreen(onEdit: (String) -> Unit, repository: ClockRepository? = null) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val repo = repository ?: remember { Clock.repository(context.applicationContext) }
    UndoWindowLifecycle(repo.undoWindow, repo::commitUndo)
    val data by repo.data.collectAsStateWithLifecycle()
    val error by repo.error.collectAsStateWithLifecycle()
    val deletedPending by repo.deleted.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Alarms") }
    var minutes by rememberSaveable { mutableStateOf("5") }
    var inputError by remember { mutableStateOf<Int?>(null) }
    var permissionPage by rememberSaveable { mutableStateOf(false) }
    var exact by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(true) }
    val owner = LocalLifecycleOwner.current
    fun refreshPermissions() {
        exact = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        notifications = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        fullscreen = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { refreshPermissions() } }
    val current = data ?: ClockData()
    val now = visibleTime(tab == "Timer" && current.timer.endAt != null)
    val elapsed = visibleTime((tab == "Stopwatch" && current.stopwatch.startedAt != null) || (tab == "Timer" && current.timer.endAt != null), true)
    BackHandler(permissionPage) { permissionPage = false }
    val actions = if (permissionPage) {
        listOf(
            SideAction("main", stringResource(R.string.clock_allow), SidekitIcon.Check, iconAtEnd = true) {
                when {
                    !notifications && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    !notifications -> context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    !exact -> context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                    !fullscreen && Build.VERSION.SDK_INT >= 34 -> context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()))
                    else -> permissionPage = false
                }
            },
            SideAction("back", stringResource(R.string.clock_back), SidekitIcon.ArrowLeftAlt, motion = ActionMotion.Back) { permissionPage = false }
        )
    } else {
        buildList {
            when (tab) {
                "Alarms" -> add(SideAction("main", stringResource(R.string.clock_new_alarm), SidekitIcon.Add, iconAtEnd = true, motion = ActionMotion.Up) { if (data != null) onEdit("new") })

                "Timer" -> {
                    add(
                        SideAction(
                            "main",
                            if (current.timer.endAt != null) {
                                stringResource(R.string.clock_pause)
                            } else if (current.timer.remaining > 0) {
                                stringResource(R.string.clock_resume)
                            } else {
                                stringResource(R.string.clock_start)
                            },
                            if (current.timer.endAt != null) SidekitIcon.Pause else SidekitIcon.PlayArrow,
                            iconAtEnd = true,
                            motion = ActionMotion.Up
                        ) {
                            if (data != null) {
                                if (current.timer.endAt != null) {
                                    repo.pauseTimer()
                                } else {
                                    val duration = if (current.timer.remaining > 0) current.timer.remaining else minutes.toLongOrNull()?.takeIf { it in 1..10080 }?.times(60000)
                                    if (duration != null && duration >= 1000) {
                                        repo.startTimer(duration)
                                        inputError = null
                                    } else {
                                        inputError = R.string.clock_error_minutes
                                    }
                                }
                            }
                        }
                    )
                    add(SideAction("reset", stringResource(R.string.clock_reset)) { repo.cancelTimer() })
                }

                else -> {
                    add(SideAction("main", stringResource(if (current.stopwatch.startedAt == null) R.string.clock_start else R.string.clock_pause), if (current.stopwatch.startedAt == null) SidekitIcon.PlayArrow else SidekitIcon.Pause, iconAtEnd = true, motion = ActionMotion.Up) { repo.toggleStopwatch() })
                    add(SideAction("reset", stringResource(if (current.stopwatch.startedAt == null) R.string.clock_reset else R.string.clock_lap)) { if (current.stopwatch.startedAt == null) repo.resetStopwatch() else repo.lap() })
                }
            }
            // As in Sideticks, Undo joins the bar while an alarm delete can be undone; "New alarm"
            // keeps the left slot. The window belongs to the Alarms tab; Timer and Stopwatch never
            // grow an undo button.
            if (tab == "Alarms" && deletedPending != null) {
                add(SideAction.undo(contentDescription = stringResource(R.string.clock_undo_delete), onClick = repo::undoDelete))
            }
        }
    }
    SidekitScaffold(
        modifier = Modifier.focusRequester(focus).focusable(),
        title = if (permissionPage) stringResource(R.string.clock_access_title) else tabLabel(tab),
        actions = actions,
        options = if (permissionPage) emptyList() else listOf(SideAction("access", stringResource(R.string.clock_alarm_access)) { permissionPage = true })
    ) { padding ->
        val list = rememberLazyListState()
        val swipes = rememberSwipeRevealGroup()
        // Scrolling closes an open row, as elsewhere in Sidekit.
        LaunchedEffect(list) {
            snapshotFlow { list.isScrollInProgress }.filter { it }.collect { swipes.closeAll() }
        }
        SidekitList(padding, state = list) {
            if (permissionPage) {
                item {
                    SidekitItem(
                        stringResource(
                            if (!notifications) {
                                R.string.clock_access_notifications
                            } else if (!exact) {
                                R.string.clock_access_exact
                            } else if (!fullscreen) {
                                R.string.clock_access_fullscreen
                            } else {
                                R.string.clock_access_ready
                            }
                        ),
                        Modifier.listFade(list, 0)
                    )
                }
            } else {
                item { SidekitItemRow(Modifier.listFade(list, 0)) { listOf("Alarms", "Timer", "Stopwatch").forEach { name -> SidekitTile(Modifier.weight(1f), onClick = { tab = name }) { SidekitText(tabLabel(name), style = hrln.interactive.sidekit.design.theme.SidekitTheme.typography.caption) } } } }
                (error ?: inputError)?.let { message -> item { SidekitItem(stringResource(message), Modifier.listFade(list, 1)) } }
                if (tab != "Stopwatch" && (!exact || !notifications || !fullscreen)) item { SidekitItem(stringResource(R.string.clock_review_access), Modifier.listFade(list, 2), onClick = { permissionPage = true }) }
                if (data == null) {
                    item { SidekitItem(stringResource(R.string.clock_loading)) }
                } else {
                    when (tab) {
                        "Alarms" -> {
                            val visible = current.alarms
                            if (visible.isEmpty()) item { SidekitItem(stringResource(R.string.clock_no_alarms), Modifier.listFade(list, 3)) }
                            visible.forEachIndexed { index, alarm ->
                                item(alarm.id) {
                                    SidekitSwipeRow(alarm.id, swipes, { repo.deleteWithUndo(alarm.id) }, Modifier.listFade(list, index + 3)) {
                                        SidekitItem(alarm.time, Modifier, secondary = stringResource(R.string.clock_alarm_summary, stringResource(if (alarm.enabled) R.string.clock_on else R.string.clock_off), alarm.repeatDays ?: stringResource(R.string.clock_once)), onClick = { onEdit(alarm.id) })
                                    }
                                }
                            }
                        }

                        "Timer" -> {
                            item { SidekitItem(durationText(current.timer.remainingAt(now, elapsed)), Modifier.listFade(list, 3)) }
                            if (current.timer.endAt == null && current.timer.remaining == 0L) {
                                item {
                                    SidekitCard(Modifier.listFade(list, 4).fillMaxWidth()) {
                                        SidekitTextField(minutes, { minutes = it }, placeholder = stringResource(R.string.clock_minutes), singleLine = true, dictation = false, focusOnKeypad = true, onDone = { focus.requestFocus() })
                                    }
                                }
                            }
                        }

                        else -> {
                            item { SidekitItem(durationText(current.stopwatch.elapsedAt(elapsed)), Modifier.listFade(list, 3)) }
                            current.stopwatch.laps.forEachIndexed { index, lap -> item { SidekitItem(stringResource(R.string.clock_lap_number, current.stopwatch.laps.size - index), Modifier.listFade(list, index + 4), secondary = durationText(lap)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockEditor(id: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val repo = remember { Clock.repository(context.applicationContext) }
    val data by repo.data.collectAsStateWithLifecycle()
    val original = data?.alarms?.find { it.id == id }
    if (data == null) return
    var time by rememberSaveable(id) { mutableStateOf(original?.time ?: "07:00") }
    var days by rememberSaveable(id) { mutableStateOf(original?.days?.sorted()?.joinToString(",") ?: "") }
    var enabled by rememberSaveable(id) { mutableStateOf(original?.enabled ?: true) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val selected = days.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    fun save() {
        val parsed = runCatching { LocalTime.parse(time) }.getOrNull()
        if (parsed == null) {
            error = R.string.clock_error_time
            return
        }
        repo.save((original ?: ClockAlarm()).copy(hour = parsed.hour, minute = parsed.minute, days = selected, enabled = enabled))
        onClose()
    }
    SidekitScaffold(
        modifier = Modifier.focusRequester(focus).focusable(),
        title = stringResource(if (id == "new") R.string.clock_new_alarm else R.string.clock_edit_alarm),
        actions = if (deleting) {
            listOf(
                SideAction.cancel("cancel") { deleting = false },
                SideAction.delete("main", icon = SidekitIcon.DeleteForever) {
                    repo.deleteWithUndo(id)
                    onClose()
                }
            )
        } else {
            listOf(SideAction.cancel("cancel", onClick = onClose), SideAction.save("main", onClick = ::save))
        },
        options = if (id == "new") emptyList() else listOf(SideAction.delete("delete", icon = SidekitIcon.DeleteForever) { deleting = true })
    ) { padding ->
        val list = rememberLazyListState()
        SidekitList(padding, state = list) {
            item { SidekitCard(Modifier.listFade(list, 0).fillMaxWidth()) { SidekitTextField(time, { time = it }, placeholder = "HH:mm", singleLine = true, dictation = false, focusOnKeypad = true, autoFocus = id == "new", onDone = { focus.requestFocus() }) } }
            item { SidekitItem(stringResource(if (enabled) R.string.clock_alarm_on else R.string.clock_alarm_off), Modifier.listFade(list, 1), onClick = { enabled = !enabled }) }
            error?.let { message -> item { SidekitItem(stringResource(message), Modifier.listFade(list, 2)) } }
            (1..7).forEach { day ->
                item {
                    val locale = LocalConfiguration.current.locales[0]
                    SidekitItem(java.time.DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }, Modifier.listFade(list, day + 2), secondary = if (day in selected) stringResource(R.string.clock_repeat) else "", onClick = { days = (if (day in selected) selected - day else selected + day).sorted().joinToString(",") })
                }
            }
        }
    }
}
