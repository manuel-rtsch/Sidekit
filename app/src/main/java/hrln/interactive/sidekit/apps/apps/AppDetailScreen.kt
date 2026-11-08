package hrln.interactive.sidekit.apps.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import hrln.interactive.sidekit.apps.everyday.CompactRowHeight
import hrln.interactive.sidekit.apps.sideport.registry.PortedApp
import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.ItemColour
import hrln.interactive.sidekit.design.components.LineButtonVariant
import hrln.interactive.sidekit.design.components.SidekitCard
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitLineButton
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme

/**
 * One installed app's page, opened by holding it in the Apps list. It carries the app's identity
 * and everything that can be done with it, so those actions no longer crowd the Options bar above
 * a list where the selected row had no visible state.
 */
@Composable
internal fun AppDetailScreen(
    app: LaunchableApp,
    source: AppsSource,
    ported: PortedApp?,
    pinned: Boolean,
    onBack: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit
) {
    val context = LocalContext.current
    var message by remember(app.key) { mutableStateOf<String?>(null) }
    var version by remember(app.key) { mutableStateOf(appVersion(context, app.component.packageName)) }
    // Coming back from the system uninstaller, the header says whether the app is still there.
    LifecycleResumeEffect(app.key) {
        version = appVersion(context, app.component.packageName)
        onPauseOrDispose {}
    }
    fun perform(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            message = "Couldn't open app: ${e.localizedMessage}"
        }
    }
    val list = rememberLazyListState()
    val actions = listOf(
        SideAction.square("back", SidekitIcon.ArrowLeftAlt, "Back", onBack, tone = ActionTone.Muted, motion = ActionMotion.Back),
        SideAction.forward("open", "Open", onClick = { perform { source.launch(app) } })
    )
    // Like Sideport's hub, the system app page is the one rare action kept in Options.
    val options = if (version != null) listOf(SideAction("info", "App info", SidekitIcon.Settings) { perform { source.info(app) } }) else emptyList()
    SidekitScaffold(app.label, actions = actions, options = options) { padding ->
        SidekitList(padding, state = list) {
            message?.let { text ->
                item(key = "message") {
                    SidekitCard(Modifier.fillMaxWidth(), onClick = { message = null }) {
                        SidekitText(text, style = SidekitTheme.typography.label, maxLines = Int.MAX_VALUE)
                    }
                }
            }
            var index = 0
            val header = index++
            item(key = "header") { AppHeader(app, version, source, Modifier.listFade(list, header)) }
            if (ported != null) {
                val sideport = index++
                item(key = "sideport") {
                    SidekitItem(
                        "In Sideport",
                        Modifier.height(CompactRowHeight).listFade(list, sideport),
                        secondary = "Can't also be added to home",
                        icon = SidekitIcon.DeployedCode,
                        colour = ItemColour.Muted
                    )
                }
            } else {
                val pin = index++
                item(key = "pin") {
                    SidekitLineButton(
                        if (pinned) "Remove from home" else "Add to home",
                        { if (pinned) onUnpin() else onPin() },
                        Modifier.height(CompactRowHeight).listFade(list, pin),
                        variant = LineButtonVariant.Secondary
                    )
                }
            }
            if (version != null) {
                val uninstall = index++
                item(key = "uninstall") {
                    SidekitLineButton(
                        "Uninstall app",
                        {
                            perform {
                                context.startActivity(
                                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}"))
                                        .putExtra(Intent.EXTRA_USER, app.user)
                                )
                            }
                        },
                        Modifier.height(CompactRowHeight).listFade(list, uninstall),
                        variant = LineButtonVariant.Destructive
                    )
                }
            }
        }
    }
}

/** The app's icon, installed version, package and profile: what the page title cannot say. */
@Composable
private fun AppHeader(app: LaunchableApp, version: String?, source: AppsSource, modifier: Modifier = Modifier) {
    val icon = rememberAppIcon(source, app)
    SidekitCard(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemContentGap)
        ) {
            icon?.let {
                Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(56.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (version != null) {
                    SidekitText("Version $version", style = SidekitTheme.typography.body)
                } else {
                    SidekitText("Not installed", style = SidekitTheme.typography.body, color = SidekitTheme.colors.dangerA2)
                }
                SidekitText(
                    app.component.packageName,
                    style = SidekitTheme.typography.tiny,
                    color = SidekitTheme.colors.surfaceA5
                )
                if (app.user != Process.myUserHandle()) {
                    SidekitText("Work profile", style = SidekitTheme.typography.caption, color = SidekitTheme.colors.surfaceA6)
                }
            }
        }
    }
}

/** The installed version, or null when the package manager cannot answer. */
private fun appVersion(context: Context, packageName: String): String? = runCatching {
    context.packageManager.getPackageInfo(packageName, 0).versionName?.takeIf { it.isNotBlank() }
}.getOrNull()
