package hrln.interactive.sidekit.sdk.settings

import android.content.res.Resources
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hrln.interactive.sidekit.design.R as DesignR
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.input.KeyMapper
import hrln.interactive.sidekit.design.input.KeypadControl
import hrln.interactive.sidekit.design.input.KeypadControls
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.LocalKeypad
import hrln.interactive.sidekit.design.input.PhysicalKey
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.sdk.R
import kotlinx.coroutines.delay

/**
 * The mapping editor. Automatic (the default) leaves keys alone and detects the module; Custom
 * picks a module, pauses detection, and lets every control of that module be moved to another key.
 *
 * This is shared by Sidekit Settings and standalone Sidekit apps. The caller owns navigation:
 * [learn] opens the capture screen for a control and [tester] opens the key tester.
 */
@Composable
fun KeypadMappingScreen(back: () -> Unit, learn: (KeypadModule, KeypadControl) -> Unit, tester: () -> Unit) {
    val keypad = LocalKeypad.current
    val resources = LocalContext.current.resources
    val mapper = keypad?.mapper
    val custom = mapper?.customModule
    val bindings = custom?.let { mapper.bindings(it) }.orEmpty()
    val list = rememberLazyListState()
    fun toggle() {
        keypad?.useCustomMapping(if (custom == null) keypad.module else null)
    }
    fun nextLayout() {
        custom?.let { keypad.useCustomMapping(KeypadModule.entries[(it.ordinal + 1) % KeypadModule.entries.size]) }
    }
    // Reset is a bar action only when there is something to reset, so it never sits around as a
    // dead option. Switching back to automatic stays in Options: it is a mode change, not a reset.
    val resetKeys = stringResource(R.string.sk_sdk_mapping_reset)
    val useAutomatic = stringResource(R.string.sk_sdk_mapping_use_automatic)
    val settings = stringResource(R.string.sk_sdk_mapping_back)
    val actions = buildList {
        add(SideAction.back("back", settings, back))
        if (custom != null && bindings.isNotEmpty()) {
            add(SideAction("reset", resetKeys, SidekitIcon.Undo, tone = ActionTone.Destructive) { mapper.resetAll(custom) })
        }
    }
    val options = if (custom != null) {
        listOf(SideAction("automatic", useAutomatic, tone = ActionTone.Muted) { keypad.useCustomMapping(null) })
    } else {
        emptyList()
    }
    SidekitScaffold(stringResource(R.string.sk_sdk_mapping_title), actions = actions, options = options) { padding ->
        SidekitList(padding, state = list) {
            item {
                SidekitCompactItem(
                    stringResource(R.string.sk_sdk_mapping_item),
                    Modifier.listFade(list, 0),
                    secondary = stringResource(if (custom == null) R.string.sk_sdk_mapping_automatic else R.string.sk_sdk_mapping_custom),
                    onClick = ::toggle
                )
            }
            if (custom != null) item { SidekitCompactItem(stringResource(R.string.sk_sdk_mapping_layout), Modifier.listFade(list, 1), secondary = custom.displayLabel(), onClick = ::nextLayout) }
            item { SidekitCompactItem(stringResource(R.string.sk_sdk_mapping_tester), Modifier.listFade(list, 2), showLinkArrow = true, onClick = tester) }
            item {
                SidekitText(
                    stringResource(if (custom == null) R.string.sk_sdk_mapping_automatic_note else R.string.sk_sdk_mapping_custom_note),
                    style = SidekitTheme.typography.label,
                    color = SidekitTheme.colors.surfaceA6,
                    maxLines = Int.MAX_VALUE
                )
            }
            if (custom != null) {
                itemsIndexed(KeypadControls.of(custom, resources), key = { _, c -> c.keyCode }) { index, control ->
                    val bound = bindings[control.keyCode]
                    SidekitCompactItem(
                        control.label,
                        Modifier.listFade(list, index + 4),
                        // Show what the key does while it is still on its default binding. The
                        // Sundial's right edge, for example, is media-next on a tap and right on
                        // a hold; "Default" alone hid that distinction.
                        secondary = bound?.label(resources) ?: control.hint,
                        // Moved keys stand out; the rest read as defaults.
                        icon = if (bound != null) SidekitIcon.Edit else null,
                        onClick = { learn(custom, control) }
                    )
                }
            }
        }
    }
}

