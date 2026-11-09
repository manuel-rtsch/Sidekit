package hrln.interactive.sidekit.system

import org.junit.Assert.assertFalse
import org.junit.Test

class NativeLibrariesTest {
    @Test
    fun `a missing library reports unavailable instead of throwing`() {
        assertFalse(NativeLibraries.load("sidekit_no_such_library"))
        assertFalse(NativeLibraries.isAvailable("sidekit_no_such_library"))
    }

    @Test
    fun `repeated loads of a missing library stay false`() {
        assertFalse(NativeLibraries.load("sidekit_no_such_library"))
        assertFalse(NativeLibraries.load("sidekit_no_such_library"))
    }
}
