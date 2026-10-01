package rw.inama.app.feature.farm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.KnowledgeBase
import rw.inama.app.domain.model.LandType
import rw.inama.app.domain.repository.CatalogRepository
import rw.inama.app.domain.repository.DiagnosisRepository
import rw.inama.app.domain.repository.FieldRepository
import rw.inama.app.domain.repository.SettingsRepository
import rw.inama.app.domain.repository.TaskRepository
import java.util.UUID

data class FarmUiState(
    val loading: Boolean = true,
    val kb: KnowledgeBase? = null,
    val fields: List<Field> = emptyList(),
    val diagnoses: List<Diagnosis> = emptyList(),
    val tasks: List<FarmTask> = emptyList(),
    val todayEpochDay: Long = FarmClock.todayEpochDay(),
) {
    val totalAres: Int get() = fields.sumOf { it.areaAres }
}

private fun farmState(
    scope: CoroutineScope,
    fields: FieldRepository,
    diagnoses: DiagnosisRepository,
    tasks: TaskRepository,
    kb: StateFlow<KnowledgeBase?>,
): StateFlow<FarmUiState> = combine(fields.fields, diagnoses.diagnoses, tasks.tasks, kb) { f, d, t, k ->
    FarmUiState(loading = k == null, kb = k, fields = f.sortedBy { it.createdAtMillis }, diagnoses = d, tasks = t, todayEpochDay = FarmClock.todayEpochDay())
}.stateIn(scope, SharingStarted.WhileSubscribed(5_000), FarmUiState())

class FarmViewModel(
    fields: FieldRepository,
    diagnoses: DiagnosisRepository,
    tasks: TaskRepository,
    catalog: CatalogRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val kb = MutableStateFlow<KnowledgeBase?>(null)
    val state: StateFlow<FarmUiState> = farmState(viewModelScope, fields, diagnoses, tasks, kb)

    init {
        viewModelScope.launch { kb.value = catalog.knowledgeBase(settings.current().language.tag) }
    }
}

class FieldDetailViewModel(
    val fieldId: String,
    private val fields: FieldRepository,
    diagnoses: DiagnosisRepository,
    private val tasks: TaskRepository,
    catalog: CatalogRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val kb = MutableStateFlow<KnowledgeBase?>(null)
    val state: StateFlow<FarmUiState> = farmState(viewModelScope, fields, diagnoses, tasks, kb)

    init {
        viewModelScope.launch { kb.value = catalog.knowledgeBase(settings.current().language.tag) }
    }

    fun setTaskDone(taskId: String, done: Boolean) {
        viewModelScope.launch { tasks.setDone(taskId, done) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            fields.delete(fieldId)
            onDeleted()
        }
    }
}

// ------------------------------------------------------------------------------- editor

data class FieldEditorState(
    val loading: Boolean = true,
    val kb: KnowledgeBase? = null,
    val isNew: Boolean = true,
    val name: String = "",
    val cropId: String? = null,
    val areaAres: Int = 10,
    val landType: LandType = LandType.HILLSIDE,
    /** null = not planted yet. */
    val plantedOnEpochDay: Long? = null,
    val saving: Boolean = false,
    val showErrors: Boolean = false,
) {
    val valid: Boolean get() = name.isNotBlank() && cropId != null && areaAres > 0
}

class FieldEditorViewModel(
    private val fieldId: String?,
    private val fields: FieldRepository,
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _state = MutableStateFlow(FieldEditorState(isNew = fieldId == null))
    val state: StateFlow<FieldEditorState> = _state.asStateFlow()
    private var original: Field? = null

    init {
        viewModelScope.launch {
            val kb = catalog.knowledgeBase(settings.current().language.tag)
            val existing = fieldId?.let { fields.get(it) }
            original = existing
            _state.update {
                if (existing == null) it.copy(loading = false, kb = kb)
                else it.copy(
                    loading = false, kb = kb, name = existing.name, cropId = existing.cropId, areaAres = existing.areaAres,
                    landType = existing.landType, plantedOnEpochDay = existing.plantedOnEpochDay,
                )
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v.take(40)) }
    fun onCrop(id: String) = _state.update { it.copy(cropId = id) }
    fun onLand(t: LandType) = _state.update { it.copy(landType = t) }
    fun setArea(ares: Int) = _state.update { it.copy(areaAres = ares.coerceIn(1, 1000)) }
    fun changeArea(delta: Int) = setArea(_state.value.areaAres + delta)

    fun setPlanted(planted: Boolean) = _state.update {
        it.copy(plantedOnEpochDay = if (planted) (it.plantedOnEpochDay ?: FarmClock.todayEpochDay()) else null)
    }

    /** Moves the planting date by whole weeks; never in the future, at most ~2 years back. */
    fun shiftPlanted(days: Int) = _state.update {
        val today = FarmClock.todayEpochDay()
        val current = it.plantedOnEpochDay ?: today
        it.copy(plantedOnEpochDay = (current + days).coerceIn(today - 730, today))
    }

    fun save(onSaved: () -> Unit) {
        val st = _state.value
        if (!st.valid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        if (st.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val field = Field(
                id = original?.id ?: "field_${UUID.randomUUID()}",
                name = st.name.trim(),
                areaAres = st.areaAres,
                cropId = st.cropId!!,
                plantedOnEpochDay = st.plantedOnEpochDay,
                landType = st.landType,
                createdAtMillis = original?.createdAtMillis ?: clock(),
            )
            fields.upsert(field)
            _state.update { it.copy(saving = false) }
            onSaved()
        }
    }
}
