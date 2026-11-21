package hrln.interactive.sidekit.apps.maps

import android.hardware.GeomagneticField
import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.compass.HeadingSensor
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.navigation.SubApp

class MapsViewModel : ViewModel() {
    var current by mutableStateOf<Place?>(null)
    var destination by mutableStateOf<Place?>(null)
}
fun NavGraphBuilder.liveMapsGraph(nav: NavHostController) {
    val root = SubApp.Maps.route
    navigation(startDestination = "$root/home", route = root) {
        composable("$root/home") { entry ->
            val parent = remember(entry) { nav.getBackStackEntry(root) }
            val vm: MapsViewModel = viewModel(parent)
            LiveMaps(vm) { nav.navigate("$root/search") }
        }
        composable("$root/search") { entry ->
            val parent = remember(entry) { nav.getBackStackEntry(root) }
            val vm: MapsViewModel = viewModel(parent)
            PlaceSearch({ nav.popBackStack() }) {
                vm.destination = it
                nav.popBackStack()
            }
        }
    }
}

@Composable
private fun LiveMaps(vm: MapsViewModel, onSearch: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var message by remember { mutableStateOf(resources.getString(R.string.maps_location_prompt)) }
    var fallback by remember { mutableStateOf(false) }
    var zoom by remember { mutableIntStateOf(0) }
    var recenter by remember { mutableStateOf(false) }
    val location = locationAction({
        vm.current = it
        recenter = true
    }) { message = it }
    val list = rememberLazyListState()
    SidekitScaffold(
        stringResource(R.string.maps_title),
        actions = listOf(
            SideAction.back("search", stringResource(R.string.maps_search), onSearch),
            SideAction("go", stringResource(R.string.maps_go)) {
                val to = vm.destination
                if (to == null) {
                    message = resources.getString(R.string.maps_choose_destination_first)
                } else if (!openDirections(context, to)) {
                    fallback = true
                    message = resources.getString(R.string.maps_no_navigation_app)
                }
            },
            location
        ),
        options = listOf(
            SideAction("zoomIn", stringResource(R.string.maps_zoom_in)) { zoom = kotlin.math.abs(zoom) + 1 },
            SideAction("zoomOut", stringResource(R.string.maps_zoom_out)) { zoom = -kotlin.math.abs(zoom) - 1 },
            SideAction("center", stringResource(R.string.maps_centre)) { recenter = !recenter }
        )
    ) { padding ->
        SidekitList(padding, state = list) {
            val center = if (recenter) vm.current ?: vm.destination else vm.destination ?: vm.current
            if (center != null) item { Box(Modifier.listFade(list, 0)) { RasterMap(center, vm.current, vm.destination, zoom) { if (!fallback) message = it } } }
            item { OutdoorInfo(vm.destination?.displayName ?: stringResource(R.string.maps_choose_destination), secondary = vm.current?.let { stringResource(R.string.maps_you_at, it.latitude, it.longitude) } ?: stringResource(R.string.maps_location_not_available), modifier = Modifier.listFade(list, if (center == null) 0 else 1)) }
            if (fallback && vm.current != null && vm.destination != null) item { StraightLine(vm.current!!, vm.destination!!) }
            item { OutdoorInfo(message, modifier = Modifier.listFade(list, 2)) }
        }
    }
}

@Composable
private fun StraightLine(from: Place, to: Place) {
    val context = LocalContext.current
    val sensor = remember { HeadingSensor(context) }
    val result = remember(from, to) { FloatArray(3).also { Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, it) } }
    val declination = remember(from) { GeomagneticField(from.latitude.toFloat(), from.longitude.toFloat(), 0f, System.currentTimeMillis()).declination }
    var accuracy by remember { mutableIntStateOf(0) }
    WhileVisible(sensor) { sensor.accuracy.collect { accuracy = it } }
    val color = SidekitTheme.colors.emphasisA1
    SidekitTile(Modifier.fillMaxWidth().height(230.dp)) {
        Column {
            SidekitText(stringResource(R.string.maps_distance_bearing, result[0].toInt(), ((result[1] + 360) % 360).toInt()), Modifier.padding(SidekitTheme.spacing.itemPadding))
            if (sensor.available) {
                Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                    rotate(result[1] - (sensor.angle.floatValue + declination)) {
                        drawLine(color, center + Offset(0f, 30.dp.toPx()), center - Offset(0f, 30.dp.toPx()), 5.dp.toPx())
                        drawLine(color, center - Offset(0f, 30.dp.toPx()), center + Offset(-15.dp.toPx(), -10.dp.toPx()), 5.dp.toPx())
                        drawLine(color, center - Offset(0f, 30.dp.toPx()), center + Offset(15.dp.toPx(), -10.dp.toPx()), 5.dp.toPx())
                    }
                }
            }
            SidekitText(
                if (!sensor.available) {
                    stringResource(R.string.maps_no_compass)
                } else if (accuracy <= 1) {
                    stringResource(R.string.maps_calibrate)
                } else {
                    stringResource(R.string.maps_hold_flat)
                },
                style = SidekitTheme.typography.small
            )
        }
    }
}
