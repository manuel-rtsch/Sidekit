package hrln.interactive.sidekit.apps.integrations

import android.content.Context
import androidx.annotation.StringRes
import hrln.interactive.sidekit.R
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

fun interface JsonRequest {
    fun request(url: String, authorization: String?, body: JSONObject?): JSONObject
}

object JsonHttp : JsonRequest {
    override fun request(url: String, authorization: String?, body: JSONObject?): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        require(connection.url.protocol == "https") { "HTTPS required" }
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        authorization?.let { connection.setRequestProperty("Authorization", it) }
        try {
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw when (code) {
                    401, 403 -> ServiceException(R.string.integrations_access_denied, "Access denied. Check your connection credentials.")
                    429 -> ServiceException(R.string.integrations_rate_limited, "Request limit reached. Try again later.")
                    else -> ServiceException(R.string.integrations_unavailable, "Service unavailable (HTTP $code). Try again.", code)
                }
            }
            val bytes = connection.inputStream.use { readLimited(it, 8 * 1024 * 1024) }
            if (bytes.size > 8 * 1024 * 1024) throw ServiceException(R.string.integrations_too_large, "Service response too large")
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * A request a service refused or could not answer. [message] is English, for logs; [message] with a
 * Context is the same text in the app language, for the screen. [code] is the HTTP status, when it
 * is part of the message.
 */
class ServiceException(@StringRes val messageRes: Int, english: String, val code: Int? = null) : IOException(english) {
    fun message(context: Context): String = if (code != null) context.getString(messageRes, code) else context.getString(messageRes)
}

/** What to tell the user about [this] failure: a [ServiceException] in the app language, else the system's own message. */
fun Throwable.userMessage(context: Context): String? = (this as? ServiceException)?.message(context) ?: localizedMessage

fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        if (out.size() + n > limit) throw IOException("Response exceeds size limit")
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
