package hrln.interactive.sidekit.apps.emulator

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmulatorCoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun invalidRomIsRejected() {
        val rom = File(context.cacheDir, "invalid.gba").apply { writeBytes(ByteArray(1024)) }
        assertEquals(0L, EmulatorCore.open(rom.path))
        rom.delete()
    }

    @Test fun gbAndGbcRenderAndRoundTripBatterySaves() {
        for (color in listOf(false, true)) {
            val bytes = ByteArray(32768)
            // Header identification, then a tiny original program enabling cartridge RAM.
            val logo = "CE ED 66 66 CC 0D 00 0B 03 73 00 83 00 0C 00 0D 00 08 11 1F 88 89 00 0E DC CC 6E E6 DD DD D9 99 BB BB 67 63 6E 0E EC CC DD DC 99 9F BB B9 33 3E"
            logo.split(" ").forEachIndexed { i, value -> bytes[0x104 + i] = value.toInt(16).toByte() }
            bytes[0x100] = 0xC3.toByte()
            bytes[0x101] = 0x50
            bytes[0x102] = 1
            bytes[0x143] = if (color) 0xC0.toByte() else 0
            bytes[0x147] = 3
            bytes[0x149] = 2 // MBC1 + battery + 8 KB RAM
            val program = intArrayOf(0x3E, 0x0A, 0xEA, 0, 0, 0x3E, 0x42, 0xEA, 0, 0xA0, 0x18, 0xFE)
            program.forEachIndexed { i, value -> bytes[0x150 + i] = value.toByte() }
            val rom = File(context.cacheDir, if (color) "diagnostic.gbc" else "diagnostic.gb").apply { writeBytes(bytes) }
            var handle = EmulatorCore.open(rom.path)
            assertNotEquals(0L, handle)
            try {
                assertEquals((160 shl 16) or 144, EmulatorCore.dimensions(handle))
                val bitmap = Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888)
                val audio = ShortArray(4096)
                repeat(180) { assertTrue(EmulatorCore.frame(handle, 0, bitmap, audio) > 0) }
                assertEquals(255, bitmap.getPixel(0, 0).ushr(24))
                val save = EmulatorCore.save(handle)
                assertEquals(0x42, save[0].toInt() and 255)
                EmulatorCore.close(handle)
                handle = EmulatorCore.open(rom.path)
                assertTrue(EmulatorCore.restore(handle, save))
                assertArrayEquals(save, EmulatorCore.save(handle))
                bitmap.recycle()
            } finally {
                EmulatorCore.close(handle)
                rom.delete()
            }
        }
    }

    @Test fun gbaRendersAndRespondsToHeldAndReleasedA() {
        val bytes = ByteArray(1024)
        // ARM program: mode 3, read KEYINPUT and paint pixel 0 red (released) or green (A).
        val words = intArrayOf(0xEA00002E.toInt())
        fun word(offset: Int, value: Int) {
            repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
        }
        word(0, words[0])
        bytes[0xB2] = 0x96.toByte()
        val program = intArrayOf(
            0xE3A00301.toInt(), 0xE3A01003.toInt(), 0xE3811B01.toInt(), 0xE1C010B0.toInt(),
            0xE3A02406.toInt(), 0xE2800C01.toInt(), 0xE2800030.toInt(),
            0xE1D010B0.toInt(), 0xE3110001.toInt(), 0x13A0101F, 0x03A01E3E,
            0xE1C210B0.toInt(), 0xEAFFFFF9.toInt()
        )
        program.forEachIndexed { i, value -> word(0xC0 + 4 * i, value) }
        val rom = File(context.cacheDir, "diagnostic.gba").apply { writeBytes(bytes) }
        val handle = EmulatorCore.open(rom.path)
        assertNotEquals(0L, handle)
        try {
            assertEquals((240 shl 16) or 160, EmulatorCore.dimensions(handle))
            val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
            val audio = ShortArray(4096)
            repeat(5) { EmulatorCore.frame(handle, 0, bitmap, audio) }
            val released = bitmap.getPixel(0, 0)
            repeat(5) { EmulatorCore.frame(handle, 1, bitmap, audio) }
            assertNotEquals(released, bitmap.getPixel(0, 0))
            repeat(5) { EmulatorCore.frame(handle, 0, bitmap, audio) }
            assertEquals(released, bitmap.getPixel(0, 0))
            bitmap.recycle()
        } finally {
            EmulatorCore.close(handle)
            rom.delete()
        }
    }
}
