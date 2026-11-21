package hrln.interactive.sidekit.apps

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The English text of app string resources, for the source-scanning convention tests: a label
 * written as `stringResource(R.string.mail_delete)` is checked as the "Delete" it shows.
 */
internal object EnglishStrings {
    private val reference = Regex("""R\.string\.(\w+)""")

    val all: Map<String, String> by lazy {
        val values = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/res/values") }
            .firstOrNull { it.isDirectory }
            ?: error("app/src/main/res/values not found")
        values.listFiles { f -> f.extension == "xml" }.orEmpty().flatMap { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) as Element }.map { it.getAttribute("name") to it.textContent.replace("\\'", "'").replace("\\\"", "\"") }
        }.toMap()
    }

    /** The English text of every `R.string.*` named in [expression]. */
    fun referencedIn(expression: String): List<String> = reference.findAll(expression).mapNotNull { all[it.groupValues[1]] }.toList()
}
