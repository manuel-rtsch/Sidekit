package hrln.interactive.sidekit.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class HomePreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val allIds: List<String> = SubApp.entries.map { it.visibilityId() }

    // Robolectric reuses one application across test methods, so start each test from defaults.
    @Before
    fun clearPrefs() {
        HomePreferences(context).reset()
    }

    private fun fresh() = HomePreferences(context)

    @Test
    fun `defaults equal the current hardcoded home and dock`() {
        val home = fresh()
        assertEquals(SubApp.entries.map { it.visibilityId() }, home.homeOrder)
        assertEquals(SubApp.dock.map { it.visibilityId() }, home.dockApps)
        assertEquals(
            listOf("builtin:Sidemessage", "builtin:Sidenotes", "builtin:Sideticks", "builtin:Phonebook"),
            home.homeOrder.take(4),
        )
    }

    @Test
    fun `order and dock round-trip through a new instance`() {
        fresh().also { home ->
            home.move(allIds[0], 3)                 // Sidemessage +3
            home.move(allIds[1], 2)                 // Sidenotes  +2
            home.setDock("builtin:Phonebook", false) // free a dock slot
            home.setDock(allIds.last(), true)       // pin Sideport in its place
        }
        val restored = fresh()
        // Start: [Sidemessage, Sidenotes, Sideticks, Phonebook, ...]
        // after Sidemessage+3 -> [Sidenotes, Sideticks, Phonebook, Sidemessage, ...]
        // after Sidenotes+2   -> [Sideticks, Phonebook, Sidenotes, Sidemessage, ...]
        assertEquals(listOf("builtin:Sideticks", "builtin:Phonebook", "builtin:Sidenotes", "builtin:Sidemessage"), restored.homeOrder.take(4))
        assertTrue(restored.isInDock(allIds.last()))
        assertFalse(restored.isInDock("builtin:Phonebook"))
        assertEquals(4, restored.dockApps.size)
    }

    @Test
    fun `move keeps a stable order and clamps at the ends`() {
        val home = fresh()
        home.move(allIds.first(), -10)        // already at top
        assertEquals(allIds.first(), home.homeOrder.first())
        home.move(allIds.first(), 200)        // past the end -> last
        assertEquals(allIds.first(), home.homeOrder.last())
        home.move("builtin:DoesNotExist", 1)  // unknown id is ignored
        assertEquals(allIds.first(), home.homeOrder.last())
    }

    @Test
    fun `setDock caps at four and unpinning restores a slot`() {
        val home = fresh()
        assertTrue(home.isInDock(allIds.first()))
        assertEquals(4, home.dockApps.size)

        // Dock is full; further pins are refused.
        allIds.drop(4).forEach { home.setDock(it, true) }
        assertEquals(4, home.dockApps.size)
        assertFalse(home.isInDock(allIds[4]))

        // Free a slot, then pin a new app.
        home.setDock(allIds.first(), false)
        home.setDock(allIds[4], true)
        assertTrue(home.isInDock(allIds[4]))
        assertEquals(4, home.dockApps.size)
    }

    @Test
    fun `unknown ids are skipped at render but still stored`() {
        val home = fresh()
        // Home still carries every default id (the missing app stays in storage) ...
        assertTrue(home.homeOrder.contains("builtin:Sideticks"))
        // ... but a known set without it renders without pulling it back in.
        val known = allIds.filterNot { it == "builtin:Sideticks" }
        assertEquals(known, home.homeOrderFor(known))

        val dock = home.dockFor(known)
        assertEquals(listOf("builtin:Sidemessage", "builtin:Sidenotes", "builtin:Phonebook"), dock)
    }

    @Test
    fun `newly seen ids are appended after the stored order`() {
        val home = fresh()
        val seen = allIds + "ported:com.example.New"
        val order = home.homeOrderFor(seen)
        assertEquals(allIds, order.take(allIds.size))
        assertEquals("ported:com.example.New", order.last())
    }

    @Test
    fun `dock renders in home order`() {
        val home = fresh()
        home.move("builtin:Phonebook", -2)   // [Sidemessage, Phonebook, Sidenotes, Sideticks, ...]
        home.move("builtin:Sidenotes", 1)    // [Sidemessage, Phonebook, Sideticks, Sidenotes, ...]
        val expected = home.homeOrderFor(allIds).filter { it in home.dockApps }
        assertEquals(expected, home.dockFor(allIds))
    }

    @Test
    fun `reset restores defaults`() {
        fresh().also { home ->
            home.move(allIds.first(), 1)
            home.setDock(allIds.first(), false)
            home.reset()
        }
        assertEquals(SubApp.entries.map { it.visibilityId() }, fresh().homeOrder)
        assertEquals(SubApp.dock.map { it.visibilityId() }, fresh().dockApps)
    }

    @Test
    fun `observer fires on order and dock changes until unregistered`() {
        val home = fresh()
        var calls = 0
        val unregister = home.observe { calls++ }

        home.move(allIds.first(), 1)          // order change
        home.setDock(allIds.first(), false)   // dock change
        home.setDock(allIds.first(), true)    // dock change back
        assertEquals(3, calls)

        unregister()
        home.move(allIds.first(), 1)
        assertEquals(3, calls)
    }
}