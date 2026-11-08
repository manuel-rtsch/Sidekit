package hrln.interactive.sidekit.apps

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two tone conventions the suite already follows by majority:
 *
 * - a destructive action must be [hrln.interactive.sidekit.design.action.ActionTone.Destructive];
 * - a commit action (save / send / done / confirm / record) must be
 *   [hrln.interactive.sidekit.design.action.ActionTone.Secondary].
 *
 * Source-scanning rather than reflection: action bars are built inside composables, so the only
 * place the label and the tone sit next to each other is the source itself.
 */
class ActionToneConventionTest {

    private val destructiveLabels = setOf(
        "delete", "delete series", "delete all", "remove", "remove all", "erase",
        "erase and start over", "uninstall", "discard", "clear history", "clear all ticked",
        "reset keys"
    )

    private val commitLabels = setOf("save", "send", "done", "confirm", "record")

    /** Reversing actions: they must never look like a way forward or a promise to commit. */
    private val mutedLabels = setOf("cancel", "undo")

    /** Labels that share a word with a commit action but are not one. */
    private val commitHomonyms = setOf(
        "WebBrowserScreen.kt:Save", // bookmark toggle, not a commit
        "ArrangeHomeScreen.kt:Done", // leaves the arrange mode
        "MailGraph.kt:Save" // saves a draft; the message itself is committed by Send
    )

    private companion object {
        /** Tone supplied at runtime (ported apps map the source app's tone); nothing to assert. */
        const val COMPUTED = "computed"
    }

    private data class Action(val file: String, val line: Int, val labels: List<String>, val tone: String?, val text: String)

    @Test
    fun `destructive actions use the destructive tone`() {
        val offences = actions().filter { action ->
            action.tone != COMPUTED &&
                action.labels.any { it.lowercase() in destructiveLabels } &&
                action.tone != "ActionTone.Destructive"
        }
        assertTrue(
            "Destructive actions must use tone = ActionTone.Destructive:\n" +
                offences.joinToString("\n") { "${it.file}:${it.line} ${it.labels}" },
            offences.isEmpty()
        )
    }

    @Test
    fun `commit actions use the secondary tone`() {
        val offences = actions().filter { action ->
            val label = action.labels.firstOrNull { it.lowercase() in commitLabels } ?: return@filter false
            action.tone != COMPUTED &&
                "${action.file}:$label" !in commitHomonyms && action.tone != "ActionTone.Secondary"
        }
        assertTrue(
            "Commit actions must use tone = ActionTone.Secondary:\n" +
                offences.joinToString("\n") { "${it.file}:${it.line} ${it.labels}" },
            offences.isEmpty()
        )
    }

    @Test
    fun `reversing actions are muted`() {
        val offences = actions().filter { action ->
            action.tone != COMPUTED &&
                action.labels.any { it.lowercase() in mutedLabels } && action.tone != "ActionTone.Muted"
        }
        assertTrue(
            "Cancel and Undo must use tone = ActionTone.Muted:\n" +
                offences.joinToString("\n") { "${it.file}:${it.line} ${it.labels} ${it.tone}" },
            offences.isEmpty()
        )
    }

    private fun actions(): List<Action> = sourceFiles().flatMap { file ->
        val text = file.readText()
        val call = Regex("""SideAction(?:\.\w+)?\(""").findAll(text)
        call.mapNotNull { match ->
            val open = match.range.last
            val close = matchingParen(text, open) ?: return@mapNotNull null
            val body = text.substring(open + 1, close)
            val args = splitTopLevel(body)
            val labelExpr = args.getOrNull(1)?.let { it.substringAfter("label =", it).trim() } ?: return@mapNotNull null
            val labels = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(labelExpr)
                .map { it.groupValues[1] }
                .filter { it.isNotBlank() }
                .toList()
            if (labels.isEmpty()) return@mapNotNull null
            Action(
                file = file.name,
                line = text.take(match.range.first).count { it == '\n' } + 1,
                labels = labels,
                tone = if (roleOf(match.value) != null) COMPUTED else toneOf(args),
                text = body
            )
        }
    }

    // The tone argument, if it is a literal. Null when absent; COMPUTED when a screen works the
    // tone out at runtime (the ported apps map the original app's tone) — nothing to assert there.
    // The role factory, if any (SideAction.save / send / delete), fixes the tone in one place.
    private fun roleOf(callee: String): String? = Regex("""SideAction\.(save|send|delete|cancel)\(""").matchEntire(callee)?.groupValues?.get(1)

    private fun toneOf(args: List<String>): String? {
        val arg = args.firstOrNull { it.trimStart().startsWith("tone") } ?: return null
        return Regex("""ActionTone\.(Primary|Secondary|Destructive|Muted)""").find(arg)?.value ?: COMPUTED
    }

    private fun sourceFiles(): List<File> {
        var dir: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/hrln/interactive/sidekit/apps")
            if (candidate.isDirectory) return candidate.walkTopDown().filter { it.extension == "kt" }.toList()
            dir = dir.parentFile
        }
        error("Could not find app/src/main/java/hrln/interactive/sidekit/apps from ${System.getProperty("user.dir")}")
    }

    private fun matchingParen(text: String, open: Int): Int? {
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '"' -> {
                    i = skipString(text, i)
                }

                '\'' -> {
                    i = skipChar(text, i)
                }

                '(' -> depth++

                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    private fun skipString(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            when {
                text[i] == '\\' -> i += 2
                text[i] == '"' -> return i
                else -> i++
            }
        }
        return text.length - 1
    }

    private fun skipChar(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            when {
                text[i] == '\\' -> i += 2
                text[i] == '\'' -> return i
                else -> i++
            }
        }
        return text.length - 1
    }

    private fun splitTopLevel(body: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '"' -> {
                    val end = skipString(body, i)
                    current.append(body, i, end + 1)
                    i = end
                }

                c == '\'' -> {
                    val end = skipChar(body, i)
                    current.append(body, i, end + 1)
                    i = end
                }

                c in "([{" -> {
                    depth++
                    current.append(c)
                }

                c in ")]}" -> {
                    depth--
                    current.append(c)
                }

                c == ',' && depth == 0 -> {
                    out += current.toString()
                    current.clear()
                }

                else -> current.append(c)
            }
            i++
        }
        if (current.isNotBlank()) out += current.toString()
        return out
    }
}
