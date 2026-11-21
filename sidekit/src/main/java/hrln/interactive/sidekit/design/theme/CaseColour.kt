package hrln.interactive.sidekit.design.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import hrln.interactive.sidekit.design.R

/**
 * The Sidephone case colours offered by Sidekit.
 *
 * These are shared with every Sidekit-native app so Settings renders the same swatches and the
 * design system applies the same palette everywhere.
 *
 * [label] is the English name, kept for callers that still show it; [labelRes] (through
 * [label] with a Context, or [displayLabel] in composition) is the name in the app language.
 */
enum class CaseColour(val label: String, @StringRes val labelRes: Int, val color: Color, private val success: Color? = null) {
    // Light/blue row: Cloud leads into the two blues. `null` (original blue) sits between them in
    // the picker, so it is not an enum entry.
    Cloud("Cloud", R.string.sk_sidekit_colour_cloud, Color(0xFFEDE8E8)),
    Blue("Blue", R.string.sk_sidekit_colour_blue, Color(0xFF82C7F3)),

    // Green row: Lime and Sage pair; Orange is the warm counterpoint. Green accents need a teal
    // commit colour, otherwise Primary and Secondary are two greens.
    Lime("Lime", R.string.sk_sidekit_colour_lime, Color(0xFFA2F49F), success = Color(0xFF0E7070)),
    Sage("Sage", R.string.sk_sidekit_colour_sage, Color(0xFF90C190), success = Color(0xFF0E7070)),
    Orange("Orange", R.string.sk_sidekit_colour_orange, Color(0xFFFF8A00)),

    // Cool row: Lavender and Pink pair.
    Lavender("Lavender", R.string.sk_sidekit_colour_lavender, Color(0xFFAE99D1)),
    Pink("Pink", R.string.sk_sidekit_colour_pink, Color(0xFFF575D1));

    /** The colour's name in the app language. */
    fun label(context: Context): String = context.getString(labelRes)

    /** The colour's name in the app language. */
    @Composable
    @ReadOnlyComposable
    fun displayLabel(): String = stringResource(labelRes)

    fun palette(): SidekitColors {
        val base = SidekitColors()
        return base.copy(
            primaryA1 = color,
            primaryA2 = lerp(color, Color.White, 0.12f),
            primaryA3 = lerp(color, Color.White, 0.24f),
            successA1 = success ?: base.successA1,
            successA2 = success?.let { lerp(it, Color.White, 0.30f) } ?: base.successA2,
            successA3 = success?.let { lerp(it, Color.White, 0.55f) } ?: base.successA3
        )
    }

    companion object {
        /**
         * Resolves a stored name, returning null for an unknown or absent value.
         *
         * Older clients may see a colour added after they shipped; they fall back to the original
         * blue rather than failing.
         */
        fun named(name: String?): CaseColour? = entries.firstOrNull { it.name == name }
    }
}
