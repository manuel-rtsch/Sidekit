package hrln.interactive.sidekit.sdk

import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.KeypadPreferences
import hrln.interactive.sidekit.design.input.PhysicalKey
import hrln.interactive.sidekit.design.input.T9Mode
import hrln.interactive.sidekit.design.theme.CaseColour
import hrln.interactive.sidekit.sdk.config.SidekitConfig
import hrln.interactive.sidekit.sdk.config.SidekitConfigSource
import hrln.interactive.sidekit.sdk.config.StaticSidekitConfigSource
import hrln.interactive.sidekit.sdk.input.HostKeypadPreferences
import hrln.interactive.sidekit.sdk.theme.HostSidekitThemeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host-backed stores fall back safely when a newer host names something this client doesn't know. */
class SidekitStoresTest {
    private val config = SidekitConfig(
        schema = SidekitContract.SCHEMA_VERSION,
        caseColour = "FutureColour",
        inputMode = "FutureMode",
        keypadModule = "FutureModule",
        customModule = "FutureModule"
    )

    @Test fun unknownNamesUseDefaults() {
        assertNull(config.caseColourOrDefault())
        assertEquals(T9Mode.MultiTap, config.inputModeOrDefault())
        assertNull(config.keypadModuleOrDefault())
        assertNull(config.customModuleOrDefault())
    }

    @Test fun hostThemeFollowsTheHostSnapshot() {
        val source = StaticSidekitConfigSource(SidekitConfig(caseColour = "Lime", compactLayout = true))
        val theme = HostSidekitThemeStore(source).read()
        assertEquals(CaseColour.Lime, theme.caseColour)
        assertTrue(theme.compact)
    }

    @Test fun hostStoresIgnoreCapabilitiesTheHostDidNotAdvertise() {
        val source = StaticSidekitConfigSource(
            SidekitConfig(
                capabilities = emptyList(),
                caseColour = "Lime",
                compactLayout = true,
                keypadModule = KeypadModule.T9.name,
                profiles = mapOf(KeypadModule.T9.name to mapOf(131 to PhysicalKey("Keypad", 131)))
            )
        )

        assertNull(HostSidekitThemeStore(source).read().caseColour)
        assertNull(HostKeypadPreferences(source).module())
        assertTrue(HostKeypadPreferences(source).profiles(KeypadModule.T9).isEmpty())
    }

    @Test fun hostKeypadCachesTheSnapshotUntilTheHostChanges() {
        val source = CountingSource(SidekitConfig(keypadModule = KeypadModule.T9.name))
        val keypad = HostKeypadPreferences(source)

        keypad.module()
        keypad.customModule()
        keypad.profiles(KeypadModule.T9)

        assertEquals(1, source.reads)
    }

    @Test fun hostKeypadReadsTheActiveProfile() {
        val key = PhysicalKey("Keypad", 131)
        val source = StaticSidekitConfigSource(
            SidekitConfig(
                keypadModule = KeypadModule.T9.name,
                customModule = KeypadModule.T9.name,
                profiles = mapOf(KeypadModule.T9.name to mapOf(131 to key))
            )
        )
        val keypad: KeypadPreferences = HostKeypadPreferences(source)
        assertEquals(KeypadModule.T9, keypad.module())
        assertEquals(KeypadModule.T9, keypad.customModule())
        assertEquals(mapOf(131 to key), keypad.profiles(KeypadModule.T9))
    }

    private class CountingSource(private val config: SidekitConfig) : SidekitConfigSource {
        var reads = 0
        override fun read(): SidekitConfig {
            reads++
            return config
        }

        override fun observe(onChange: (SidekitConfig?) -> Unit): () -> Unit = {}
    }
}
