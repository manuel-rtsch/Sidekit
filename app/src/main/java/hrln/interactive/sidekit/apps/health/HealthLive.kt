package hrln.interactive.sidekit.apps.health

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.maps.OutdoorInfo
import hrln.interactive.sidekit.apps.maps.WhileVisible
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.theme.SidekitTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class HealthViewModel(val repository: HealthRepository) : ViewModel() {
    fun saveGoal(text: String): Boolean {
        val value = text.toIntOrNull()?.takeIf { it in 1..100_000 } ?: return false
        repository.goal(value)
        return true
    }
}

@Composable
fun LiveHealth(page: String, onHistory: () -> Unit, onGoals: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val vm: HealthViewModel = viewModel(factory = viewModelFactory { initializer { HealthViewModel(HealthRepository.get(context)) } })
    val ledger by vm.repository.store.state.collectAsStateWithLifecycle()
    val error by vm.repository.store.error.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(canCount(context)) }
    var message by remember { mutableStateOf(resources.getString(R.string.health_permission_prompt)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it
        if (!it) message = resources.getString(R.string.health_permission_denied)
    }
    val available = remember { context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null }
    WhileVisible(allowed) {
        allowed = canCount(context)
        if (allowed && available) {
            vm.repository.store.state.filterNotNull().first()
            scheduleRollover(context)
            stepReadings(context).collect { recordSteps(context, vm.repository, it) }
        }
    }
    val list = rememberLazyListState()
    var goal by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(ledger?.goal) { if (goal.isEmpty()) goal = ledger?.goal?.toString().orEmpty() }
    val goalInvalid = stringResource(R.string.health_goal_invalid)
    val actions = when (page) {
        "goals" -> listOf(SideAction.cancel("back", onClick = onBack), SideAction.save("save") { if (ledger != null && vm.saveGoal(goal)) onBack() else message = goalInvalid })

        "history" -> listOf(SideAction.back("back", stringResource(R.string.health_back), onBack))

        else -> listOfNotNull(
            SideAction.back("history", stringResource(R.string.health_history), onHistory),
            SideAction.forward("goals", stringResource(R.string.health_goals), onGoals),
            if (!allowed && available) SideAction("allow", stringResource(R.string.health_allow)) { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) } else null
        )
    }
    SidekitScaffold(
        if (page == "home") {
            stringResource(R.string.health_title)
        } else if (page == "goals") {
            stringResource(R.string.health_daily_goal)
        } else {
            stringResource(R.string.health_step_history)
        },
        actions = actions
    ) { padding ->
        SidekitList(padding, state = list) {
            when (page) {
                "goals" -> {
                    item { SidekitTile(Modifier.listFade(list, 0)) { SidekitTextField(goal, { goal = it }, Modifier.fillMaxWidth().padding(SidekitTheme.spacing.itemPadding), placeholder = stringResource(R.string.health_steps_per_day), singleLine = true, dictation = false, focusOnKeypad = true, autoFocus = true, onDone = { if (vm.saveGoal(goal)) onBack() else message = goalInvalid }) } }
                    item { OutdoorInfo(message, modifier = Modifier.listFade(list, 1)) }
                }

                "history" -> {
                    val days = ledger?.let { (it.history + if (it.day.isNotEmpty()) listOf(StepDay(it.day, it.today, it.approximate)) else emptyList()).sortedByDescending { d -> d.date } }.orEmpty()
                    if (days.isEmpty()) item { OutdoorInfo(stringResource(R.string.health_no_samples), modifier = Modifier.listFade(list, 0)) }
                    items(days.size) { i ->
                        val steps = days[i].steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val label = pluralStringResource(if (days[i].approximate) R.plurals.health_day_steps_approx else R.plurals.health_day_steps, steps, steps)
                        OutdoorInfo(days[i].date, secondary = label, modifier = Modifier.listFade(list, i))
                    }
                }

                else -> {
                    item { Box(Modifier.listFade(list, 0)) { StepsCard(StepsToday(if (ledger?.day == LocalDate.now().toString()) ledger!!.today.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0, ledger?.goal ?: 8000)) } }
                    item {
                        OutdoorInfo(
                            if (!available) {
                                stringResource(R.string.health_no_step_counter)
                            } else if (!allowed) {
                                message
                            } else {
                                stringResource(R.string.health_steps_approximate)
                            },
                            secondary = if (allowed && available) stringResource(R.string.health_sampling_note) else null,
                            modifier = Modifier.listFade(list, 1)
                        )
                    }
                    item { OutdoorInfo(stringResource(R.string.health_heart_sleep), secondary = stringResource(R.string.health_no_source), modifier = Modifier.listFade(list, 2)) }
                }
            }
            error?.let { item { OutdoorInfo(it) } }
        }
    }
}
