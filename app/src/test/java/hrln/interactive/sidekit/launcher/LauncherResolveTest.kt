package hrln.interactive.sidekit.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hrln.interactive.sidekit.apps.settings.visibilityId
import hrln.interactive.sidekit.apps.sideport.registry.PortedApp
import hrln.interactive.sidekit.home.HomePreferences
import hrln.interactive.sidekit.navigation.SubApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Glue between [HomePreferences] and the launcher renderer: the configured home order and dock
 * ids must resolve to [LauncherEntry]s (builtin or ported) in the correct order, dropping anything
 * the launcher does not know about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LauncherResolveTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val subsById = SubApp.entries.associateBy { it.visibilityId() }
    private val portedApps = listOf(
        PortedApp("test.matrix", "Matrix"),
        PortedApp("test.calendar", "Foreign Calendar"),
    )
    private val portedById = portedApps.associateBy { it.visibilityId() }
    private val known = subsById.keys + portedById.keys

    @Before
    fun cleanPrefs() {
        HomePreferences(context).reset()
    }

    @Test
    fun `defaults resolve to builtins in declaration order then ported apps`() {
        val home = HomePreferences(context)
        val entries = toLauncherEntries(home.homeOrderFor(known), subsById, portedById)
        assertEquals(SubApp.entries.map { it.visibilityId() } + portedById.keys, entries.map { it.id })
        assertTrue(entries.filterIsInstance<PortedEntry>().map { it.id } == listOf("ported:test.matrix", "ported:test.calendar"))
    }

    @Test
    fun `default dock resolves to the four built-ins in home order`() {
        val home = HomePreferences(context)
        val dock = toLauncherEntries(home.dockFor(known), subsById, portedById)
        assertEquals(SubApp.dock.take(HomePreferences.DOCK_MAX).map { it.visibilityId() }, dock.map { it.id })
    }

    @Test
    fun `custom dock renders in home order and can hold a ported app`() {
        val home = HomePreferences(context)
        SubApp.dock.forEach { home.setDock(it.visibilityId(), false) } // clear the default dock
        home.setDock("builtin:Camera", true)
        home.setDock("builtin:Mail", true)
        // Pin a ported app into the second freed slot.
        home.setDock("ported:test.calendar", true)

        val dock = toLauncherEntries(home.dockFor(known), subsById, portedById)
        // Home order has Mail before Camera, ported apps appended last, so dock = [Mail, Camera, ForeignCalendar].
        assertEquals(listOf("builtin:Mail", "builtin:Camera", "ported:test.calendar"), dock.map { it.id })
    }

    @Test
    fun `reordering home order reorders the shared grid and list order`() {
        val home = HomePreferences(context)
        val sideticks = SubApp.Sideticks.visibilityId()
        val phonebook = SubApp.Phonebook.visibilityId()
        // Move Sideticks (index 2) to the end-ish; move Phonebook (index 3) before it.
        home.move(sideticks, 20)
        home.move(phonebook, -2)

        val entries = toLauncherEntries(home.homeOrderFor(known), subsById, portedById)
        // Sideticks moved later, so Phonebook now comes first of the two.
        assertTrue(entries.map { it.id }.indexOf(phonebook) < entries.map { it.id }.indexOf(sideticks))
    }

    @Test
    fun `unknown ids are dropped at the boundary`() {
        val entries = toLauncherEntries(
            listOf("builtin:Sidemessage", "builtin:DoesNotExist", "ported:unknown.pkg"),
            subsById, portedById,
        )
        assertEquals(listOf("builtin:Sidemessage"), entries.map { it.id })
    }
}