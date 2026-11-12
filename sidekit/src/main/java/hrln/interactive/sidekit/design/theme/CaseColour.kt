package hrln.interactive.sidekit.design.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The Sidephone case colours offered by Sidekit.
 *
 * These are shared with every Sidekit-native app so Settings renders the same swatches and the
 * design system applies the same palette everywhere.
 */
enum class CaseColour(val label: String, val color: Color, private val success: Color? = null) {
    // Green accents need a teal commit colour, otherwise Primary and Secondary are two greens.
    Lime("Lime", Color(0xFFA2F49F), success = Color(0xFF0E7070)),
    Orange("Orange", Color(0xFFF68A02)),
    Blue("Blue", Color(0xFF82C7F3)),
    Pink("Pink", Color(0xFFF575D1)),
    Sage("Sage", Color(0xFF90C190), success = Color(0xFF0E7070)),
    Lavender("Lavender", Color(0xFFAE99D1));

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
