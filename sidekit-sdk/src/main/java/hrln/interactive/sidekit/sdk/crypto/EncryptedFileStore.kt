package hrln.interactive.sidekit.sdk.crypto

import android.content.Context
import androidx.core.util.AtomicFile
import java.io.File
import org.json.JSONObject

/**
 * A JSON document in app-private `noBackupFilesDir`, encrypted with a non-exportable Android
 * Keystore key and replaced atomically so a crash mid-write keeps the previous copy.
 *
 * This is the shared implementation behind Sidekit's private integration settings (mail, bank)
 * and standalone Sidekit apps' credential stores.
 */
class EncryptedFileStore(
    context: Context,
    name: String,
    private val cipher: ByteCipher = KeystoreCipher("sidekit_$name")
) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "$name.sealed"))

    @Synchronized
    fun read(): JSONObject = if (!file.baseFile.exists()) {
        JSONObject()
    } else {
        JSONObject(String(cipher.decrypt(file.readFully()), Charsets.UTF_8))
    }

    @Synchronized
    fun write(value: JSONObject) {
        val bytes = cipher.encrypt(value.toString().toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    @Synchronized
    fun clear() = file.delete()
}
