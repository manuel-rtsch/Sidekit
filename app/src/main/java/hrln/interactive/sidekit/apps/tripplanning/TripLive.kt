package hrln.interactive.sidekit.apps.tripplanning

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.integrations.ServiceException
import hrln.interactive.sidekit.apps.maps.*
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.undoBar
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.navigation.SubApp
import hrln.interactive.sidekit.sdk.ui.UndoWindowLifecycle
import hrln.interactive.sidekit.sdk.ui.rememberUndoWindow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** A status line: a string resource the UI resolves, or text a service sent as-is. */
sealed interface TripMessage {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : TripMessage
    data class Text(val text: String) : TripMessage
}

@Composable
private fun TripMessage.text(): String = when (this) {
    is TripMessage.Res -> stringResource(id, *args.toTypedArray())
    is TripMessage.Text -> text
}

class TripViewModel(val repository: TripRepository, private val planner: TripPlanner = LocalTripPlanner(), private val transit: NswTransit = NswTransit()) : ViewModel() {
    var from by mutableStateOf("")
    var to by mutableStateOf("")
    var time by mutableStateOf("")
    var notes by mutableStateOf("")
    var selected by mutableStateOf<Trip?>(null)
    var editingId: String? = null
    private var itinerary = ""
    val message = MutableStateFlow<TripMessage>(TripMessage.Res(R.string.trips_intro))
    var stops by mutableStateOf<List<TransitStop>>(emptyList())
    var origin by mutableStateOf<TransitStop?>(null)
    var destination by mutableStateOf<TransitStop?>(null)
    var choosingOrigin by mutableStateOf(true)
    var journeys by mutableStateOf<List<TransitJourney>>(emptyList())
    var departures by mutableStateOf<List<TransitDeparture>>(emptyList())
    var busy by mutableStateOf(false)
    fun search(fromOrigin: Boolean, onReady: () -> Unit) {
        if (busy) return
        choosingOrigin = fromOrigin
        val query = if (fromOrigin) from else to
        request {
            stops = transit.stops(query)
            message.value = TripMessage.Res(if (stops.isEmpty()) R.string.trips_no_stops else R.string.trips_choose_stop)
            onReady()
        }
    }
    fun find(onReady: () -> Unit) = request {
        val a = origin?.takeIf { it.name == from } ?: throw TransitInputException(R.string.trips_choose_from_first, "Search and choose your From stop first")
        val b = destination?.takeIf { it.name == to } ?: throw TransitInputException(R.string.trips_choose_to_first, "Search and choose your To stop first")
        journeys = transit.journeys(a, b, time)
        message.value = TripMessage.Res(if (journeys.isEmpty()) R.string.trips_no_journeys else R.string.trips_snapshot)
        onReady()
    }
    fun board(onReady: () -> Unit) = request {
        val stop = origin?.takeIf { it.name == from } ?: throw TransitInputException(R.string.trips_choose_from_stop_first, "Search and choose a From stop first")
        departures = transit.departures(stop)
        message.value = TripMessage.Res(R.string.trips_updated, listOf(java.time.LocalTime.now(java.time.ZoneId.of("Australia/Sydney")).withNano(0).toString()))
        onReady()
    }
    private fun request(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = (e as? TransitProblem)?.let { TripMessage.Res(it.text) }
                    ?: (e as? ServiceException)?.let { TripMessage.Res(it.messageRes, listOfNotNull(it.code)) }
                    ?: e.message?.let(TripMessage::Text) ?: TripMessage.Res(R.string.trips_unavailable)
            } finally {
                busy = false
            }
        }
    }

    /** [itinerary] is the journey's legs as the UI wrote them, in the app's language. */
    fun saveJourney(journey: TransitJourney, itinerary: String, onSaved: () -> Unit) {
        this.itinerary = itinerary
        time = "${journey.departure} – ${journey.arrival}"
        save(onSaved)
    }
    var saving by mutableStateOf(false)
        private set
    fun edit(trip: Trip?) {
        itinerary = trip?.itinerary.orEmpty()
        origin = trip?.origin
        destination = trip?.destination
        editingId = trip?.id
        from = trip?.from.orEmpty()
        to = trip?.to.orEmpty()
        time = trip?.time.orEmpty().let { value -> runCatching { java.time.Instant.parse(value.substringBefore(" – ")).atZone(java.time.ZoneId.of("Australia/Sydney")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrDefault(value) }
        notes = trip?.notes.orEmpty()
    }
    fun save(onSaved: () -> Unit) {
        if (saving) return
        if (repository.store.state.value == null) {
            message.value = TripMessage.Res(R.string.trips_loading_saved)
            return
        }
        saving = true
        val draft = TripDraft(from, to, time, notes)
        viewModelScope.launch {
            try {
                val result = planner.plan(draft).let { it.copy(id = editingId ?: it.id, origin = origin?.takeIf { s -> s.name == draft.from }, destination = destination?.takeIf { s -> s.name == draft.to }, itinerary = itinerary) }
                repository.save(result)
                selected = result
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalArgumentException) {
                message.value = TripMessage.Res(R.string.trips_enter_start_destination)
            } finally {
                saving = false
            }
        }
    }
}
fun NavGraphBuilder.liveTripsGraph(nav: NavHostController, transit: NswTransit = NswTransit()) {
    val root = SubApp.TripPlanning.route
    navigation(startDestination = "$root/home", route = root) {
        listOf("home", "saved", "plan", "detail", "places", "search", "stops", "journeys", "departures").forEach { page ->
            composable("$root/$page") { entry ->
                val context = LocalContext.current
                val parent = remember(entry) { nav.getBackStackEntry(root) }
                val vm: TripViewModel = viewModel(parent, factory = viewModelFactory { initializer { TripViewModel(TripRepository.get(context), transit = transit) } })
                val data by vm.repository.store.state.collectAsStateWithLifecycle()
                val message by vm.message.collectAsStateWithLifecycle()
                val error by vm.repository.store.errorRes.collectAsStateWithLifecycle()
                val back = {
                    nav.popBackStack()
                    Unit
                }
                if (page == "search") {
                    PlaceSearch(back) {
                        vm.repository.savePlace(it)
                        back()
                    }
                } else {
                    val list = rememberLazyListState()
                    val group = rememberSwipeRevealGroup()
                    var useAsStart by remember { mutableStateOf(false) }
                    var undo by remember { mutableStateOf<(() -> Unit)?>(null) }
                    val undoWindow = rememberUndoWindow { undo = null }
                    UndoWindowLifecycle(undoWindow) { undo = null }
                    fun plan() {
                        vm.edit(null)
                        nav.navigate("$root/plan")
                    }
                    val backLabel = stringResource(R.string.trips_back)
                    val undoLabel = stringResource(R.string.trips_undo)
                    val actions = when (page) {
                        "home" -> listOf(SideAction.back("saved", stringResource(R.string.trips_saved)) { nav.navigate("$root/saved") }, SideAction.forward("plan", stringResource(R.string.trips_plan), ::plan))

                        "plan" -> listOf(
                            SideAction.cancel("back", stringResource(R.string.trips_cancel), onClick = back),
                            SideAction.save("save", stringResource(if (vm.saving) R.string.trips_saving else R.string.trips_save)) { vm.save(back) }
                        )

                        "places" -> listOf(SideAction.back("back", backLabel, back), SideAction("add", stringResource(R.string.trips_add)) { nav.navigate("$root/search") })

                        "detail" -> listOf(
                            SideAction.back("back", backLabel, back),
                            SideAction("replan", stringResource(R.string.trips_plan_again)) {
                                vm.edit(vm.selected)
                                nav.navigate("$root/plan")
                            }
                        )

                        "departures" -> listOf(SideAction.back("back", backLabel, back), SideAction("refresh", stringResource(if (vm.busy) R.string.trips_loading else R.string.trips_refresh)) { vm.board {} })

                        "stops", "journeys" -> listOf(SideAction.back("back", backLabel, back))

                        else -> listOf(SideAction.back("back", backLabel, back), SideAction.forward("plan", stringResource(R.string.trips_plan), ::plan))
                    }.let { actions ->
                        // The undo window belongs to the lists a delete can happen on: saved places, and
                        // the saved trips shown on both "home" and "saved". It must not evict Save from the
                        // editor if the user navigates during the window.
                        val undoAction = undo?.takeIf { page == "places" || page == "saved" || page == "home" }
                            ?.let { restore ->
                                SideAction.undo(label = undoLabel) {
                                    undoWindow.cancel()
                                    restore()
                                    undo = null
                                }
                            }
                        if (undoAction == null) actions else undoBar(continueWith = actions.last(), undo = undoAction)
                    }
                    val options = if (page == "places") {
                        listOf(SideAction("useAs", stringResource(if (useAsStart) R.string.trips_use_as_destination else R.string.trips_use_as_start)) { useAsStart = !useAsStart })
                    } else if (page == "detail") {
                        listOf(
                            SideAction("edit", stringResource(R.string.trips_edit)) {
                                vm.edit(vm.selected)
                                nav.navigate("$root/plan")
                            }
                        )
                    } else if (page == "plan") {
                        listOf(
                            SideAction("fromSearch", stringResource(R.string.trips_find_from)) { vm.search(true) { nav.navigate("$root/stops") } },
                            SideAction("toSearch", stringResource(R.string.trips_find_to)) { vm.search(false) { nav.navigate("$root/stops") } },
                            SideAction("journeys", stringResource(R.string.trips_find_journeys)) { vm.find { nav.navigate("$root/journeys") } },
                            SideAction("departures", stringResource(R.string.trips_departures_here)) { vm.board { nav.navigate("$root/departures") } }
                        )
                    } else {
                        listOf(SideAction("places", stringResource(R.string.trips_places)) { nav.navigate("$root/places") })
                    }
                    SidekitScaffold(
                        stringResource(
                            when (page) {
                                "plan" -> R.string.trips_plan_title
                                "places" -> R.string.trips_saved_places
                                "saved" -> R.string.trips_saved_trips
                                else -> R.string.trips_title
                            }
                        ),
                        actions = actions,
                        options = options
                    ) { padding ->
                        SidekitList(padding, state = list) {
                            when (page) {
                                "plan" -> {
                                    item { TripField(stringResource(R.string.trips_from), vm.from, { vm.from = it }, Modifier.listFade(list, 0), true) }
                                    item { TripField(stringResource(R.string.trips_to), vm.to, { vm.to = it }, Modifier.listFade(list, 1)) }
                                    item { TripField(stringResource(R.string.trips_time_hint), vm.time, { vm.time = it }, Modifier.listFade(list, 2)) }
                                    item { TripField(stringResource(R.string.trips_notes), vm.notes, { vm.notes = it }, Modifier.listFade(list, 3)) }
                                    item { TripInfo(stringResource(R.string.trips_find_from), secondary = vm.origin?.name ?: stringResource(R.string.trips_from_hint), onClick = { vm.search(true) { nav.navigate("$root/stops") } }) }
                                    item { TripInfo(stringResource(R.string.trips_find_to), secondary = vm.destination?.name ?: stringResource(R.string.trips_to_hint), onClick = { vm.search(false) { nav.navigate("$root/stops") } }) }
                                    item { TripInfo(stringResource(if (vm.busy) R.string.trips_loading_transit else R.string.trips_find_journeys), secondary = stringResource(R.string.trips_journeys_detail), onClick = { vm.find { nav.navigate("$root/journeys") } }) }
                                    item { TripInfo(stringResource(R.string.trips_departures_here), secondary = stringResource(R.string.trips_departures_detail), onClick = { vm.board { nav.navigate("$root/departures") } }) }
                                }

                                "stops" -> items(vm.stops.size) { i ->
                                    val stop = vm.stops[i]
                                    TripInfo(stop.name, secondary = stop.type, onClick = {
                                        if (vm.choosingOrigin) {
                                            vm.origin = stop
                                            vm.from = stop.name
                                        } else {
                                            vm.destination = stop
                                            vm.to = stop.name
                                        }
                                        back()
                                    })
                                }

                                "journeys" -> items(vm.journeys.size) { i ->
                                    val j = vm.journeys[i]
                                    val itinerary = j.legs.joinToString("\n") { "${routeName(context, it.route)}: ${it.from} → ${it.to} · ${transitTime(it.departure)}–${transitTime(it.arrival)} · ${legTiming(context, it.live)}" }
                                    TripInfo(
                                        "${transitTime(j.departure)}–${transitTime(j.arrival)} · ${j.legs.joinToString(" → ") { routeName(context, it.route) }}",
                                        secondary = j.legs.joinToString("\n") { "${it.from} → ${it.to} · ${legTiming(context, it.live)}" } + "\n" + stringResource(R.string.trips_tap_to_save),
                                        onClick = { vm.saveJourney(j, itinerary) { nav.navigate("$root/home") { popUpTo("$root/home") { inclusive = true } } } }
                                    )
                                }

                                "departures" -> {
                                    if (vm.departures.isEmpty()) item { TripInfo(stringResource(R.string.trips_no_departures)) }
                                    items(vm.departures.size) { i ->
                                        val d = vm.departures[i]
                                        TripInfo(
                                            "${d.route} → ${d.destination}",
                                            secondary = "${transitTime(d.estimated ?: d.planned)} · ${if (d.cancelled) {
                                                stringResource(R.string.trips_cancelled)
                                            } else if (d.live) {
                                                stringResource(R.string.trips_live_estimate_scheduled, transitTime(d.planned))
                                            } else {
                                                stringResource(R.string.trips_scheduled)
                                            }}\n${d.platform}"
                                        )
                                    }
                                }

                                "places" -> {
                                    if (data?.places.isNullOrEmpty()) item { TripInfo(stringResource(R.string.trips_no_saved_places), modifier = Modifier.listFade(list, 0)) }
                                    items(data?.places?.size ?: 0) { i ->
                                        val p = data!!.places[i]
                                        SidekitSwipeRow(p.key, group, {
                                            vm.repository.deletePlace(p)
                                            undo = { vm.repository.savePlace(p) }
                                            undoWindow.start()
                                        }, Modifier.listFade(list, i)) {
                                            TripInfo(p.name, secondary = stringResource(if (useAsStart) R.string.trips_use_as_start else R.string.trips_use_as_destination), onClick = {
                                                val inEditor = nav.previousBackStackEntry?.destination?.route == "$root/plan"
                                                if (!inEditor) vm.edit(null)
                                                if (useAsStart) vm.from = p.name else vm.to = p.name

                                                back()
                                                if (!inEditor) nav.navigate("$root/plan")
                                            })
                                        }
                                    }
                                }

                                "detail" -> vm.selected?.let { t ->
                                    item { TripInfo(t.from, secondary = stringResource(R.string.trips_to_place, t.to), modifier = Modifier.listFade(list, 0)) }
                                    item { TripInfo(savedTripTime(t.time).ifBlank { stringResource(R.string.trips_no_time) }, secondary = t.notes.ifBlank { stringResource(R.string.trips_no_notes) }, modifier = Modifier.listFade(list, 1)) }
                                    if (t.itinerary.isNotBlank()) item { TripInfo(stringResource(R.string.trips_saved_itinerary), secondary = t.itinerary) }
                                }

                                else -> {
                                    val trips = data?.trips.orEmpty()
                                    if (trips.isEmpty()) item { TripInfo(stringResource(R.string.trips_no_saved_trips), secondary = stringResource(R.string.trips_get_started), modifier = Modifier.listFade(list, 0)) }
                                    items(trips.size, key = { trips[it].id }) { i ->
                                        val t = trips[i]
                                        SidekitSwipeRow(t.id, group, {
                                            vm.repository.delete(t)
                                            undo = { vm.repository.save(t) }
                                            undoWindow.start()
                                        }, Modifier.listFade(list, i)) {
                                            TripInfo("${t.from} → ${t.to}", secondary = savedTripTime(t.time).ifBlank { stringResource(R.string.trips_no_time) }, onClick = {
                                                vm.selected = t
                                                nav.navigate("$root/detail")
                                            })
                                        }
                                    }
                                }
                            }
                            item { TripInfo(error?.let { stringResource(it) } ?: message.text(), modifier = Modifier.listFade(list, if (page == "plan") 4 else 1)) }
                        }
                    }
                }
            }
        }
    }
}

