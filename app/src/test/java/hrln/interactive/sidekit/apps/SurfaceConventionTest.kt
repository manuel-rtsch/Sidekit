package hrln.interactive.sidekit.apps

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2: the list is the navigation surface and the action bar is the action/status surface.
 * Settings and Sideport must not add a third surface with in-body line buttons. Ported-app
 * content is the one exception: those buttons belong to the foreign app, not Sidekit chrome.
 */
class SurfaceConventionTest {

    @Test
    fun `settings and sideport do not add a third action surface`() {
        val offences = sources().flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                line.contains("SidekitLineButton(")
            }.map { (index, line) -> "${file.name}:${index + 1} $line" }
        }
        assertTrue(
            "Use rows for navigation and the action bar for actions:\n" + offences.joinToString("\n"),
            offences.isEmpty()
        )
    }

    private fun sources(): List<File> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/hrln/interactive/sidekit/apps") }
            .firstOrNull { it.isDirectory }
            ?: error("apps source tree not found")
        return listOf(File(root, "settings"), File(root, "sideport"))
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" && it.name != "ProjectionScreens.kt" }
                    .toList()
            }
    }
}