/** Where a key capture is. */
sealed interface CaptureStep {
    /** Listening for a key; gives up after [secondsLeft]. */
    data class Waiting(val secondsLeft: Int) : CaptureStep

    /** Heard [key]. [movesFrom] is the control it is bound to now, if any; [guarded] means it is the Back or Options key. */
    data class Heard(val key: PhysicalKey, val movesFrom: KeypadControl?, val guarded: Boolean) : CaptureStep
}

/**
 * Learns the key for one control. Waiting, every press is captured (none of them act, so a key
 * can be learned whatever it does now); the wait gives up on its own, and touch Cancel or the
 * system Back always leave. Once a key is heard, pressing it again saves it, and every other key
 * works as usual again: Back cancels, the D-pad reaches Save / Try again. Back and Options can
 * only be moved with Save, so pressing them to escape never maps them by accident.
 */
@Composable
fun KeyCaptureScreen(module: KeypadModule, control: KeypadControl, done: () -> Unit) {
    val mapper = LocalKeypad.current?.mapper
    val resources = LocalContext.current.resources
    var attempt by remember { mutableIntStateOf(0) }
    var step by remember { mutableStateOf<CaptureStep>(CaptureStep.Waiting(CAPTURE_SECONDS)) }
    val latestDone by rememberUpdatedState(done)
    fun save(key: PhysicalKey) {
        mapper?.bind(module, control.keyCode, key)
        latestDone()
    }
    if (mapper != null) {
        DisposableEffect(mapper, module, control.keyCode) {
            val stop = mapper.capture { key, event ->
                when (val now = step) {
                    is CaptureStep.Waiting -> {
                        // The keypad service turns the Sidephone's top-left key into the system's Back:
                        // that's the way out, not a key to learn.
                        if (event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD && event.keyCode == KeyEvent.KEYCODE_BACK) {
                            latestDone()
                        } else {
                            step = heard(mapper, module, control, key, resources)
                        }
                        true
                    }

                    is CaptureStep.Heard -> if (key == now.key && !now.guarded) {
                        save(key)
                        true
                    } else {
                        false
                    }
                }
            }
            onDispose { stop() }
        }
    }
    val waiting = step as? CaptureStep.Waiting
    LaunchedEffect(attempt, waiting != null) {
        if (waiting == null) return@LaunchedEffect
        for (left in CAPTURE_SECONDS - 1 downTo 0) {
            delay(1_000)
            step = CaptureStep.Waiting(left)
        }
        latestDone()
    }
    KeyCaptureContent(
        module,
        control,
        step,
        cancel = done,
        retry = {
            attempt++
            step = CaptureStep.Waiting(CAPTURE_SECONDS)
        },
        save = { (step as? CaptureStep.Heard)?.let { save(it.key) } }
    )
}

private fun heard(mapper: KeyMapper, module: KeypadModule, control: KeypadControl, key: PhysicalKey, resources: Resources): CaptureStep.Heard {
    val now = mapper.controlFor(module, key) ?: key.keyCode
    val movesFrom = KeypadControls.find(module, now, resources)?.takeIf { it.keyCode != control.keyCode }
    val guarded = now == KeyEvent.KEYCODE_BACK || now == KeyEvent.KEYCODE_ENDCALL || now == KeyEvent.KEYCODE_MENU
    return CaptureStep.Heard(key, movesFrom, guarded && control.keyCode != now)
}

