package hrln.interactive.sidekit.apps.video

import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.media.Access
import hrln.interactive.sidekit.apps.media.PermissionRationale
import hrln.interactive.sidekit.apps.media.formatDuration
import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.PublishChrome
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.sdk.media.Thumbnail
import hrln.interactive.sidekit.ui.SidekitPreviewFrame
import hrln.interactive.sidekit.ui.SidephonePreview
import kotlinx.coroutines.delay

object VideoTags {
    const val LIST = "video_list"
    const val PLAYER = "video_player"
}

private val THUMB_W = 72.dp
private val THUMB_H = 54.dp

/**
 * Video home: the phone's videos, newest first. Each row is the video's poster frame, its title
 * (camera clips by date) and its length, in Sidekit's Item style; tap to play.
 */
@Composable
fun VideoListScreen(
    state: VideoListState,
    onPlay: (Video) -> Unit,
    onBack: () -> Unit,
    access: Access = Access.Granted,
    onAllow: () -> Unit = {}
) {
    if (access != Access.Granted) {
        SidekitScaffold(
            title = stringResource(R.string.video_title),
            actions = listOf(
                SideAction.square("back", SidekitIcon.ArrowLeftAlt, stringResource(R.string.video_home), onBack, motion = ActionMotion.Back),
                if (access == Access.Blocked) {
                    SideAction.forward("allow", stringResource(R.string.video_open_settings), onAllow, icon = SidekitIcon.Settings)
                } else {
                    SideAction("allow", stringResource(R.string.video_allow), SidekitIcon.Check, iconAtEnd = true, tone = ActionTone.Secondary, contentDescription = stringResource(R.string.video_allow_description), onClick = onAllow)
                }
            )
        ) { padding ->
            PermissionRationale(stringResource(R.string.video_rationale_title), stringResource(R.string.video_rationale_body), access, padding)
        }
        return
    }
    SidekitScaffold(title = stringResource(R.string.video_title), actions = listOf(SideAction.back("back", stringResource(R.string.video_home), onBack))) { padding ->
        if (state.loaded && state.videos.isEmpty()) {
            Empty(stringResource(R.string.video_empty), padding)
            return@SidekitScaffold
        }
        val listState = rememberLazyListState()
        val untitled = stringResource(R.string.video_untitled)
        SidekitList(padding, Modifier.testTag(VideoTags.LIST), state = listState) {
            itemsIndexed(state.videos, key = { _, v -> v.id }, contentType = { _, _ -> 0 }) { index, video ->
                SidekitItem(
                    title = video.title.ifBlank { untitled },
                    secondary = formatDuration(video.durationMs),
                    modifier = Modifier.listFade(listState, index),
                    onClick = { onPlay(video) },
                    leading = {
                        Box(Modifier.size(THUMB_W, THUMB_H).clip(SidekitTheme.shapes.control).background(SidekitTheme.colors.surfaceA2)) {
                            Thumbnail(video.uri, THUMB_W, THUMB_H, Modifier.fillMaxSize())
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Empty(text: String, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        SidekitText(text, style = SidekitTheme.typography.label, color = SidekitTheme.colors.surfaceA6, textAlign = TextAlign.Center, maxLines = 3)
    }
}

/** Controls hide after this long without input while playing. */
private const val HIDE_AFTER_MS = 3_000L

/** How often the time and progress line update while visible. */
private const val TICK_MS = 500L

/**
 * Full-screen player. The picture fills the whole design canvas (letterboxed to its aspect); on a
 * taller keypad phone that canvas is taller than the Sidephone's 480 x 640.
 *
 * The bar is "← | ▶ Play / ❚❚ Pause | ⏭ +10 s" with a progress line and times just above it;
 * dragging or tapping the line seeks. While playing, bar and line slide away after 3 s without
 * input; a tap on the picture or any key brings them back. Keypad: OK / 5 play-pause,
 * 4 / 6 (or left / right) skip 10 s back / forward, media keys work too.
 */
@Composable
fun VideoPlayerScreen(player: VideoPlayer, onBack: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    var inputs by remember { mutableIntStateOf(0) }
    var tick by remember { mutableLongStateOf(0L) }
    val focus = remember { FocusRequester() }
    val playing = player.isPlaying
    val showControls = visible || !playing

    fun poke() {
        visible = true
        inputs++
    }

    LaunchedEffect(inputs, playing) {
        if (playing) {
            delay(HIDE_AFTER_MS)
            visible = false
        }
    }
    // The clock only runs while the controls are on screen and the video moves.
    LaunchedEffect(showControls, playing) {
        while (showControls && playing) {
            tick++
            delay(TICK_MS)
        }
        tick++
    }
    // Keep the display on while a video plays, as any player does.
    val view = LocalView.current
    DisposableEffect(playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(SidekitTheme.colors.dark)
            .testTag(VideoTags.PLAYER)
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                poke()
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                        player.togglePlay()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player.pause()
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        player.seekBy(-SEEK_STEP_MS)
                        tick++
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        player.seekBy(SEEK_STEP_MS)
                        tick++
                        true
                    }

                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { if (visible && playing) visible = false else poke() } },
        contentAlignment = Alignment.Center
    ) {
        VideoSurface(player, Modifier.then(if (player.aspect > 0f) Modifier.aspectRatio(player.aspect) else Modifier.fillMaxSize()))
        if (player.failed) {
            SidekitText(stringResource(R.string.video_cant_play), style = SidekitTheme.typography.label, color = SidekitTheme.colors.surfaceA6)
        }
        SeekLine(
            player = player,
            tick = tick,
            visible = showControls,
            onSeek = {
                poke()
                player.seekTo(it)
                tick++
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = SidekitTheme.spacing.pagePadding)
                .padding(bottom = SidekitTheme.spacing.pagePadding * 2 + SidekitTheme.spacing.actionBarHeight)
        )
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val backToVideos = stringResource(R.string.video_back_to_videos)
    val playLabel = stringResource(if (playing) R.string.video_pause else R.string.video_play)
    val playDescription = stringResource(if (playing) R.string.video_pause_description else R.string.video_play_description)
    val forward10 = stringResource(R.string.video_forward_10)
    val back10 = stringResource(R.string.video_back_10)
    val restart = stringResource(R.string.video_restart)
    val actions = if (!showControls) {
        emptyList()
    } else {
        listOf(
            SideAction.square("back", SidekitIcon.ArrowLeftAlt, backToVideos, onBack, motion = ActionMotion.Back),
            SideAction(
                "play",
                playLabel,
                if (playing) SidekitIcon.Pause else SidekitIcon.PlayArrow,
                motion = ActionMotion.Up,
                contentDescription = playDescription
            ) {
                poke()
                player.togglePlay()
            },
            SideAction.square("skip", SidekitIcon.SkipNext, forward10, {
                poke()
                player.seekBy(SEEK_STEP_MS)
                tick++
            }, tone = ActionTone.Muted)
        )
    }
    val options = listOf(
        SideAction("back10", back10, SidekitIcon.SkipPrevious) {
            poke()
            player.seekBy(-SEEK_STEP_MS)
            tick++
        },
        SideAction("restart", restart, SidekitIcon.Undo) {
            poke()
            player.seekTo(0)
            player.play()
        }
    )
    PublishChrome(header = null, actions = actions, options = options)
}

/** The picture: a [SurfaceView], composited by the display hardware rather than drawn by the GPU. */
@Composable
private fun VideoSurface(player: VideoPlayer, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = player.attach(holder)
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                    override fun surfaceDestroyed(holder: SurfaceHolder) = player.attach(null)
                })
            }
        }
    )
}

