package hrln.interactive.sidekit.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hrln.interactive.sidekit.apps.sideport.registry.PortedApp
import hrln.interactive.sidekit.apps.settings.visibilityId
import hrln.interactive.sidekit.navigation.SubApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Arrange-apps edit buffer: seeds from stored `HomePreferences`, keeps edits local, and only
 * persists on [HomeArranger.commit]. Covers the dock cap, reorder, reset and commit ordering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class HomeArrangerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val ported = listOf(PortedApp("test.matrix", "Matrix"))
    private val subsById = SubApp.entries.associateBy { it.visibilityId() }
    private val portedById = ported.associateBy { it.visibilityId() }
    private val known = subsById.keys + portedById.keys

    @Before
    fun cleanPrefs() {
        HomePreferences(context).reset()
    }

    @Test
    fun `seeds from stored order and dock, appending freshly-seen apps`() {
        val home = HomePreferences(context)
        home.move("builtin:Photos", 2)            // persisted order change
        val arranger = HomeArranger(home, known)

        // home.homeOrder is the raw stored list; the arranger appends the freshly-seen ported id.
        assertEquals(home.homeOrder + listOf("ported:test.matrix"), arranger.order)
        assertEquals(home.dockFor(known).toSet(), arranger.dock)
        assertEquals(setOf("builtin:Sidemessage", "builtin:Sidenotes", "builtin:Sideticks", "builtin:Phonebook"), arranger.dock)
        // Unknown/unseen/missing ids are dropped from the buffer order.
        assertFalse(arranger.order.contains("builtin:Nope"))
    }

    @Test
    fun `move reorders within bounds and clamps at the ends`() {
        val home = HomePreferences(context)
        val arranger = HomeArranger(home, known)
        val photos = "builtin:Photos"
        // Default index of Photos is 4.
        assertEquals(4, arranger.order.indexOf(photos))
        arranger.move(photos, 1)
        assertEquals(5, arranger.order.indexOf(photos))
        arranger.move(photos, -100)
        assertEquals(0, arranger.order.indexOf(photos))
        arranger.move(photos, 100)
        assertEquals(arranger.order.lastIndex, arranger.order.indexOf(photos))
        // No-op: moving by zero, or an id not in the order.
        val before = arranger.order.toList()
        arranger.move(photos, 0)
        arranger.move("builtin:Missing", 1)
        assertEquals(before, arranger.order)
    }

    @Test
    fun `dock toggles are capped at four and support ported apps`() {
        val home = HomePreferences(context)
        val arranger = HomeArranger(home, known)
        val photos = "builtin:Photos"
        val matrix = "ported:test.matrix"

        arranger.toggleDock(photos)
        assertEquals(4, arranger.dock.size)          // default dock is already full -> refused
        assertFalse(arranger.isInDock(photos))

        arranger.toggleDock("builtin:Phonebook")     // unpin -> 3
        assertEquals(3, arranger.dock.size)
        arranger.toggleDock(photos)                  // pin -> 4
        assertTrue(arranger.isInDock(photos))
        arranger.toggleDock(matrix)                  // full -> refused (ported app stays out)
        assertFalse(arranger.isInDock(matrix))

        arranger.toggleDock(photos)                  // unpin -> 3, frees a slot
        arranger.toggleDock(matrix)                  // pin a ported app
        assertTrue(arranger.isInDock(matrix))
        assertEquals(4, arranger.dock.size)
    }

    @Test
    fun `reset restores built-in defaults plus freshly-seen apps`() {
        val home = HomePreferences(context)
        val arranger = HomeArranger(home, known)
        arranger.move("builtin:Photos", 3)
        arranger.toggleDock("builtin:Phonebook")
        arranger.toggleDock("builtin:Sidenotes")

        arranger.reset()
        assertEquals(SubApp.entries.map { it.visibilityId() } + listOf("ported:test.matrix"), arranger.order)
        assertEquals(SubApp.dock.map { it.visibilityId() }.toSet(), arranger.dock)
    }

    @Test
    fun `commit persists order then dock, in the new home order, capped at four`() {
        val home = HomePreferences(context)
        val arranger = HomeArranger(home, known)
        arranger.move("builtin:Photos", 2)           // Photos -> index 6
        arranger.toggleDock("builtin:Phonebook")      // unpin
        arranger.toggleDock("builtin:Photos")         // pin in the freed slot
        arranger.commit()

        val stored = HomePreferences(context)
        assertEquals(arranger.order, stored.homeOrder)
        assertTrue(stored.isInDock("builtin:Photos"))
        assertFalse(stored.isInDock("builtin:Phonebook"))
        assertTrue(stored.dockApps.size <= HomePreferences.DOCK_MAX)
        // Dock renders in the committed home order: it is the home order, restricted to the dock.
        val dockSet = stored.dockApps.toSet()
        assertEquals(stored.homeOrder.filter { it in dockSet }, stored.dockApps)
    }
}