package hrln.interactive.sidekit.apps.sideweather

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
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.maps.*
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.undoBar
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.navigation.SubApp
import hrln.interactive.sidekit.sdk.ui.UndoWindowLifecycle
import hrln.interactive.sidekit.sdk.ui.rememberUndoWindow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** What the forecast row at the foot of each Sideweather page says about the data. */
sealed interface WeatherStatus {
    data object SelectLocation : WeatherStatus
    data object Loading : WeatherStatus
    data object Cached : WeatherStatus
    data object Unavailable : WeatherStatus
    data object Offline : WeatherStatus

    /** Fresh from Open-Meteo at [at] (epoch millis). */
    data class Updated(val at: Long) : WeatherStatus
}

@Composable
fun WeatherStatus.text(): String = when (this) {
    WeatherStatus.SelectLocation -> stringResource(R.string.weather_status_select)
    WeatherStatus.Loading -> stringResource(R.string.weather_status_loading)
    WeatherStatus.Cached -> stringResource(R.string.weather_status_cached)
    WeatherStatus.Unavailable -> stringResource(R.string.weather_status_unavailable)
    WeatherStatus.Offline -> stringResource(R.string.weather_status_offline)
    is WeatherStatus.Updated -> stringResource(R.string.weather_status_updated, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at)))
}

/** [city] looks up the town for a current-location fix; the default (tests) knows none. */
class WeatherViewModel(val repository: WeatherRepository, private val city: suspend (Place) -> String? = { null }) : ViewModel() {
    val forecast = MutableStateFlow<Forecast?>(null)
    val status = MutableStateFlow<WeatherStatus>(WeatherStatus.SelectLocation)

    /**
     * Shows the fix straight away as "Current location", then adds its town once the geocoder
     * answers ("Current location · Madrid"). Offline, the plain name stays until it is next shown.
     */
    fun selectCurrentLocation(fix: Place) {
        repository.select(fix)
        nameCurrentLocation(fix)
    }
    private fun nameCurrentLocation(fix: Place) = viewModelScope.launch {
        city(fix)?.let { repository.rename(fix, currentLocationName(it)) }
    }
    suspend fun visible(place: Place?) {
        forecast.value = null
        if (place == null) return
        // Saved before towns were looked up, or while offline.
        if (place.name == CURRENT_LOCATION) nameCurrentLocation(place)
        forecast.value = repository.cached(place)
        while (currentCoroutineContext().isActive) {
            status.value = if (forecast.value == null) WeatherStatus.Loading else WeatherStatus.Cached
            try {
                forecast.value = repository.refresh(place)
                status.value = WeatherStatus.Updated(forecast.value!!.received)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status.value = if (forecast.value == null) WeatherStatus.Unavailable else WeatherStatus.Offline
            }
            delay(30 * 60_000L)
        }
    }
}

fun NavGraphBuilder.sideweatherGraph(nav: NavHostController) {
    val root = SubApp.Sideweather.route
    navigation(startDestination = "$root/home", route = root) {
        listOf("home", "hourly", "five-day", "locations", "search").forEach { page ->
            composable("$root/$page") { entry ->
                val context = LocalContext.current
                val parent = remember(entry) { nav.getBackStackEntry(root) }
                val vm: WeatherViewModel = viewModel(parent, factory = viewModelFactory { initializer { WeatherViewModel(WeatherRepository.get(context)) { cityName(context.applicationContext, it) } } })
                val settings by vm.repository.settings.state.collectAsStateWithLifecycle()
                val forecast by vm.forecast.collectAsStateWithLifecycle()
                val status by vm.status.collectAsStateWithLifecycle()
                val error by vm.repository.settings.error.collectAsStateWithLifecycle()
                val place = settings?.places?.firstOrNull { it.key == settings?.selected }
                val imperial = settings?.fahrenheit == true
                if (page in listOf("home", "hourly", "five-day")) WhileVisible(place?.key) { vm.visible(place) }
                val back = {
                    nav.popBackStack()
                    Unit
                }
                val locations = {
                    nav.navigate("$root/locations") { launchSingleTop = true }
                    Unit
                }
                val statusText = status.text()
                val options = listOf(SideAction("units", stringResource(if (imperial) R.string.weather_use_celsius else R.string.weather_use_fahrenheit)) { vm.repository.settings.edit { it.copy(fahrenheit = !it.fahrenheit) } })
                when (page) {
                    "search" -> PlaceSearch(back) {
                        vm.repository.select(it)
                        nav.popBackStack()
                    }

                    "locations" -> {
                        val hint = stringResource(R.string.weather_locations_hint)
                        var message by remember { mutableStateOf<String?>(null) }
                        var deleted by remember { mutableStateOf<Place?>(null) }
                        val undoWindow = rememberUndoWindow { deleted = null }
                        UndoWindowLifecycle(undoWindow) { deleted = null }
                        val locate = locationAction(vm::selectCurrentLocation) { message = it }
                        val list = rememberLazyListState()
                        val group = rememberSwipeRevealGroup()
                        val selectedLabel = stringResource(R.string.weather_selected)
                        val add = SideAction("add", stringResource(R.string.weather_add)) { nav.navigate("$root/search") }
                        val undo = deleted?.let { p ->
                            SideAction.undo(contentDescription = stringResource(R.string.weather_undo_delete, p.name)) {
                                undoWindow.cancel()
                                vm.repository.select(p)
                                deleted = null
                            }
                        }
                        SidekitScaffold(stringResource(R.string.weather_locations), actions = if (undo == null) listOf(SideAction.back("back", stringResource(R.string.weather_back), back), add, locate) else undoBar(add, undo)) { padding ->
                            SidekitList(padding, state = list) {
                                item { OutdoorInfo(error ?: message ?: hint, modifier = Modifier.listFade(list, 0)) }
                                val places = settings?.places.orEmpty()
                                items(places.size, key = { places[it].key }) { i ->
                                    val p = places[i]
                                    SidekitSwipeRow(p.key, group, {
                                        vm.repository.delete(p)
                                        deleted = p
                                        undoWindow.start()
                                    }, Modifier.listFade(list, i + 1)) {
                                        OutdoorInfo(p.name, secondary = if (p.key == place?.key) selectedLabel else null, onClick = {
                                            vm.repository.select(p)
                                            back()
                                        })
                                    }
                                }
                            }
                        }
                    }

                    "home" -> SideweatherHomeScreen(forecast?.let { f -> place?.let { f.current(context, it, imperial) } } ?: CurrentConditions(place?.name ?: stringResource(R.string.weather_choose_location), "—", "", ""), forecast?.summary(context, imperial).orEmpty() + WeatherRow(error ?: statusText, ""), { nav.navigate("$root/hourly") }, { nav.navigate("$root/five-day") }, locations, options)

                    else -> ForecastScreen(stringResource(if (page == "hourly") R.string.weather_hourly_title else R.string.weather_five_day_title), (if (page == "hourly") forecast?.hourly(context, imperial) else forecast?.daily(context, imperial)).orEmpty() + WeatherRow(statusText, ""), back, locations, place = place?.name)
                }
            }
        }
    }
}
