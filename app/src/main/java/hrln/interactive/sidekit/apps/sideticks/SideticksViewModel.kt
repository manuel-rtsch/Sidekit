package hrln.interactive.sidekit.apps.sideticks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hrln.interactive.sidekit.ui.UndoWindow
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

val ReminderDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)

data class SideticksUiState(
    val ticks: List<Tick> = emptyList(),
    val loaded: Boolean = true,
    val showTicked: Boolean = false,
    val draft: String? = null,
    val dueText: String? = null,
    val recentlyDeleted: List<Tick> = emptyList(),
    val error: String? = null,
    val byDue: Boolean = false
) {
    val open: List<Tick> get() = ticks.filterNot { it.ticked }.let { if (byDue) it.sortedBy { t -> t.dueAt ?: Long.MAX_VALUE } else it }
    val ticked: List<Tick> get() = ticks.filter { it.ticked }
}

class SideticksViewModel(private val repository: TicksRepository) : ViewModel() {
    private val ui = MutableStateFlow(SideticksUiState())
    private var undoOrder: List<Int> = emptyList()

    /** The five-second offer; pauses off-screen, commits when the list is left. */
    val undoWindow = UndoWindow(viewModelScope) { ui.update { it.copy(recentlyDeleted = emptyList()) } }
    val state = combine(repository.ticks, ui, repository.error) { ticks, ui, error ->
        ui.copy(ticks = ticks.orEmpty(), loaded = ticks != null, error = ui.error ?: error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SideticksUiState(loaded = false))
    fun toggle(id: Int) = repository.toggle(id)
    fun setShowTicked(show: Boolean) {
        ui.update { it.copy(showTicked = show) }
    }
    fun sort() {
        ui.update { it.copy(byDue = !it.byDue) }
    }
    fun clearTicked() = delete(repository.ticks.value.orEmpty().filter { it.ticked })
    fun delete(ticks: List<Tick>) {
        undoOrder = repository.ticks.value.orEmpty().map { it.id }
        repository.delete(ticks.map { it.id }.toSet())
        ui.update { it.copy(recentlyDeleted = ticks) }
        undoWindow.start()
    }
    fun undoDelete() {
        undoWindow.cancel()
        repository.restore(ui.value.recentlyDeleted, undoOrder)
        ui.update { it.copy(recentlyDeleted = emptyList()) }
    }

    /** Leaving Sideticks finalises the deletion instead of offering Undo when you come back. */
    fun commitUndo() {
        undoWindow.cancel()
        ui.update { it.copy(recentlyDeleted = emptyList()) }
    }
    fun startAdding() {
        ui.update { it.copy(draft = "", dueText = null, error = null) }
    }
    fun updateDraft(text: String) {
        ui.update { if (it.draft == null) it else it.copy(draft = text) }
    }
    fun setDue(text: String?) {
        ui.update { it.copy(dueText = text, error = null) }
    }
    fun cancelAdding() {
        ui.update { it.copy(draft = null, dueText = null, error = null) }
    }
    fun confirmDraft() {
        val draft = ui.value
        val due = try {
            draft.dueText?.let { LocalDateTime.parse(it, ReminderDateFormat).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        } catch (_: Exception) {
            ui.update { it.copy(error = "Use YYYY-MM-DD HH:mm for the due date.") }
            return
        }
        if (due != null && due <= System.currentTimeMillis()) {
            ui.update { it.copy(error = "Choose a future due time.") }
            return
        }
        if (repository.add(draft.draft.orEmpty(), due)) cancelAdding()
    }
}