/** A leg's route number, or Walk / Transit for a leg without one. */
private fun routeName(context: Context, route: String) = when (route) {
    NswTransit.WALK -> context.getString(R.string.trips_route_walk)
    NswTransit.TRANSIT -> context.getString(R.string.trips_route_transit)
    else -> route
}

private fun legTiming(context: Context, live: Boolean) = context.getString(if (live) R.string.trips_live_estimate else R.string.trips_scheduled)

@Composable
private fun TripField(label: String, value: String, change: (String) -> Unit, modifier: Modifier, main: Boolean = false) {
    SidekitTile(modifier.fillMaxWidth()) {
        SidekitTextField(value, change, Modifier.fillMaxWidth().padding(SidekitTheme.spacing.itemPadding), placeholder = label, singleLine = true, focusOnKeypad = main, autoFocus = main, onDone = {})
    }
}

@Composable
private fun TripInfo(title: String, modifier: Modifier = Modifier, secondary: String? = null, onClick: (() -> Unit)? = null) {
    SidekitCard(modifier.fillMaxWidth().heightIn(min = SidekitTheme.spacing.itemHeight), onClick = onClick) {
        Column {
            SidekitText(title, style = SidekitTheme.typography.caption, maxLines = Int.MAX_VALUE)
            secondary?.let { SidekitText(it, style = SidekitTheme.typography.small, color = SidekitTheme.colors.surfaceA6, maxLines = Int.MAX_VALUE) }
        }
    }
}
