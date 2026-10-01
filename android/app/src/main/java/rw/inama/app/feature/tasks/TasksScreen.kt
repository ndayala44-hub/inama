package rw.inama.app.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rw.inama.app.R
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.Urgency
import rw.inama.app.domain.repository.FieldRepository
import rw.inama.app.domain.repository.TaskRepository
import rw.inama.app.domain.usecase.AddAdviceToTasksUseCase
import rw.inama.app.feature.onboarding.fieldColors
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.components.ChipFlow
import rw.inama.app.ui.components.CircleIconButton
import rw.inama.app.ui.components.Divider
import rw.inama.app.ui.components.EmptyState
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.PillChoice
import rw.inama.app.ui.components.ScreenHeader
import rw.inama.app.ui.components.Segmented
import rw.inama.app.ui.components.SectionTitle
import rw.inama.app.ui.components.TaskRow
import rw.inama.app.ui.components.urgencyLabel
import rw.inama.app.ui.theme.Inama

data class TasksUiState(
    val showDone: Boolean = false,
    val tasks: List<FarmTask> = emptyList(),
    val fields: List<Field> = emptyList(),
    val draft: String = "",
    val draftFieldId: String? = null,
) {
    val open: List<FarmTask> get() = tasks.filter { !it.done }
    val done: List<FarmTask> get() = tasks.filter { it.done }.sortedByDescending { it.doneAtMillis ?: it.createdAtMillis }
    /** Open tasks grouped by urgency, most urgent first. */
    val grouped: List<Pair<Urgency, List<FarmTask>>>
        get() = Urgency.entries.map { u -> u to open.filter { it.urgency == u }.sortedBy { it.createdAtMillis } }.filter { it.second.isNotEmpty() }
}

class TasksViewModel(
    private val tasks: TaskRepository,
    fields: FieldRepository,
    private val addTasks: AddAdviceToTasksUseCase,
) : ViewModel() {
    private data class Local(val showDone: Boolean = false, val draft: String = "", val draftFieldId: String? = null)

    private val local = MutableStateFlow(Local())

    val state: StateFlow<TasksUiState> = combine(tasks.tasks, fields.fields, local) { t, f, l ->
        TasksUiState(l.showDone, t, f, l.draft, l.draftFieldId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun showDone(done: Boolean) = local.update { it.copy(showDone = done) }
    fun onDraft(text: String) = local.update { it.copy(draft = text.take(120)) }
    fun onDraftField(id: String?) = local.update { it.copy(draftFieldId = id) }

    fun addDraft() {
        val l = local.value
        if (l.draft.isBlank()) return
        viewModelScope.launch {
            addTasks.manual(l.draft, l.draftFieldId, tasks.tasks.first())
            local.update { it.copy(draft = "") }
        }
    }

    fun setDone(id: String, done: Boolean) {
        viewModelScope.launch { tasks.setDone(id, done) }
    }

    fun delete(id: String) {
        viewModelScope.launch { tasks.delete(id) }
    }
}

@Composable
fun TasksScreen() {
    val container = LocalAppContainer.current
    val vm: TasksViewModel = viewModel { TasksViewModel(container.taskRepository, container.fieldRepository, container.addAdviceToTasks) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    val fieldName = { id: String? -> state.fields.firstOrNull { it.id == id }?.name }

    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenHeader(stringResource(R.string.tasks_title), subtitle = stringResource(R.string.tasks_subtitle, state.open.size))
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Segmented(
                    options = listOf(false to stringResource(R.string.tasks_todo), true to stringResource(R.string.tasks_done)),
                    selected = state.showDone,
                    onSelect = vm::showDone,
                )
            }
            if (!state.showDone) {
                item {
                    InamaCard(padding = 14.dp, spacing = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = state.draft,
                                onValueChange = vm::onDraft,
                                placeholder = { Text(stringResource(R.string.tasks_add_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors(),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { vm.addDraft() }),
                                modifier = Modifier.weight(1f),
                            )
                            CircleIconButton(R.drawable.ic_plus, stringResource(R.string.tasks_add), vm::addDraft, background = c.forest, tint = c.white, bordered = false, size = 52.dp)
                        }
                        if (state.fields.isNotEmpty() && state.draft.isNotBlank()) {
                            ChipFlow {
                                PillChoice(stringResource(R.string.tasks_any_field), state.draftFieldId == null, { vm.onDraftField(null) })
                                state.fields.forEach { f -> PillChoice(f.name, state.draftFieldId == f.id, { vm.onDraftField(f.id) }) }
                            }
                        }
                    }
                }
                if (state.open.isEmpty()) {
                    item { EmptyState(R.drawable.ic_tasks, stringResource(R.string.tasks_empty_title), stringResource(R.string.tasks_empty_body)) }
                }
                state.grouped.forEach { (urgency, list) ->
                    item(key = "h_${urgency.wire}") { SectionTitle(stringResource(urgencyLabel(urgency)) + " · ${list.size}") }
                    item(key = "g_${urgency.wire}") {
                        InamaCard(padding = 8.dp, spacing = 0.dp) {
                            list.forEachIndexed { i, t ->
                                if (i > 0) Divider()
                                TaskRow(t, fieldName(t.fieldId), onToggle = { done -> vm.setDone(t.id, done) })
                            }
                        }
                    }
                }
            } else {
                if (state.done.isEmpty()) {
                    item { EmptyState(R.drawable.ic_check, stringResource(R.string.tasks_done_empty_title), stringResource(R.string.tasks_done_empty_body)) }
                } else {
                    item {
                        InamaCard(padding = 8.dp, spacing = 0.dp) {
                            state.done.forEachIndexed { i, t ->
                                if (i > 0) Divider()
                                TaskRow(
                                    t,
                                    fieldName(t.fieldId),
                                    onToggle = { done -> vm.setDone(t.id, done) },
                                    trailing = { CircleIconButton(R.drawable.ic_trash, stringResource(R.string.action_delete), { vm.delete(t.id) }, bordered = false, size = 44.dp, tint = c.muted) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
