package hrln.interactive.sidekit.apps.integrations

import android.content.Context
import hrln.interactive.sidekit.sdk.crypto.EncryptedFileStore
import org.json.JSONObject

/** Credentials stay in app-private storage, encrypted with a non-exportable Android Keystore key. */
class PrivateSettings(context: Context, name: String) {
    private val store = EncryptedFileStore(context, name)

    fun read(): JSONObject = store.read()

    fun write(value: JSONObject) = store.write(value)

    fun clear() = store.clear()
}
