package hrln.interactive.sidekit.apps.translate

import hrln.interactive.sidekit.R
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class MemoryTranslationStore(var saved: List<Translation> = emptyList()) : TranslationStore {
    override fun read() = saved
    override fun write(history: List<Translation>) {
        saved = history
    }
}

private class FakeTranslator(private val failure: Boolean = false) : Translator {
    override suspend fun translate(text: String, source: TranslateLanguage, target: TranslateLanguage): String {
        if (failure) throw IOException("offline")
        return "$text [${source.code}->${target.code}]"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TranslateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private lateinit var store: MemoryTranslationStore
    private lateinit var repository: TranslateRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = MemoryTranslationStore()
        repository = TranslateRepository(store, scope.backgroundScope, dispatcher) { 42L }
        scope.testScheduler.runCurrent()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `successful translation updates result and persisted history`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        vm.setText("Good morning")
        vm.translate()
        scope.testScheduler.runCurrent()
        assertEquals("Good morning [en->zh-CN]", vm.state.value.target.text)
        assertEquals(1, repository.history.value!!.size)
        assertEquals("Good morning", repository.history.value!!.first().sourceText)
        scope.testScheduler.runCurrent()
        assertEquals(repository.history.value, store.saved)
    }

    @Test fun `offline failure keeps source and explains error`() {
        val vm = TranslateViewModel(repository, FakeTranslator(failure = true))
        vm.setText("Hello")
        vm.translate()
        scope.testScheduler.runCurrent()
        assertEquals("Hello", vm.state.value.source.text)
        assertEquals("", vm.state.value.target.text)
        assertEquals(TranslateNotice(R.string.translate_error_failed), vm.state.value.error)
        assertEquals(false, vm.state.value.translating)
    }

    @Test fun `speech in target language translates back without sample output`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        vm.onHeard(TranslateSide.Target, "你好")
        scope.testScheduler.runCurrent()
        assertEquals("你好", vm.state.value.target.text)
        assertEquals("你好 [zh-CN->en]", vm.state.value.source.text)
        assertEquals(false, vm.state.value.translating)
    }

    @Test fun `swap and history restore preserve language pair`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        vm.setText("One")
        vm.translate()
        scope.testScheduler.runCurrent()
        vm.swapLanguages()
        assertEquals(TranslateLanguage.Chinese, vm.state.value.source.language)
        assertEquals(TranslateLanguage.English, vm.state.value.target.language)
        vm.open(repository.history.value!!.first())
        assertEquals(TranslateLanguage.English, vm.state.value.source.language)
        assertEquals("One", vm.state.value.source.text)
    }

    @Test fun `tapping a language opens the list and choosing one sets that side`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        vm.pickLanguage(TranslateSide.Target)
        assertEquals(TranslateMode.Language, vm.state.value.mode)
        assertEquals(TranslateSide.Target, vm.state.value.picking)
        vm.chooseLanguage(TranslateLanguage.Spanish)
        assertEquals(TranslateMode.Translate, vm.state.value.mode)
        assertEquals(TranslateLanguage.English, vm.state.value.source.language)
        assertEquals(TranslateLanguage.Spanish, vm.state.value.target.language)
        vm.setText("Hello")
        vm.translate()
        scope.testScheduler.runCurrent()
        assertEquals("Hello [en->es]", vm.state.value.target.text)
    }

    @Test fun `any language can be reached directly, not only Chinese`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        TranslateLanguage.entries.filter { it != TranslateLanguage.English }.forEach { language ->
            vm.pickLanguage(TranslateSide.Target)
            vm.chooseLanguage(language)
            assertEquals(language, vm.state.value.target.language)
            assertEquals(TranslateLanguage.English, vm.state.value.source.language)
        }
    }

    @Test fun `choosing the other side's language swaps the pair`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        vm.pickLanguage(TranslateSide.Source)
        vm.chooseLanguage(TranslateLanguage.Chinese)
        assertEquals(TranslateLanguage.Chinese, vm.state.value.source.language)
        assertEquals(TranslateLanguage.English, vm.state.value.target.language)
    }

    @Test fun `changing the target redoes an existing translation`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        vm.setText("Hi")
        vm.translate()
        scope.testScheduler.runCurrent()
        vm.pickLanguage(TranslateSide.Target)
        vm.chooseLanguage(TranslateLanguage.French)
        scope.testScheduler.runCurrent()
        assertEquals("Hi [en->fr]", vm.state.value.target.text)
    }

    @Test fun `a swap from the list moves each text with its language`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        vm.setText("Hello")
        vm.translate()
        scope.testScheduler.runCurrent()
        vm.pickLanguage(TranslateSide.Target)
        vm.chooseLanguage(TranslateLanguage.English)
        scope.testScheduler.runCurrent()
        assertEquals(TranslatePane(TranslateLanguage.Chinese, "Hello [en->zh-CN]"), vm.state.value.source)
        assertEquals(TranslatePane(TranslateLanguage.English, "Hello"), vm.state.value.target)
        assertEquals(TranslateMode.Translate, vm.state.value.mode)
    }

    @Test fun `changing the target mid-translation never shows the old pair's result`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        vm.setText("Hi")
        vm.translate()
        vm.pickLanguage(TranslateSide.Target)
        vm.chooseLanguage(TranslateLanguage.German)
        scope.testScheduler.runCurrent()
        assertEquals("Hi [en->de]", vm.state.value.target.text)
        assertEquals(listOf(TranslateLanguage.German), repository.history.value!!.map { it.target })
    }

    @Test fun `swapping mid-translation drops the old direction's result`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        vm.setText("Hi")
        vm.translate()
        vm.pickLanguage(TranslateSide.Source)
        vm.chooseLanguage(TranslateLanguage.Chinese)
        scope.testScheduler.runCurrent()
        assertEquals(TranslatePane(TranslateLanguage.Chinese, ""), vm.state.value.source)
        assertEquals(TranslatePane(TranslateLanguage.English, "Hi"), vm.state.value.target)
        assertEquals(false, vm.state.value.translating)
    }

    @Test fun `opening the list drops a pending microphone request`() {
        val vm = TranslateViewModel(repository, FakeTranslator())
        vm.onMic(TranslateSide.Source)
        vm.pickLanguage(TranslateSide.Target)
        assertEquals(false, vm.state.value.microphonePermissionNeeded)
        assertNull(vm.state.value.talking)
    }
}