/**
 * Elapsed time, a progress line you can drag or tap to seek, and the length. Recomposes on each
 * [tick] (twice a second, only while shown and playing): two short labels and one redraw.
 */
@Composable
private fun SeekLine(player: VideoPlayer, tick: Long, visible: Boolean, onSeek: (Long) -> Unit, modifier: Modifier) {
    val colors = SidekitTheme.colors
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val duration = player.durationMs
    val position = if (dragging) (dragFraction * duration).toLong() else player.positionMs()
    val fraction = if (duration > 0) position / duration.toFloat() else 0f
    val seek = stringResource(R.string.video_seek)
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (visible) 1f else 0f
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemContentGap)
    ) {
        SidekitText(formatDuration(position), style = SidekitTheme.typography.label)
        Box(
            Modifier
                .weight(1f)
                .height(36.dp)
                .semantics { contentDescription = seek }
                .then(
                    if (!visible || duration <= 0) {
                        Modifier
                    } else {
                        Modifier
                            .pointerInput(duration) {
                                detectTapGestures { onSeek(((it.x / size.width).coerceIn(0f, 1f) * duration).toLong()) }
                            }
                            .pointerInput(duration) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        dragging = true
                                        dragFraction = (it.x / size.width).coerceIn(0f, 1f)
                                    },
                                    onDragEnd = {
                                        dragging = false
                                        onSeek((dragFraction * duration).toLong())
                                    },
                                    onDragCancel = { dragging = false }
                                ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
                            }
                    }
                )
                .drawBehind {
                    val h = 6.dp.toPx()
                    val top = (size.height - h) / 2
                    val r = CornerRadius(h / 2, h / 2)
                    drawRoundRect(colors.surfaceA4, Offset(0f, top), Size(size.width, h), r)
                    drawRoundRect(colors.light, Offset(0f, top), Size(size.width * fraction.coerceIn(0f, 1f), h), r)
                }
        )
        SidekitText(formatDuration(duration), style = SidekitTheme.typography.label)
    }
}

// ---- Previews ----------------------------------------------------------------------------

object VideoSampleData {
    val videos = listOf(
        Video(1, "17 Jul 2026, 20:34", 42_000),
        Video(2, "17 Jul 2026, 20:42", 7_000),
        Video(3, "Beach day", 185_000),
        Video(4, "20 Jul 2026, 13:23", 63_000),
        Video(5, "Concert", 3_725_000)
    )
}

@SidephonePreview
@Composable
private fun VideoListPreview() = SidekitPreviewFrame { VideoListScreen(VideoListState(VideoSampleData.videos, loaded = true), {}, {}) }

@SidephonePreview
@Composable
private fun VideoLockedPreview() = SidekitPreviewFrame { VideoListScreen(VideoListState(), {}, {}, access = Access.Ask) }
