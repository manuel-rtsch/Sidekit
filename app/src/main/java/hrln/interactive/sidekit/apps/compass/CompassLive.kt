package hrln.interactive.sidekit.apps.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.maps.*
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.undoBar
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.sdk.ui.UndoWindowLifecycle
import hrln.interactive.sidekit.sdk.ui.rememberUndoWindow
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

private data class HeadingBookmark(val time: Long, val heading: Float, val place: Place?)
private object Bookmarks {
    private var store: OutdoorStore<List<HeadingBookmark>>? = null

    @Synchronized fun get(context: android.content.Context): OutdoorStore<List<HeadingBookmark>> = store ?: OutdoorStore(
        File(context.filesDir, "compass.json"),
        { j ->
            val a = j.getJSONArray("items")
            List(a.length()) {
                val o = a.getJSONObject(it)
                HeadingBookmark(o.getLong("time"), o.getDouble("heading").toFloat(), o.optJSONObject("place")?.let(Place::parse))
            }
        },
        { items: List<HeadingBookmark> -> JSONObject().put("items", JSONArray(items.map { JSONObject().put("time", it.time).put("heading", it.heading).put("place", it.place?.json()) })) },
        emptyList(),
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    ).also { store = it }
}

@Composable
fun LiveCompass(onBookmarks: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val sensor = remember { HeadingSensor(context) }
    val store = remember { Bookmarks.get(context) }
    val loaded by store.state.collectAsStateWithLifecycle()
    val error by store.errorRes.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf(resources.getString(if (sensor.available) R.string.compass_hold_flat else R.string.compass_no_sensor)) }
    var lowAccuracy by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf<Place?>(null) }
    val location = locationAction({ place = it }) { status = it }
    WhileVisible(sensor) { sensor.accuracy.collect { lowAccuracy = it <= android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW } }
    val list = rememberLazyListState()
    val measurer = rememberTextMeasurer()
    val bearingStyle = SidekitTheme.typography.title.copy(color = SidekitTheme.colors.light)
    SidekitScaffold(
        stringResource(R.string.compass_title),
        actions = listOf(
            SideAction("calibrate", stringResource(R.string.compass_calibrate)) { status = resources.getString(R.string.compass_calibrate_hint) },
            SideAction.save("save", stringResource(R.string.compass_save)) {
                val heading = sensor.latest
                if (heading == null) {
                    status = resources.getString(R.string.compass_waiting_heading)
                } else if (loaded == null) {
                    status = resources.getString(R.string.compass_loading_bookmarks)
                } else {
                    store.edit { (listOf(HeadingBookmark(System.currentTimeMillis(), heading, place)) + it).take(100) }
                    status = resources.getString(if (place == null) R.string.compass_saved_without_location else R.string.compass_saved_with_location)
                }
            }
        ),
        options = listOf(location)
    ) { padding ->
        SidekitList(padding, state = list) {
            item { Box(Modifier.fillMaxWidth().height(278.dp).listFade(list, 0), contentAlignment = androidx.compose.ui.Alignment.TopCenter) { CompassDial({ sensor.angle.floatValue }, Modifier.padding(top = 5.dp)) } }
            item {
                SidekitTile(Modifier.fillMaxWidth().listFade(list, 1)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val text = if (sensor.latest == null) resources.getString(R.string.compass_waiting) else resources.getString(R.string.compass_degrees_magnetic, sensor.angle.floatValue.toInt())
                        val layout = measurer.measure(text, bearingStyle)
                        drawText(layout, topLeft = Offset(30.dp.toPx(), (size.height - layout.size.height) / 2))
                    }
                }
            }
            item { OutdoorInfo(error?.let { stringResource(it) } ?: if (lowAccuracy) stringResource(R.string.compass_low_accuracy) else status, modifier = Modifier.listFade(list, 2)) }
            item { OutdoorInfo(stringResource(R.string.compass_location_optional), secondary = stringResource(R.string.compass_location_optional_detail), modifier = Modifier.listFade(list, 3)) }
            item(key = "bookmarks") {
                SidekitItem(stringResource(R.string.compass_bookmarks), Modifier.listFade(list, 4), showLinkArrow = true, onClick = onBookmarks)
            }
        }
    }
}

@Composable
fun CompassBookmarks(onBack: () -> Unit) {
    val store = Bookmarks.get(LocalContext.current)
    val rows by store.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val group = rememberSwipeRevealGroup()
    var deleted by remember { mutableStateOf<HeadingBookmark?>(null) }
    val undoWindow = rememberUndoWindow { deleted = null }
    UndoWindowLifecycle(undoWindow) { deleted = null }
    SidekitScaffold(
        stringResource(R.string.compass_saved_headings),
        actions = undoBar(
            continueWith = SideAction.back("back", stringResource(R.string.compass_back), onBack),
            undo = deleted?.let { p ->
                SideAction.undo(label = stringResource(R.string.compass_undo), contentDescription = stringResource(R.string.compass_undo_delete)) {
                    undoWindow.cancel()
                    store.edit { (it + p).sortedByDescending { b -> b.time } }
                    deleted = null
                }
            }
        )
    ) { padding ->
        SidekitList(padding, state = list) {
            if (rows.isNullOrEmpty()) item { OutdoorInfo(stringResource(R.string.compass_no_saved), modifier = Modifier.listFade(list, 0)) }
            items(rows.orEmpty().size) { i ->
                val b = rows!![i]
                SidekitSwipeRow(b.time, group, {
                    store.edit { it - b }
                    deleted = b
                    undoWindow.start()
                }, Modifier.listFade(list, i)) {
                    OutdoorInfo(stringResource(R.string.compass_degrees_magnetic, b.heading.toInt()), secondary = b.place?.let { "${it.latitude}, ${it.longitude}" } ?: stringResource(R.string.compass_no_location))
                }
            }
        }
    }
}
