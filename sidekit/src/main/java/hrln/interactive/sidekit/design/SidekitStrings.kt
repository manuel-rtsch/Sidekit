package hrln.interactive.sidekit.design

import android.content.res.Resources
import androidx.annotation.StringRes

/**
 * The design library's own strings for code that runs without a Context, such as the default
 * labels of the [hrln.interactive.sidekit.design.action.SideAction] factories and the Options bar.
 *
 * [hrln.interactive.sidekit.design.theme.SidekitTheme] points it at the composing Activity's
 * resources, so the text follows the app language. Without them (plain JVM tests) it falls back
 * to the English text the caller passes.
 */
internal object SidekitStrings {
    @Volatile
    private var resources: Resources? = null

    fun attach(resources: Resources) {
        this.resources = resources
    }

    /** [id] formatted with [args] from the attached resources, or [english] when none are attached. */
    fun get(@StringRes id: Int, english: String, vararg args: Any): String {
        val attached = resources?.let { runCatching { it.getString(id, *args) }.getOrNull() }
        return attached ?: if (args.isEmpty()) english else english.format(*args)
    }
}
