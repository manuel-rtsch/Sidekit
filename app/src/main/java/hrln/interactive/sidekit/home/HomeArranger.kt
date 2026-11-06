package hrln.interactive.sidekit.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hrln.interactive.sidekit.apps.settings.visibilityId
import hrln.interactive.sidekit.navigation.SubApp

/**
 * The Edit buffer for the "Arrange apps" screen.
 *
 * Seeds order and dock from the stored [HomePreferences], then lets the user edit a local copy so
 * nothing is persisted until [commit] (Back or Reset only touch this buffer). [order] and [dock]
 * are Compose state so the arrange UI re-renders as the user edits.
 */
class HomeArranger(private val home: HomePreferences, known: Iterable<String>) {

    private val knownIds = known.toList()
    private val builtinIds = SubApp.entries.map { it.visibilityId() }.toSet()

    /** Ported (or otherwise freshly-seen) ids that aren't part of the built-in default list. */
    private val appendedDefaults: List<String> = knownIds.filter { it !in builtinIds }

    var order by mutableStateOf(home.homeOrderFor(knownIds))
        private set

    var dock by mutableStateOf(home.dockFor(knownIds).toSet())
        private set

    fun isInDock(id: String): Boolean = id in dock

    /** Moves [id] by [delta] in the home order (positive = later), clamped at the ends. */
    fun move(id: String, delta: Int) {
        val index = order.indexOf(id)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, order.lastIndex)
        if (target == index) return
        order = order.toMutableList().apply { removeAt(index); add(target, id) }
    }

    /** Pins [id] to the dock (capped at [HomePreferences.DOCK_MAX]) or unpins it. */
    fun toggleDock(id: String) {
        dock = when {
            id in dock -> dock - id
            dock.size < HomePreferences.DOCK_MAX -> dock + id
            else -> dock
        }
    }

    /** Restores the built-in defaults (enum order + freshly-seen apps; the default dock). */
    fun reset() {
        order = SubApp.entries.map { it.visibilityId() } + appendedDefaults
        dock = SubApp.dock.map { it.visibilityId() }.toSet()
    }

    /** Writes the buffer to [HomePreferences]; order first so the dock is rendered in the new order. */
    fun commit() {
        home.saveOrder(order)
        home.saveDock(dock.toList())
    }
}