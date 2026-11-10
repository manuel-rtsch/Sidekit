package hrln.interactive.sidekit.apps

import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.NoticeKind
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.withNotice
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A transient message has one shape: the bar is replaced by a single dense Muted notice that
 * dismisses on tap. Screens ask for it with [withNotice] instead of hand-building the action, so
 * the same save/delete/error feedback appears in the same place across the suite.
 */
class NoticeConventionTest {

    private fun action(key: String, label: String) = SideAction(key, label) {}

    @Test
    fun `a notice replaces the bar with one dismissible message`() {
        val bar = withNotice("Saved", {}, listOf(action("back", "Back"), action("save", "Save")))
        assertEquals(1, bar.size)
        val notice = bar.single()
        assertEquals("notice", notice.key)
        assertEquals("Saved", notice.label)
        assertEquals(ActionTone.Muted, notice.tone)
        assertTrue(notice.dense)
        assertEquals(ActionMotion.Up, notice.motion)
        assertEquals(NoticeKind.Other.autoDismissMs, notice.autoDismissMs)
    }

    @Test
    fun `a success notice leaves sooner than an error`() {
        val success = withNotice("Saved", {}, emptyList(), NoticeKind.Success).single()
        val other = withNotice("Could not save", {}, emptyList(), NoticeKind.Other).single()
        assertEquals(2_000L, success.autoDismissMs)
        assertEquals(5_000L, other.autoDismissMs)
    }

    @Test
    fun `no notice leaves the bar alone`() {
        val actions = listOf(action("back", "Back"), action("save", "Save"))
        assertSame(actions, withNotice(null, {}, actions))
    }

    @Test
    fun `no screen builds its own notice action`() {
        val offences = appSources().flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                line.contains("SideAction(") && line.contains("\"notice\"")
            }.map { (index, line) -> "${file.name}:${index + 1} $line" }
        }
        assertTrue(
            "Build notices with withNotice:\n" + offences.joinToString("\n"),
            offences.isEmpty()
        )
    }

    @Test
    fun `no screen defines its own withNotice`() {
        val offences = appSources().flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                line.contains("fun withNotice(")
            }.map { (index, line) -> "${file.name}:${index + 1} $line" }
        }
        assertTrue(
            "Use the shared withNotice from design/action:\n" + offences.joinToString("\n"),
            offences.isEmpty()
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
