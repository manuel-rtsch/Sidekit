package hrln.interactive.sidekit.apps.sideport

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.apps.AppRow
import hrln.interactive.sidekit.apps.apps.AppSearchField
import hrln.interactive.sidekit.apps.apps.AppsRepository
import hrln.interactive.sidekit.apps.apps.LaunchableApp
import hrln.interactive.sidekit.apps.apps.matchesApp
import hrln.interactive.sidekit.apps.everyday.CompactRowHeight
import hrln.interactive.sidekit.apps.sideport.bridge.Bridge
import hrln.interactive.sidekit.apps.sideport.bridge.Conversation
import hrln.interactive.sidekit.apps.sideport.install.ApkInstaller
import hrln.interactive.sidekit.apps.sideport.install.InstallStatus
import hrln.interactive.sidekit.apps.sideport.registry.PortMode
import hrln.interactive.sidekit.apps.sideport.registry.PortedApp
import hrln.interactive.sidekit.apps.sideport.registry.PortedApps
import hrln.interactive.sidekit.apps.sideport.service.SideportService
import hrln.interactive.sidekit.apps.sideport.ui.PortedAppIcon
import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.NoticeKind
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.withNotice
import hrln.interactive.sidekit.design.components.ItemColour
import hrln.interactive.sidekit.design.components.SidekitCard
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.SidekitTextField
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.home.HomePreferences
import hrln.interactive.sidekit.system.SidekitBuild
import kotlinx.coroutines.launch

/**
 * Sideport: Android apps as Sidekit apps. Install an APK (or pick an installed app) and it opens
 * drawn in Sidekit's components by the projection service, with its chats and media also
 * available here directly through the notification and media-session bridge.
 */
fun NavGraphBuilder.sideportGraph(nav: NavHostController) {
    navigation(startDestination = SideportRoutes.HOME, route = SideportRoutes.ROOT) {
        composable(SideportRoutes.HOME) {
            SideportHomeScreen(
                onHome = { nav.popBackStack() },
                onAdd = { nav.navigate(SideportRoutes.ADD) { launchSingleTop = true } },
                onHub = { nav.navigate(SideportRoutes.hub(it)) { launchSingleTop = true } }
            )
        }
        composable(SideportRoutes.ADD) { SideportAddScreen(onDone = { nav.popBackStack() }) }
        composable(SideportRoutes.HUB) { entry ->
            val pkg = entry.arguments?.getString("pkg").orEmpty()
            SideportHubScreen(
                pkg,
                onBack = { nav.popBackStack() },
                onChat = { nav.navigate(SideportRoutes.chat(it)) },
                onPlayer = { nav.navigate(SideportRoutes.player(pkg)) }
            )
        }
        composable(SideportRoutes.CHAT) { entry ->
            SideportChatScreen(Uri.decode(entry.arguments?.getString("key").orEmpty()), onBack = { nav.popBackStack() })
        }
        composable(SideportRoutes.PLAYER) { entry ->
            SideportPlayerScreen(entry.arguments?.getString("pkg").orEmpty(), onBack = { nav.popBackStack() })
        }
    }
}

