package hrln.interactive.sidekit.apps

import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.undoBar
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Undo has one shape: the bar keeps its two slots and the exit never moves. While a delete can be
 * taken back the right slot offers "Undo" instead of the main action; the main action returns when
 * the window closes.
 */
class UndoConventionTest {

    private fun action(key: String, label: String) = SideAction(key, label) {}

    @Test
    fun `undo takes the right slot and leaves the exit alone`() {
        val bar = undoBar(exit = action("back", "Back"), primary = action("new", "New"), undo = SideAction.undo {})
        assertEquals(listOf("back", "undo"), bar.map { it.key })
    }

    @Test
    fun `the primary returns when there is nothing to undo`() {
        val bar = undoBar(exit = action("back", "Back"), primary = action("new", "New"), undo = null)
        assertEquals(listOf("back", "new"), bar.map { it.key })
    }

    @Test
    fun `a root list with no exit shows undo alone`() {
        val bar = undoBar(primary = action("new", "New"), undo = SideAction.undo {})
        assertEquals(listOf("undo"), bar.map { it.key })
    }

    @Test
    fun `a draft keeps Send and adds undo third`() {
        val bar = undoBar(
            exit = action("back", "Back"),
            primary = action("send", "Send"),
            undo = SideAction.undo {},
            keepPrimary = true,
        )
        assertEquals(listOf("back", "send", "undo"), bar.map { it.key })
    }

    @Test
    fun `undo is muted and rises in place`() {
        val undo = SideAction.undo(contentDescription = "Undo delete Barclays") {}
        assertEquals(ActionTone.Muted, undo.tone)
        assertEquals("Undo delete Barclays", undo.contentDescription)
    }

    /**
     * The shape above only holds if every bar asks for it. A hand-built "Undo" action is how the
     * suite drifted into takeover and three-button bars, so fail on one.
     */
    @Test
    fun `no screen builds its own Undo action`() {
        val offences = appSources().flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                line.contains("SideAction(") && line.contains("\"Undo\"")
            }.map { (index, line) -> "${file.name}:${index + 1} $line" }
        }
        assertTrue(
            "Build Undo with SideAction.undo and place it with undoBar:\n" + offences.joinToString("\n"),
            offences.isEmpty(),
        )
    }

    private fun appSources(): List<File> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/hrln/interactive/sidekit/apps") }
            .firstOrNull { it.isDirectory }
            ?: error("apps source tree not found")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
