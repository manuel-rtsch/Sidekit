package hrln.interactive.sidekit.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import hrln.interactive.sidekit.design.components.SidekitIconImage
import hrln.interactive.sidekit.design.components.SidekitTile
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.system.AppIcons

/** Monochrome, so native app icons sit with Sidekit's own white symbols. */
private val Monochrome = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** A native app's own icon, in grey, at any size. */
@Composable
internal fun NativeAppIcon(app: NativeApp, size: Dp = SidekitTheme.spacing.iconSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(null, app.packageName) { value = AppIcons.load(context, app.packageName) }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), app.label, modifier.size(size), colorFilter = Monochrome)
    } else {
        SidekitIconImage(SidekitIcon.DeployedCode, size = size, contentDescription = app.label, modifier = modifier)
    }
}

/** A native app's launcher tile: its own icon, in grey, on a Sidekit tile. */
@Composable
internal fun NativeTile(app: NativeApp, onOpen: (NativeApp) -> Unit, modifier: Modifier = Modifier) {
    SidekitTile(modifier = modifier, onClick = { onOpen(app) }, onClickLabel = app.label) {
        NativeAppIcon(app, size = SidekitTheme.spacing.tileIconSize)
    }
}