private fun Context.openSettings(action: String, data: Uri? = null): Boolean = try {
    startActivity(Intent(action).apply { if (data != null) setData(data) }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}

/** Opens a ported app, explaining what's missing if it can't. Returns a message to show, if any. */
fun openPorted(context: Context, app: PortedApp): String? = when (Sideport.open(context, app)) {
    OpenResult.Opened -> null

    OpenResult.NeedsService -> {
        context.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.getString(R.string.sideport_needs_service, app.label)
    }

    OpenResult.NotInstalled -> context.getString(R.string.sideport_not_installed, app.label)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SideportHomeScreen(onHome: () -> Unit, onAdd: () -> Unit, onHub: (String) -> Unit) {
    val context = LocalContext.current
    val apps by PortedApps.get(context).apps.collectAsStateWithLifecycle()
    val install by ApkInstaller.status.collectAsStateWithLifecycle()
    val connected by SideportService.connected.collectAsStateWithLifecycle()
    var bridge by remember { mutableStateOf(Bridge.isEnabled(context)) }
    var projection by remember { mutableStateOf(connected || SideportService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        bridge = Bridge.isEnabled(context)
        projection = SideportService.instance != null || SideportService.isEnabled(context)
        onPauseOrDispose {}
    }
    var message by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    // Progress stays in the list until the install ends; only the outcome is a transient notice,
    // so the notice's auto-dismiss can't reset the installer's status mid-install.
    val installText = installMessage(install)
    val progress = installText.takeIf { install is InstallStatus.Working || install is InstallStatus.Confirming }
    val outcome = installText.takeIf { progress == null }
    val actions = withNotice(
        message ?: outcome,
        {
            message = null
            ApkInstaller.clear()
        },
        listOf(SideAction.back("home", stringResource(R.string.sideport_home), onHome), SideAction.forward("add", stringResource(R.string.sideport_add_app), onAdd)),
        if (message == null && install is InstallStatus.Installed) NoticeKind.Success else NoticeKind.Other
    )
    SidekitScaffold("Sideport", actions = actions) { padding ->
        SidekitList(padding, state = list) {
            var index = 0
            progress?.let { text ->
                val i = index++
                item(key = "install") {
                    SidekitCard(Modifier.fillMaxWidth().listFade(list, i)) {
                        SidekitText(text, style = SidekitTheme.typography.label, maxLines = Int.MAX_VALUE)
                    }
                }
            }
            if (apps.isEmpty()) {
                val i = index++
                item(key = "intro") {
                    SidekitCard(Modifier.fillMaxWidth().listFade(list, i)) {
                        SidekitText(
                            stringResource(R.string.sideport_intro),
                            style = SidekitTheme.typography.label,
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }
            }
            val start = index
            itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                val hubLabel = stringResource(R.string.sideport_app_hub_label, app.label)
                SidekitItem(
                    app.label,
                    Modifier.listFade(list, start + i).combinedClickable(
                        onClick = { message = openPorted(context, app) },
                        onLongClick = { onHub(app.packageName) },
                        onLongClickLabel = hubLabel
                    ),
                    secondary = if (app.mode == PortMode.Original) stringResource(R.string.sideport_original) else null,
                    icon = SidekitIcon.DeployedCode
                )
            }
            index += apps.size
            val setup = index
            item(key = "projection") {
                SidekitItem(
                    stringResource(R.string.sideport_projection),
                    Modifier.listFade(list, setup),
                    colour = ItemColour.Muted,
                    secondary = stringResource(if (projection) R.string.sideport_on else R.string.sideport_turn_on),
                    onClick = { context.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                )
            }
            item(key = "bridge") {
                SidekitItem(
                    stringResource(R.string.sideport_chats_media),
                    Modifier.listFade(list, setup + 1),
                    colour = ItemColour.Muted,
                    secondary = stringResource(if (bridge) R.string.sideport_on else R.string.sideport_turn_on),
                    onClick = { context.openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun installMessage(status: InstallStatus): String? = when (status) {
    InstallStatus.Idle -> null
    is InstallStatus.Working -> status.step
    is InstallStatus.Confirming -> stringResource(R.string.sideport_install_confirm, status.label)
    is InstallStatus.Installed -> stringResource(R.string.sideport_installed, status.label)
    is InstallStatus.Failed -> stringResource(R.string.sideport_install_failed_with, status.message)
}

/**
 * Installed apps that can become Sideport apps: no Sidekit build (Sidekit never ports itself or a
 * sibling), nothing already ported, one row per package, matching [query].
 */
internal fun sideportCandidates(
    installed: List<LaunchableApp>,
    ported: List<PortedApp>,
    query: String
): List<LaunchableApp> {
    val have = ported.map { it.packageName }.toSet()
    return installed
        .filter { !SidekitBuild.isSidekit(it.component.packageName) && it.component.packageName !in have && matchesApp(it.label, query) }
        .distinctBy { it.component.packageName }
}

/** Install an APK, or turn an app that's already installed into a Sideport app. */
@Composable
internal fun SideportAddScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val ported = PortedApps.get(context)
    val repository = remember { AppsRepository(context.applicationContext) }
    val installed by remember { repository.apps }.collectAsStateWithLifecycle(emptyList())
    val portedApps by ported.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch { ApkInstaller.install(context, uri) }
            onDone()
        }
    }
    fun installApk() {
        if (!ApkInstaller.canInstall(context)) {
            context.openSettings(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            message = resources.getString(R.string.sideport_allow_installs)
        } else {
            picker.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "application/octet-stream"))
        }
    }
    val candidates = remember(installed, portedApps, query) { sideportCandidates(installed, portedApps, query) }
    val list = rememberLazyListState()
    val actions = withNotice(
        message,
        { message = null },
        listOf(
            SideAction.back("back", "Sideport", onDone),
            SideAction("install", stringResource(R.string.sideport_install_apk), SidekitIcon.Add, tone = ActionTone.Secondary) { installApk() }
        )
    )
    SidekitScaffold(stringResource(R.string.sideport_add_app), actions = actions) { padding ->
        SidekitList(padding, state = list) {
            item(key = "search") { AppSearchField(query) { query = it } }
            itemsIndexed(candidates, key = { _, a -> a.key }) { i, app ->
                AppRow(app, repository, Modifier.listFade(list, i + 1), onClick = {
                    ported.add(app.component.packageName, app.label)
                    // One package is either direct or Sideport, never both.
                    HomePreferences(context).removeInstalled(app.component.packageName)
                    onDone()
                })
            }
        }
    }
}

/** A removal offered on an app's hub, confirmed in the action bar before it happens. */
internal enum class HubRemoval(@StringRes val verb: Int, @StringRes val confirm: Int) {
    /** Take the app out of Sideport; it stays installed. */
    Remove(R.string.sideport_remove, R.string.sideport_confirm_remove),

    /** Hand the app to Android's uninstaller. */
    Uninstall(R.string.sideport_uninstall, R.string.sideport_confirm_uninstall)
}

/** What a ported app's hub shows. [version] is null while the app isn't installed. */
internal data class SideportHubUiState(
    val app: PortedApp,
    val version: String? = null,
    val chats: List<Conversation> = emptyList(),
    val mediaTitle: String? = null,
    val bridgeOn: Boolean = false,
    val message: String? = null,
    val confirming: HubRemoval? = null
)

/**
 * A ported app's home in Sidekit: what the app is, how it opens, its chats and media, and what can
 * be done with it. Remove and Uninstall live in the body rather than in Options, so they are
 * findable without the keypad; Options keeps only the system app page.
 */
@Composable
internal fun SideportHubScreen(pkg: String, onBack: () -> Unit, onChat: (String) -> Unit, onPlayer: () -> Unit) {
    val context = LocalContext.current
    val ported = PortedApps.get(context)
    val apps by ported.apps.collectAsStateWithLifecycle()
    val app = apps.firstOrNull { it.packageName == pkg }
    if (app == null) {
        SidekitScaffold("Sideport", actions = listOf(SideAction.back("back", stringResource(R.string.sideport_back), onBack))) { padding ->
            SidekitList(padding) { item { SidekitItem(stringResource(R.string.sideport_app_removed)) } }
        }
        return
    }
    val conversations by Bridge.conversations.collectAsStateWithLifecycle()
    val media by Bridge.media.collectAsStateWithLifecycle()
    val bridgeOn by Bridge.connected.collectAsStateWithLifecycle()
    val chats = remember(conversations, pkg) { Bridge.conversationsFor(pkg) }
    var version by remember(pkg) { mutableStateOf(appVersion(context, pkg)) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<HubRemoval?>(null) }
    // Coming back from the system uninstaller, the header says whether the app is still there.
    LifecycleResumeEffect(pkg) {
        version = appVersion(context, pkg)
        onPauseOrDispose {}
    }
    val nowPlaying = stringResource(R.string.sideport_now_playing)
    val mediaTitle = media[pkg]?.let { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: nowPlaying }
    SideportHubContent(
        SideportHubUiState(app, version, chats, mediaTitle, bridgeOn, message, confirming),
        onBack = onBack,
        onOpen = { message = openPorted(context, app) },
        onChat = onChat,
        onPlayer = onPlayer,
        onBridge = { context.openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
        onInfo = { context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$pkg".toUri()) },
        onMode = { ported.setMode(pkg, if (app.mode == PortMode.Projected) PortMode.Original else PortMode.Projected) },
        onDismissMessage = { message = null },
        onAsk = { confirming = it },
        onCancel = { confirming = null },
        onRemove = {
            ported.remove(pkg)
            onBack()
        },
        onUninstall = { context.startActivity(Intent(Intent.ACTION_DELETE, "package:$pkg".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    )
}

/** The app's installed version, or null when it isn't installed (any more). */
private fun appVersion(context: Context, pkg: String): String? = runCatching {
    context.packageManager.getPackageInfo(pkg, 0).versionName?.takeIf { it.isNotBlank() }
}.getOrNull()

/** The hub body, given its state: a header, how the app opens, what it holds, then Remove/Uninstall. */
@Composable
internal fun SideportHubContent(
    state: SideportHubUiState,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onChat: (String) -> Unit,
    onPlayer: () -> Unit,
    onBridge: () -> Unit,
    onInfo: () -> Unit,
    onMode: () -> Unit,
    onDismissMessage: () -> Unit,
    onAsk: (HubRemoval) -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onUninstall: () -> Unit
) {
    val app = state.app
    val list = rememberLazyListState()
    // Back answers a confirmation before it leaves the hub.
    BackHandler(enabled = state.confirming != null, onBack = onCancel)
    val confirming = state.confirming
    val keep = stringResource(R.string.sideport_keep, app.label)
    val verb = confirming?.let { stringResource(it.verb) }
    val confirmDescription = confirming?.let { stringResource(it.confirm, app.label) }
    val actions = confirming?.let { removal ->
        listOf(
            SideAction.cancel(icon = null, motion = ActionMotion.Up, contentDescription = keep, onClick = onCancel),
            SideAction("forward", verb!!, SidekitIcon.DeleteForever, tone = ActionTone.Destructive, weight = 1.2f, motion = ActionMotion.Up, contentDescription = confirmDescription) {
                when (removal) {
                    HubRemoval.Remove -> onRemove()

                    // The uninstaller is another activity: settle the bar first, so returning from it
                    // (uninstalled or cancelled) shows the hub again, not a stale confirmation.
                    HubRemoval.Uninstall -> {
                        onCancel()
                        onUninstall()
                    }
                }
            }
        )
    } ?: listOf(
        SideAction.square("back", SidekitIcon.ArrowLeftAlt, stringResource(R.string.sideport_back), onBack, tone = ActionTone.Muted, motion = ActionMotion.Back),
        SideAction.forward("open", stringResource(R.string.sideport_open), onOpen)
    )
    // Navigation belongs in the list; Options holds only the destructive actions.
    val removeLabel = stringResource(R.string.sideport_remove_from)
    val uninstallLabel = stringResource(R.string.sideport_uninstall_app)
    val options = buildList {
        add(SideAction("remove", removeLabel, SidekitIcon.DeleteForever, tone = ActionTone.Destructive) { onAsk(HubRemoval.Remove) })
        if (state.version != null) add(SideAction("uninstall", uninstallLabel, SidekitIcon.DeleteForever, tone = ActionTone.Destructive) { onAsk(HubRemoval.Uninstall) })
    }
    val bar = if (state.confirming != null) actions else withNotice(state.message, onDismissMessage, actions)
    SidekitScaffold(app.label, actions = bar, options = options) { padding ->
        SidekitList(padding, state = list) {
            var index = 0
            val header = index++
            item(key = "header") { SideportHeader(app, state.version, Modifier.listFade(list, header)) }
            if (state.version != null) {
                val info = index++
                item(key = "info") {
                    SidekitItem(stringResource(R.string.sideport_app_info), Modifier.height(CompactRowHeight).listFade(list, info), showLinkArrow = true, onClick = onInfo)
                }
                val mode = index++
                item(key = "mode") {
                    SidekitItem(
                        stringResource(R.string.sideport_open_as),
                        Modifier.height(CompactRowHeight).listFade(list, mode),
                        secondary = if (app.mode == PortMode.Projected) "Sideport" else stringResource(R.string.sideport_original),
                        onClick = onMode
                    )
                }
            }
            if (state.mediaTitle != null) {
                val mediaRow = index++
                item(key = "media") {
                    SidekitItem(state.mediaTitle, Modifier.listFade(list, mediaRow), icon = SidekitIcon.MusicNote2, colour = ItemColour.Secondary, showLinkArrow = true, onClick = onPlayer)
                }
            }
            val start = index
            itemsIndexed(state.chats, key = { _, c -> c.key }) { i, c ->
                SidekitItem(c.title, Modifier.listFade(list, start + i), secondary = c.messages.lastOrNull()?.text, onClick = { onChat(c.key) })
            }
            index += state.chats.size
            if (!state.bridgeOn) {
                item(key = "bridge") {
                    SidekitCard(Modifier.fillMaxWidth().listFade(list, index), onClick = onBridge) {
                        SidekitText(stringResource(R.string.sideport_bridge_prompt, app.label), style = SidekitTheme.typography.label, maxLines = Int.MAX_VALUE)
                    }
                }
            } else if (state.chats.isEmpty() && state.mediaTitle == null) {
                item(key = "none") { SidekitItem(stringResource(R.string.sideport_no_chats), Modifier.listFade(list, start), colour = ItemColour.Muted) }
            }
        }
    }
}

/**
 * The app itself: its icon, its installed version (or that it is missing) and its package. The
 * screen title above already names the app, so this card carries what the title cannot.
 */
@Composable
private fun SideportHeader(app: PortedApp, version: String?, modifier: Modifier = Modifier) {
    SidekitCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemContentGap)) {
            PortedAppIcon(app, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (version != null) {
                    SidekitText(stringResource(R.string.sideport_version, version), style = SidekitTheme.typography.body)
                } else {
                    SidekitText(stringResource(R.string.sideport_not_installed_short), style = SidekitTheme.typography.body, color = SidekitTheme.colors.dangerA2)
                }
                SidekitText(app.packageName, style = SidekitTheme.typography.tiny, color = SidekitTheme.colors.surfaceA5)
            }
        }
    }
}

/** A chat read from a ported app's notifications, answered through its inline reply. */
@Composable
internal fun SideportChatScreen(key: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val conversations by Bridge.conversations.collectAsStateWithLifecycle()
    val chat: Conversation? = conversations[key]
    var draft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val canReply = chat?.reply != null
    val back = stringResource(R.string.sideport_back)
    val send = stringResource(R.string.sideport_send)
    val openChat = stringResource(R.string.sideport_open_chat)
    val actions = withNotice(
        message,
        { message = null },
        buildList {
            add(SideAction.square("back", SidekitIcon.ArrowLeftAlt, back, onBack, tone = ActionTone.Muted, motion = ActionMotion.Back))
            if (canReply) {
                add(
                    SideAction("send", send, SidekitIcon.ArrowUpward, iconAtEnd = true, tone = ActionTone.Secondary, motion = ActionMotion.Up) {
                        if (draft.isBlank()) return@SideAction
                        if (Bridge.reply(context, key, draft.trim())) draft = "" else message = resources.getString(R.string.sideport_chat_gone)
                    }
                )
            } else {
                chat?.open?.let { open -> add(SideAction.forward("open", openChat, { runCatching { open.send() } })) }
            }
        }
    )
    SidekitScaffold(chat?.title ?: stringResource(R.string.sideport_chat), actions = actions) { padding ->
        SidekitList(padding, state = list) {
            val messages = chat?.messages.orEmpty()
            itemsIndexed(messages) { _, m ->
                SidekitCard(Modifier.fillMaxWidth(), color = if (m.mine) SidekitTheme.colors.emphasisA1 else SidekitTheme.colors.surfaceA3) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        m.sender?.let { SidekitText(it, style = SidekitTheme.typography.caption, color = SidekitTheme.colors.surfaceA6) }
                        SidekitText(m.text, style = SidekitTheme.typography.bodySmall, maxLines = Int.MAX_VALUE)
                    }
                }
            }
            if (canReply) item(key = "draft") { SidekitTextField(draft, { draft = it }, placeholder = stringResource(R.string.sideport_reply), focusOnKeypad = true) }
        }
    }
}

/** Transport controls for a ported app's media session. */
@Composable
internal fun SideportPlayerScreen(pkg: String, onBack: () -> Unit) {
    val media by Bridge.media.collectAsStateWithLifecycle()
    val controller: MediaController? = media[pkg]
    var metadata by remember(controller) { mutableStateOf(controller?.metadata) }
    var playback by remember(controller) { mutableStateOf(controller?.playbackState) }
    DisposableEffect(controller) {
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(m: MediaMetadata?) {
                metadata = m
            }
            override fun onPlaybackStateChanged(s: PlaybackState?) {
                playback = s
            }
        }
        controller?.registerCallback(callback)
        onDispose { controller?.unregisterCallback(callback) }
    }
    val playing = playback?.state == PlaybackState.STATE_PLAYING
    val transport = controller?.transportControls
    val back = stringResource(R.string.sideport_back)
    val previous = stringResource(R.string.sideport_previous)
    val playPause = stringResource(if (playing) R.string.sideport_pause else R.string.sideport_play)
    val next = stringResource(R.string.sideport_next)
    val actions = buildList {
        add(SideAction.square("back", SidekitIcon.ArrowLeftAlt, back, onBack, tone = ActionTone.Muted, motion = ActionMotion.Back))
        if (transport != null) {
            add(SideAction.square("prev", SidekitIcon.SkipPrevious, previous, { transport.skipToPrevious() }, tone = ActionTone.Muted))
            add(
                SideAction(if (playing) "pause" else "play", icon = if (playing) SidekitIcon.Pause else SidekitIcon.PlayArrow, contentDescription = playPause) {
                    if (playing) transport.pause() else transport.play()
                }
            )
            add(SideAction.square("next", SidekitIcon.SkipNext, next, { transport.skipToNext() }, tone = ActionTone.Muted))
        }
    }
    SidekitScaffold(stringResource(R.string.sideport_now_playing), actions = actions) { padding ->
        SidekitList(padding) {
            if (controller == null) {
                item { SidekitItem(stringResource(R.string.sideport_nothing_playing), colour = ItemColour.Muted) }
                return@SidekitList
            }
            item {
                SidekitCard(Modifier.fillMaxWidth().height(200.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SidekitText(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: stringResource(R.string.sideport_unknown), style = SidekitTheme.typography.headline, maxLines = 2)
                        (metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.let {
                            SidekitText(it, style = SidekitTheme.typography.label, color = SidekitTheme.colors.surfaceA6)
                        }
                        metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.let {
                            SidekitText(it, style = SidekitTheme.typography.caption, color = SidekitTheme.colors.surfaceA6)
                        }
                    }
                }
            }
        }
    }
}
