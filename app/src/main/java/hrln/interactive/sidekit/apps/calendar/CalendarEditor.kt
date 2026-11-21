package hrln.interactive.sidekit.apps.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.theme.SidekitTheme
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val EventDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)

@Composable
fun CalendarEditor(id: Long?, date: LocalDate, onClose: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val repo = remember { DeviceCalendarRepository(context) }
    val scope = rememberCoroutineScope()
    var calendars by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
    var original by remember { mutableStateOf<EventDraft?>(null) }
    var ready by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf<String?>(null) }
    var start by rememberSaveable { mutableStateOf<String?>(null) }
    var end by rememberSaveable { mutableStateOf<String?>(null) }
    var calendarId by rememberSaveable { mutableStateOf<Long?>(null) }
    var allDay by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var choosingCalendar by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        try {
            calendars = repo.calendars()
            original = id?.let { repo.event(it) }
            if (id != null && original == null) {
                error = R.string.calendar_error_missing
                return@LaunchedEffect
            }
            val event = original
            val zone = if (event?.allDay == true) ZoneOffset.UTC else event?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            if (title == null) {
                title = event?.title.orEmpty()
                allDay = event?.allDay ?: false
                start = event?.start?.let { EventDateFormat.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "$date 09:00"
                end = event?.end?.let { EventDateFormat.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "$date 10:00"
                calendarId = event?.calendarId ?: calendars.firstOrNull()?.id
            }
            ready = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = R.string.calendar_error_open
        }
    }
    fun save() {
        if (!ready || busy) return
        val calendar = calendarId
        if (calendar == null || calendars.none { it.id == calendar }) {
            error = R.string.calendar_error_choose
            return
        }
        val zone = if (allDay) ZoneOffset.UTC else original?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val begin = runCatching { LocalDateTime.parse(start, EventDateFormat).let { if (allDay) it.toLocalDate().atStartOfDay() else it }.atZone(zone).toInstant().toEpochMilli() }.getOrNull()
        val finish = runCatching { LocalDateTime.parse(end, EventDateFormat).let { if (allDay) it.toLocalDate().atStartOfDay() else it }.atZone(zone).toInstant().toEpochMilli() }.getOrNull()
        if (title.isNullOrBlank() || begin == null || finish == null || finish <= begin) {
            error = R.string.calendar_error_invalid
            return
        }
        busy = true
        scope.launch {
            try {
                repo.save(EventDraft(id, title.orEmpty(), begin, finish, calendar, allDay, original?.rrule, zone.id))
                onClose()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = R.string.calendar_error_save
            } finally {
                busy = false
            }
        }
    }
    BackHandler(choosingCalendar || deleting || busy) {
        if (!busy) {
            choosingCalendar = false
            deleting = false
        }
    }
    SidekitScaffold(
        modifier = Modifier.focusRequester(focus).focusable(),
        title = stringResource(
            if (choosingCalendar) {
                R.string.calendar_choose_calendar
            } else if (id == null) {
                R.string.calendar_new_event
            } else {
                R.string.calendar_edit_event
            }
        ),
        actions = when {
            busy -> listOf(SideAction("main", stringResource(R.string.calendar_saving)) {})

            choosingCalendar -> listOf(SideAction("main", stringResource(R.string.calendar_back)) { choosingCalendar = false })

            deleting -> listOf(
                SideAction.cancel("cancel") { deleting = false },
                SideAction.delete("main", stringResource(if (original?.rrule.isNullOrBlank()) R.string.calendar_delete else R.string.calendar_delete_series)) {
                    if (id != null) {
                        busy = true
                        scope.launch {
                            try {
                                repo.delete(id)
                                onClose()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                error = R.string.calendar_error_delete
                                deleting = false
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            )

            else -> listOf(SideAction.cancel("cancel", onClick = onClose), SideAction.save("main", onClick = ::save))
        },
        options = if (id == null || busy || choosingCalendar) emptyList() else listOf(SideAction.delete("delete") { deleting = true })
    ) { padding ->
        val list = rememberLazyListState()
        SidekitList(padding, state = list) {
            if (choosingCalendar) {
                calendars.forEachIndexed { index, calendar ->
                    item {
                        SidekitItem(calendar.name, Modifier.listFade(list, index), onClick = {
                            calendarId = calendar.id
                            choosingCalendar = false
                        })
                    }
                }
            } else {
                error?.let { message -> item { SidekitItem(stringResource(message), Modifier.listFade(list, 0)) } }
                if (!ready && error == null) item { SidekitItem(stringResource(R.string.calendar_loading), Modifier.listFade(list, 0)) }
                if (ready) {
                    if (!original?.rrule.isNullOrBlank()) item { SidekitItem(stringResource(R.string.calendar_series_notice), Modifier.listFade(list, 0)) }
                    item { SidekitCard(Modifier.listFade(list, 1).fillMaxWidth()) { SidekitTextField(title.orEmpty(), { title = it }, placeholder = stringResource(R.string.calendar_event_title), focusOnKeypad = true, autoFocus = id == null, singleLine = true, onDone = { focus.requestFocus() }) } }
                    item {
                        SidekitCard(Modifier.listFade(list, 2).fillMaxWidth()) {
                            Column {
                                SidekitText(stringResource(R.string.calendar_start_format), style = SidekitTheme.typography.caption)
                                SidekitTextField(start.orEmpty(), { start = it }, singleLine = true, dictation = false, onDone = { focus.requestFocus() })
                            }
                        }
                    }
                    item {
                        SidekitCard(Modifier.listFade(list, 3).fillMaxWidth()) {
                            Column {
                                SidekitText(stringResource(R.string.calendar_end_format), style = SidekitTheme.typography.caption)
                                SidekitTextField(end.orEmpty(), { end = it }, singleLine = true, dictation = false, onDone = { focus.requestFocus() })
                            }
                        }
                    }
                    item { SidekitItem(stringResource(if (allDay) R.string.calendar_all_day_exclusive else R.string.calendar_timed), Modifier.listFade(list, 4), onClick = { allDay = !allDay }) }
                    item { SidekitItem(calendars.find { it.id == calendarId }?.name ?: stringResource(R.string.calendar_no_writable), Modifier.listFade(list, 5), onClick = { choosingCalendar = true }) }
                    if (calendars.isEmpty()) item { SidekitItem(stringResource(R.string.calendar_add_account), Modifier.listFade(list, 6)) }
                }
            }
        }
    }
}
