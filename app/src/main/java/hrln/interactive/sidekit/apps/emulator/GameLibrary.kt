package hrln.interactive.sidekit.apps.emulator

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

internal class GameLibrary(private val context: Context) {
    private val root = File(context.filesDir, "emulator").apply { mkdirs() }
    fun games(): List<File> = root.listFiles().orEmpty().filter { it.extension in extensions }.sortedBy { title(it) }
    fun title(file: File): String = File(root, "${file.nameWithoutExtension}.title").takeIf { it.exists() }?.readText() ?: file.name
    fun import(uri: Uri): File {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: error("This file has no name.")
        val extension = name.substringAfterLast('.', "").lowercase()
        require(extension in extensions) { "Choose an uncompressed .gb, .gbc or .gba ROM." }
        val temp = File.createTempFile("import-", ".tmp", root)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 32 * 1024 * 1024) { "ROMs must be 32 MB or smaller." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    require(total > 0) { "The ROM is empty." }
                }
            } ?: error("Cannot read this file.")
            val id = digest.digest().joinToString("") { "%02x".format(it) }
            val target = File(root, "$id.$extension")
            if (!target.exists()) check(temp.renameTo(target)) { "Could not store ROM." }
            File(root, "$id.title").writeText(name.substringBeforeLast('.'))
            return target
        } finally {
            temp.delete()
        }
    }
    fun saveFile(game: File) = AtomicFile(File(root, "${game.nameWithoutExtension}.sav"))
    companion object {
        val extensions = setOf("gb", "gbc", "gba")
    }
}
