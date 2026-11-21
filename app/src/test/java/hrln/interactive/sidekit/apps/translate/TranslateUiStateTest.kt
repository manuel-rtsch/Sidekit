package hrln.interactive.sidekit.apps.translate

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslateUiStateTest {
    private val base = TranslateUiState()

    @Test fun startsEmptyWithoutPretendingToTranslate() {
        assertEquals(TranslateLanguage.English, base.source.language)
        assertEquals("", base.source.text)
        assertEquals(TranslateLanguage.Chinese, base.target.language)
        assertEquals("", base.target.text)
        assertNull(base.talking)
    }

    @Test fun micTogglesWithoutInsertingSampleText() {
        val listening = base.toggleMic(TranslateSide.Source)
        assertEquals(TranslateSide.Source, listening.talking)
        assertEquals("", listening.source.text)
        assertNull(listening.toggleMic(TranslateSide.Source).talking)
        assertEquals(TranslateSide.Target, listening.toggleMic(TranslateSide.Target).talking)
    }

    @Test fun languageNamesFollowTheUiLanguage() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals(listOf("English", "Chinese", "Spanish", "French", "German", "Japanese"), TranslateLanguage.entries.map { it.label })
            Locale.setDefault(Locale.FRENCH)
            assertEquals("Espagnol", TranslateLanguage.Spanish.label)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
