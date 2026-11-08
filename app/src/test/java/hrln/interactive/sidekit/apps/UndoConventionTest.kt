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
    fun `undo joins the primary on the right and adds nothing else`() {
        val bar = undoBar(continueWith = action("new", "New"), undo = SideAction.undo {})
        assertEquals(listOf("new", "undo"), bar.map { it.key })
    }

    @Test
    fun `the primary keeps the left slot and the exit steps aside`() {
        val bar = undoBar(continueWith = action("compose", "Compose"), undo = SideAction.undo {})
        assertEquals(listOf("compose", "undo"), bar.map { it.key })
        assertEquals(2, bar.size)
    }

    @Test
    fun `the bar is unchanged when there is nothing to undo`() {
        val bar = undoBar(continueWith = action("back", "Back"), undo = null)
        assertEquals(listOf("back"), bar.map { it.key })
    }

    @Test
    fun `a draft never grows an undo button`() {
        val bar = undoBar(continueWith = action("send", "Send"), undo = null)
        assertEquals(listOf("send"), bar.map { it.key })
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
