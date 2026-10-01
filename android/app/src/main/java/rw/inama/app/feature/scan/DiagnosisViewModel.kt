package rw.inama.app.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rw.inama.app.domain.model.CaseStatus
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.ExpertCase
import rw.inama.app.domain.model.FeedbackVerdict
import rw.inama.app.domain.repository.DiagnosisRepository
import rw.inama.app.domain.repository.ExpertRepository
import rw.inama.app.domain.repository.FieldRepository
import rw.inama.app.domain.repository.TaskRepository
import rw.inama.app.domain.usecase.AddAdviceToTasksUseCase

data class DiagnosisUiState(
    val loading: Boolean = true,
    val diagnosis: Diagnosis? = null,
    val fieldName: String? = null,
    val inTasks: Boolean = false,
    /** Set after "Add to tasks": how many new tasks were created (0 = they were already there). */
    val addedCount: Int? = null,
    val escalating: Boolean = false,
    val escalateFailed: Boolean = false,
)

class DiagnosisViewModel(
    private val id: String,
    private val diagnoses: DiagnosisRepository,
    fields: FieldRepository,
    private val tasks: TaskRepository,
    private val addToTasks: AddAdviceToTasksUseCase,
    private val expert: ExpertRepository,
) : ViewModel() {

    private data class Local(val addedCount: Int? = null, val escalating: Boolean = false, val escalateFailed: Boolean = false)

    private val local = MutableStateFlow(Local())

    val state: StateFlow<DiagnosisUiState> = combine(diagnoses.diagnoses, fields.fields, tasks.tasks, local) { ds, fs, ts, l ->
        val d = ds.firstOrNull { it.id == id }
        DiagnosisUiState(
            loading = false,
            diagnosis = d,
            fieldName = fs.firstOrNull { it.id == d?.fieldId }?.name,
            inTasks = ts.any { it.sourceId == id },
            addedCount = l.addedCount,
            escalating = l.escalating,
            escalateFailed = l.escalateFailed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosisUiState())

    fun addStepsToTasks() {
        viewModelScope.launch {
            val d = diagnoses.get(id) ?: return@launch
            val added = addToTasks.fromDiagnosis(d, tasks.tasks.first())
            local.update { it.copy(addedCount = added) }
        }
    }

    fun escalate(onCase: (String) -> Unit) {
        val current = state.value.diagnosis ?: return
        val existingCase = current.caseId
        if (existingCase != null) {
            onCase(existingCase)
            return
        }
        if (local.value.escalating) return
        local.update { it.copy(escalating = true, escalateFailed = false) }
        viewModelScope.launch {
            try {
                val case = expert.escalate(current, note = null)
                local.update { it.copy(escalating = false) }
                onCase(case.id)
            } catch (e: Exception) {
                local.update { it.copy(escalating = false, escalateFailed = true) }
            }
        }
    }

    fun feedback(verdict: FeedbackVerdict) {
        viewModelScope.launch { diagnoses.setFeedback(id, verdict) }
    }
}

data class ExpertCaseUiState(val loading: Boolean = true, val case: ExpertCase? = null, val diagnosis: Diagnosis? = null)

/** Follows a case sent to a person; polls until the reply arrives. */
class ExpertCaseViewModel(
    private val caseId: String,
    private val expert: ExpertRepository,
    private val diagnoses: DiagnosisRepository,
    private val pollMillis: Long = 5_000,
) : ViewModel() {
    private val _state = MutableStateFlow(ExpertCaseUiState())
    val state: StateFlow<ExpertCaseUiState> = _state

    init {
        viewModelScope.launch {
            while (isActive) {
                val case = runCatching { expert.get(caseId) }.getOrNull()
                val d = case?.let { diagnoses.get(it.diagnosisId) }
                _state.update { ExpertCaseUiState(loading = false, case = case ?: it.case, diagnosis = d ?: it.diagnosis) }
                if (case == null || case.status == CaseStatus.ANSWERED) break
                delay(pollMillis)
            }
        }
    }
}