@Composable
fun KeyCaptureContent(module: KeypadModule, control: KeypadControl, step: CaptureStep, cancel: () -> Unit, retry: () -> Unit, save: () -> Unit) {
    val heard = step as? CaptureStep.Heard
    val resources = LocalContext.current.resources
    val actions = if (heard == null) {
        listOf(SideAction.cancel("cancel", onClick = cancel))
    } else {
        listOf(
            SideAction("retry", stringResource(R.string.sk_sdk_capture_try_again), SidekitIcon.Undo, tone = ActionTone.Muted, onClick = retry),
            SideAction("save", stringResource(DesignR.string.sk_sidekit_action_save), SidekitIcon.Check, tone = ActionTone.Secondary, onClick = save)
        )
    }
    if (heard != null) BackHandler(onBack = cancel)
    SidekitScaffold(stringResource(if (heard == null) R.string.sk_sdk_capture_title else R.string.sk_sdk_capture_title_again), actions = actions) { padding ->
        SidekitList(padding) {
            item {
                SidekitCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemContentGap)) {
                        SidekitText("${module.displayLabel()} · ${control.label}", style = SidekitTheme.typography.headline)
                        SidekitText(control.hint, style = SidekitTheme.typography.label, color = SidekitTheme.colors.surfaceA6, maxLines = Int.MAX_VALUE)
                    }
                }
            }
            when (step) {
                is CaptureStep.Waiting -> {
                    item {
                        SidekitCompactItem(stringResource(R.string.sk_sdk_capture_listening), secondary = stringResource(R.string.sk_sdk_capture_seconds, step.secondsLeft), colour = ItemColour.Muted)
                    }
                    item {
                        SidekitText(
                            stringResource(R.string.sk_sdk_capture_prompt, control.label),
                            style = SidekitTheme.typography.label,
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }

                is CaptureStep.Heard -> {
                    item {
                        SidekitCompactItem(step.key.label(resources), secondary = step.key.device.ifEmpty { stringResource(R.string.sk_sdk_keypad) }, icon = SidekitIcon.Check)
                    }
                    item {
                        val text = when {
                            step.guarded -> stringResource(
                                R.string.sk_sdk_capture_guarded,
                                step.movesFrom?.label ?: stringResource(DesignR.string.sk_sidekit_key_options),
                                control.label
                            )

                            step.movesFrom != null -> stringResource(R.string.sk_sdk_capture_moves, step.movesFrom.label)

                            else -> stringResource(R.string.sk_sdk_capture_again)
                        }
                        SidekitText(text, style = SidekitTheme.typography.label, maxLines = Int.MAX_VALUE)
                    }
                }
            }
        }
    }
}

/** One press seen by the key tester; [mappedTo] is the control it became, if the mapping changed it. */
data class TestedKey(val key: PhysicalKey, val scanCode: Int, val mappedTo: String?)

/** Shows what each key press sends and what SideKit makes of it: for setting up a new keypad. */
@Composable
fun KeyTesterScreen(back: () -> Unit) {
    val keypad = LocalKeypad.current
    val resources = LocalContext.current.resources
    val presses = remember { mutableStateListOf<TestedKey>() }
    if (keypad != null) {
        DisposableEffect(keypad) {
            val stop = keypad.mapper.watch { key, out ->
                val mappedTo = if (out.keyCode == key.keyCode) {
                    null
                } else {
                    KeypadControls.find(keypad.module, out.keyCode, resources)?.label ?: PhysicalKey.keyName(out.keyCode, resources)
                }
                presses.add(0, TestedKey(key, out.scanCode, mappedTo))
                if (presses.size > MAX_TESTED) presses.removeAt(presses.lastIndex)
            }
            onDispose { stop() }
        }
    }
    KeyTesterContent(keypad?.module, presses, back)
}

@Composable
fun KeyTesterContent(module: KeypadModule?, presses: List<TestedKey>, back: () -> Unit) {
    val list = rememberLazyListState()
    val resources = LocalContext.current.resources
    val title = stringResource(R.string.sk_sdk_mapping_tester)
    SidekitScaffold(title, actions = listOf(SideAction.back("back", stringResource(R.string.sk_sdk_mapping_item), back))) { padding ->
        SidekitList(padding, state = list) {
            item {
                SidekitText(
                    stringResource(R.string.sk_sdk_tester_intro, module?.displayLabel() ?: "Sidekit"),
                    style = SidekitTheme.typography.label,
                    color = SidekitTheme.colors.surfaceA6,
                    maxLines = Int.MAX_VALUE
                )
            }
            items(presses.size) { index ->
                val press = presses[index]
                SidekitCard(Modifier.fillMaxWidth().listFade(list, index + 1)) {
                    Column {
                        val name = press.key.label(resources)
                        SidekitText(press.mappedTo?.let { "$name → $it" } ?: name, style = SidekitTheme.typography.body)
                        val device = press.key.device.ifEmpty { stringResource(R.string.sk_sdk_keypad) }
                        val detail = if (press.scanCode != 0) {
                            stringResource(R.string.sk_sdk_tester_detail_scan, device, press.key.keyCode, press.scanCode)
                        } else {
                            stringResource(R.string.sk_sdk_tester_detail, device, press.key.keyCode)
                        }
                        SidekitText(detail, style = SidekitTheme.typography.label, color = SidekitTheme.colors.surfaceA6)
                    }
                }
            }
        }
    }
}

private const val CAPTURE_SECONDS = 10
private const val MAX_TESTED = 8
