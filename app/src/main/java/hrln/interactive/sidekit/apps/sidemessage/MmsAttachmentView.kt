package hrln.interactive.sidekit.apps.sidemessage

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.components.SidekitText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MmsAttachmentView(attachment: MmsAttachment) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.uri) {
        if (attachment.mime.startsWith("image/")) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(attachment.uri)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 800) sample *= 2
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                }.getOrNull()
            }
        }
    }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val noApp = stringResource(R.string.messages_attachment_no_app)
    val openFailed = stringResource(R.string.messages_attachment_failed)
    Column(
        Modifier.fillMaxWidth().clickable {
            scope.launchIoAndMain({
                val directory = File(context.cacheDir, "mms-attachments").apply { mkdirs() }
                val file = File(directory, Uri.parse(attachment.uri).lastPathSegment.orEmpty() + ".attachment")
                context.contentResolver.openInputStream(Uri.parse(attachment.uri))!!.use { input -> file.outputStream().use { it.write(MmsTransport.readBounded(input, MmsTransport.MAX_BYTES)) } }
                FileProvider.getUriForFile(context, "${context.packageName}.mms", file)
            }, { uri ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (_: Exception) {
                    error = noApp
                }
            }, { error = openFailed })
        }
    ) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.messages_attachment_picture_cd), modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) }
        SidekitText(error ?: stringResource(R.string.messages_attachment_tap, attachment.name), maxLines = 2)
    }
}
private fun <T> kotlinx.coroutines.CoroutineScope.launchIoAndMain(work: () -> T, success: (T) -> Unit, failure: () -> Unit) = launch {
    try {
        success(withContext(Dispatchers.IO) { work() })
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        failure()
    }
}
