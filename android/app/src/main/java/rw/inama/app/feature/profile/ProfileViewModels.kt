package rw.inama.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rw.inama.app.domain.model.AppSettings
import rw.inama.app.domain.model.District
import rw.inama.app.domain.model.Farmer
import rw.inama.app.domain.model.Session
import rw.inama.app.domain.repository.CatalogRepository
import rw.inama.app.domain.repository.DataResetter
import rw.inama.app.domain.repository.DiagnosisRepository
import rw.inama.app.domain.repository.FieldRepository
import rw.inama.app.domain.repository.ProfileRepository
import rw.inama.app.domain.repository.ServerHealth
import rw.inama.app.domain.repository.ServerRepository
import rw.inama.app.domain.repository.SessionRepository
import rw.inama.app.domain.repository.SettingsRepository
import rw.inama.app.domain.repository.TaskRepository

data class MeUiState(
    val farmer: Farmer? = null,
    val district: District? = null,
    val session: Session? = null,
    val fieldCount: Int = 0,
    val totalAres: Int = 0,
    val checkCount: Int = 0,
    val tasksDone: Int = 0,
)

class MeViewModel(
    profile: ProfileRepository,
    session: SessionRepository,
    fields: FieldRepository,
    diagnoses: DiagnosisRepository,
    tasks: TaskRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val districts = MutableStateFlow<List<District>>(emptyList())

    private data class Counts(val fields: Int, val ares: Int, val checks: Int, val tasksDone: Int)

    private val counts: Flow<Counts> = combine(fields.fields, diagnoses.diagnoses, tasks.tasks) { f, d, t ->
        Counts(f.size, f.sumOf { it.areaAres }, d.size, t.count { it.done })
    }

    val state: StateFlow<MeUiState> = combine(profile.farmer, session.session, counts, districts) { farmer, s, cnt, ds ->
        MeUiState(
            farmer = farmer,
            district = ds.firstOrNull { it.id == farmer?.districtId },
            session = s,
            fieldCount = cnt.fields,
            totalAres = cnt.ares,
            checkCount = cnt.checks,
            tasksDone = cnt.tasksDone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeUiState())

    init {
        viewModelScope.launch { districts.value = runCatching { catalog.districts() }.getOrDefault(emptyList()) }
    }
}

sealed interface ServerCheck {
    data object Idle : ServerCheck
    data object Checking : ServerCheck
    data class Done(val health: ServerHealth) : ServerCheck
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val serverDraft: String = "",
    val serverCheck: ServerCheck = ServerCheck.Idle,
    val resetting: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val server: ServerRepository,
    private val session: SessionRepository,
    private val resetter: DataResetter,
    private val defaultServerUrl: String,
) : ViewModel() {
    private data class Local(val serverDraft: String? = null, val serverCheck: ServerCheck = ServerCheck.Idle, val resetting: Boolean = false)

    private val local = MutableStateFlow(Local())

    val state: StateFlow<SettingsUiState> = combine(settings.settings, local) { s, l ->
        SettingsUiState(s, l.serverDraft ?: (s.serverUrlOverride ?: defaultServerUrl), l.serverCheck, l.resetting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(serverDraft = defaultServerUrl))

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun onServerDraft(url: String) = local.update { it.copy(serverDraft = url.trim(), serverCheck = ServerCheck.Idle) }

    fun testServer() {
        val url = state.value.serverDraft
        local.update { it.copy(serverCheck = ServerCheck.Checking) }
        viewModelScope.launch {
            val health = server.health(url)
            local.update { it.copy(serverCheck = ServerCheck.Done(health)) }
        }
    }

    /** Saves the typed server address; the default address clears the override. */
    fun saveServer() {
        val url = state.value.serverDraft.trimEnd('/')
        update { it.copy(serverUrlOverride = url.takeIf { u -> u.isNotBlank() && u != defaultServerUrl.trimEnd('/') }) }
        local.update { it.copy(serverDraft = null) }
    }

    fun resetServer() {
        update { it.copy(serverUrlOverride = null) }
        local.update { it.copy(serverDraft = null, serverCheck = ServerCheck.Idle) }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            session.signOut()
            settings.update { it.copy(onboardingComplete = false) }
            onDone()
        }
    }

    fun resetAll(onDone: () -> Unit) {
        if (local.value.resetting) return
        local.update { it.copy(resetting = true) }
        viewModelScope.launch {
            resetter.resetAll()
            local.update { Local() }
            onDone()
        }
    }
}
