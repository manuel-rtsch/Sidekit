package hrln.interactive.sidekit.apps.spotify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.ItemColour
import hrln.interactive.sidekit.design.components.SidekitCard
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme

private enum class SettingsConfirmation { SignOut, ClearHistory }

@Composable
fun SpotifySettingsScreen(
    state: SpotifyUiState,
    onBack: () -> Unit,
    onAudioQuality: () -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
    onSignOut: () -> Unit,
    onNotice: (String) -> Unit,
    onDismissNotice: () -> Unit
) {
    var confirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
    val back = spotifyBack("Back", onBack).first()
    val normal = withNotice(
        state.notice,
        onDismissNotice,
        buildList {
            add(back)
            if (state.signedIn) {
                add(SideAction.delete("signOut", "Sign out", onClick = { confirmation = SettingsConfirmation.SignOut }))
            }
        }
    )
    val actions = when (confirmation) {
        SettingsConfirmation.SignOut -> listOf(
            SideAction.cancel("cancelSignOut", "Cancel") { confirmation = null },
            SideAction.delete("confirmSignOut", "Sign out", onClick = {
                confirmation = null
                onSignOut()
            })
        )

        SettingsConfirmation.ClearHistory -> listOf(
            SideAction.cancel("cancelClear", "Cancel") { confirmation = null },
            SideAction.delete("confirmClear", "Clear", onClick = {
                confirmation = null
                onClearHistory()
            })
        )

        null -> normal
    }
    SidekitScaffold(
        title = "Settings",
        actions = actions,
        options = listOf(
            SideAction("about", "About", SidekitIcon.Docs, onClick = { onNotice("Sidetracks · a Sidekit sub-app") }),
            SideAction("cache", "Clear cache", SidekitIcon.DeleteForever, onClick = onClearCache)
        )
    ) { padding ->
        val listState = rememberLazyListState()
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        SidekitList(padding, state = listState) {
            item(key = "quality") {
                SidekitItem(
                    "Audio quality",
                    Modifier.listFade(listState, 0).focusRequester(first),
                    secondary = state.audioQuality.label,
                    secondaryToEnd = true,
                    showLinkArrow = true,
                    onClick = onAudioQuality
                )
            }
            item(key = "streaming") {
                SidekitCard(
                    Modifier.listFade(listState, 1).fillMaxWidth(),
                    color = SidekitTheme.colors.surfaceA2
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SidekitText("Streaming only", style = SidekitTheme.typography.bodySmall)
                        SidekitText(
                            "Offline downloads are not available in this build",
                            style = SidekitTheme.typography.caption,
                            color = SidekitTheme.colors.surfaceA6,
                            maxLines = 2
                        )
                    }
                }
            }
            item(key = "cache") {
                SidekitItem(
                    "Artwork cache",
                    Modifier.listFade(listState, 2),
                    secondary = "12 MB",
                    secondaryToEnd = true,
                    showLinkArrow = true,
                    onClick = onClearCache
                )
            }
            item(key = "history") {
                SidekitItem(
                    "Clear history",
                    Modifier.listFade(listState, 3),
                    secondary = "${state.history.size} tracks",
                    secondaryToEnd = true,
                    showLinkArrow = true,
                    onClick = { confirmation = SettingsConfirmation.ClearHistory }
                )
            }
            item(key = "account") {
                SidekitItem(
                    "Account",
                    Modifier.listFade(listState, 4),
                    secondary = if (state.signedIn) "Signed in" else "Not signed in",
                    secondaryToEnd = true
                )
            }
        }
    }
}

@Composable
fun SpotifySignInScreen(
    state: SpotifyUiState,
    onBack: () -> Unit,
    onLogIn: () -> Unit,
    onDismissNotice: () -> Unit
) {
    val colors = SidekitTheme.colors
    val actions = withNotice(
        state.notice,
        onDismissNotice,
        listOf(backToLauncher(onBack), SideAction.forward("login", "Log in", onLogIn))
    )
    SidekitScaffold(
        title = "Sign in",
        actions = actions
    ) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item(key = "explain") {
                SidekitCard(Modifier.listFade(listState, 0).fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SidekitText("Sidetracks plays from your Spotify account.", style = SidekitTheme.typography.bodySmall, maxLines = 2)
                        SidekitText(
                            "Your password is entered in the browser, never here.",
                            style = SidekitTheme.typography.caption,
                            color = colors.surfaceA6,
                            maxLines = 2
                        )
                    }
                }
            }
            item(key = "login") {
                SidekitItem(
                    "Log in with Spotify",
                    Modifier.listFade(listState, 1),
                    showLinkArrow = true,
                    onClick = onLogIn
                )
            }
            item(key = "status") {
                SidekitItem(
                    "Status",
                    Modifier.listFade(listState, 2),
                    secondary = when {
                        state.authPending -> "Waiting for browser"
                        state.signedIn -> "Connected"
                        else -> "Waiting"
                    },
                    secondaryToEnd = true,
                    colour = ItemColour.Muted
                )
            }
        }
    }
}

private fun backToLauncher(onBack: () -> Unit) = SideAction.back("back", "Launcher", onBack)
