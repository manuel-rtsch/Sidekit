package hrln.interactive.sidekit.apps.emulator

import android.graphics.Bitmap
import hrln.interactive.sidekit.system.NativeLibraries

internal object EmulatorCore {
    /** False when this build does not package the emulator ABI; callers show a message instead. */
    val available = NativeLibraries.load("sidekit_emulator")

    external fun open(path: String): Long
    external fun dimensions(handle: Long): Int
    external fun frame(handle: Long, keys: Int, bitmap: Bitmap, audio: ShortArray): Int
    external fun save(handle: Long): ByteArray
    external fun restore(handle: Long, bytes: ByteArray): Boolean
    external fun close(handle: Long)
}
