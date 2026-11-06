package hrln.interactive.sidekit.home

import android.content.Context
import android.content.SharedPreferences
import hrln.interactive.sidekit.apps.settings.visibilityId
import hrln.interactive.sidekit.navigation.SubApp
import org.json.JSONArray

/**
 * The launcher's home order and dock, stored as stable app IDs.
 *
 * One ordered home list plus a dock set (Phase 0 decision): both the grid and the list render
 * from the same home list, and the dock shows the pinned apps in home order. IDs are the existing
 * [visibilityId] strings (`builtin:Name` for Sidekit apps, `ported:package` for Sideport apps).
 *
 * Unknown or uninstalled IDs are kept in storage but skipped at render time, so a reinstalled app
 * returns to its slot. Newly-seen apps that are not yet in the stored order are appended at the end.
 *
 * Follows the [hrln.interactive.sidekit.design.input.KeyMapper] pattern: a default value, JSON
 * array encode/decode that preserves order, validation on read, atomic writes, and a
 * SharedPreferences observer for live reload.
 */
class HomePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sidekit_home", Context.MODE_PRIVATE)

    /** Full home order that both the grid and list render from. Default = builtin enum order. */
    val homeOrder: List<String>
        get() = read(KEY_HOME_ORDER, SubApp.entries.map { it.visibilityId() })

    /** The dock, stored as its ids. Default = the four built-in dock apps. At most [DOCK_MAX] entries. */
    val dockApps: List<String>
        get() = read(KEY_DOCK_APPS, SubApp.dock.map { it.visibilityId() })

    /** Moves [id] by [delta] (positive = later) in the home order, if it is present. */
    fun move(id: String, delta: Int) {
        val order = homeOrder
        val index = order.indexOf(id)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, order.lastIndex)
        if (target == index) return
        write(KEY_HOME_ORDER, order.toMutableList().apply { removeAt(index); add(target, id) })
    }

    /** Pins [id] to the dock when [pinned], or unpins it. The dock renders in home order. */
    fun setDock(id: String, pinned: Boolean) {
        val current = dockApps
        when {
            pinned && id !in current && current.size < DOCK_MAX ->
                write(KEY_DOCK_APPS, sortByHomeOrder(current + id))
            !pinned && id in current ->
                write(KEY_DOCK_APPS, sortByHomeOrder(current.filterNot { it == id }))
            else -> Unit
        }
    }

    fun isInDock(id: String): Boolean = id in dockApps

    /** Drops any stored order/dock and returns to the built-in defaults. */
    fun reset() {
        prefs.edit().remove(KEY_HOME_ORDER).remove(KEY_DOCK_APPS).apply()
    }

    /**
     * The home order restricted to the [known] ids (installed builtin + ported apps), preserving
     * stored order. Ids that are freshly seen (not yet in the stored order) are appended in [known]
     * order, so a new app appears without a manual edit.
     */
    fun homeOrderFor(known: Iterable<String>): List<String> {
        val stored = homeOrder
        val knownSet = known.toSet()
        return stored.filter { it in knownSet } + known.filter { it !in stored }
    }

    /** The dock ids drawn from [known] and rendered in home order. */
    fun dockFor(known: Iterable<String>): List<String> {
        val dock = dockApps.toSet()
        return homeOrderFor(known).filter { it in dock }
    }

    /** Registers a change listener for home order and dock writes; returns an unregister call. */
    fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_HOME_ORDER || key == KEY_DOCK_APPS || key == null) onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun sortByHomeOrder(ids: List<String>): List<String> {
        val order = homeOrder
        return ids.sortedBy { i -> order.indexOf(i).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    /**
     * Decodes an order-preserving JSON array, dropping blank entries. A missing key returns
     * [default]; malformed data also falls back to [default].
     */
    private fun read(key: String, default: List<String>): List<String> {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> array.optString(i).takeIf { it.isNotBlank() } }
        }.getOrDefault(default)
    }

    /** Encodes as an order-preserving JSON array and writes atomically. */
    private fun write(key: String, value: List<String>) {
        val array = JSONArray().apply { value.forEach { put(it) } }
        prefs.edit().putString(key, array.toString()).apply()
    }

    companion object {
        /** Maximum number of apps allowed in the dock. */
        const val DOCK_MAX = 4

        private const val KEY_HOME_ORDER = "home_order"
        private const val KEY_DOCK_APPS = "dock_apps"
    }
}