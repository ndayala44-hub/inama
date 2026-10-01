package rw.inama.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rw.inama.app.domain.farm.Priority
import rw.inama.app.domain.farm.PriorityPicker
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.District
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.Farmer
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.KnowledgeBase
import rw.inama.app.domain.model.WeatherReport
import rw.inama.app.domain.repository.CatalogRepository
import rw.inama.app.domain.repository.DiagnosisRepository
import rw.inama.app.domain.repository.FieldRepository
import rw.inama.app.domain.repository.ProfileRepository
import rw.inama.app.domain.repository.SettingsRepository
import rw.inama.app.domain.repository.TaskRepository
import rw.inama.app.domain.repository.WeatherRepository
import rw.inama.app.feature.farm.FarmClock

data class HomeState(
    val farmer: Farmer? = null,
    val district: District? = null,
    val kb: KnowledgeBase? = null,
    val fields: List<Field> = emptyList(),
    val diagnoses: List<Diagnosis> = emptyList(),
    val tasks: List<FarmTask> = emptyList(),
    val weather: WeatherReport? = null,
    val priority: Priority? = null,
    val online: Boolean = true,
    val todayEpochDay: Long = FarmClock.todayEpochDay(),
) {
    val openTasks: List<FarmTask> get() = tasks.filter { !it.done }.sortedBy { it.urgency.ordinal }.take(3)
}

class HomeViewModel(
    profile: ProfileRepository,
    fields: FieldRepository,
    diagnoses: DiagnosisRepository,
    tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val catalog: CatalogRepository,
    private val weather: WeatherRepository,
    online: StateFlow<Boolean>,
) : ViewModel() {

    private data class Extras(val kb: KnowledgeBase? = null, val weather: WeatherReport? = null, val district: District? = null)

    private val extras = MutableStateFlow(Extras())

    private val base = combine(profile.farmer, fields.fields, diagnoses.diagnoses, tasks.tasks, online) { f, fl, d, t, on ->
        HomeState(farmer = f, fields = fl, diagnoses = d, tasks = t, online = on)
    }

    val state: StateFlow<HomeState> = combine(base, extras) { b, e ->
        val today = FarmClock.todayEpochDay()
        val priority = e.kb?.let { PriorityPicker.pick(it, b.fields, b.diagnoses, b.tasks, e.weather, today, System.currentTimeMillis()) }
        b.copy(kb = e.kb, weather = e.weather, district = e.district, priority = priority, todayEpochDay = today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        viewModelScope.launch {
            val lang = settings.current().language.tag
            val kb = catalog.knowledgeBase(lang)
            extras.update { it.copy(kb = kb) }
            val districtId = profile.current()?.districtId
            val district = catalog.districts().firstOrNull { it.id == districtId }
            extras.update { it.copy(district = district) }
            refreshWeather(false)
        }
        viewModelScope.launch {
            // Refresh when the phone comes back online.
            online.collect { isOnline -> if (isOnline && extras.value.kb != null) refreshWeather(false) }
        }
    }

    fun refreshWeather(force: Boolean) {
        viewModelScope.launch {
            val report = runCatching { weather.forecast(extras.value.district, settings.current().language.tag, force) }.getOrNull()
            if (report != null) extras.update { it.copy(weather = report) }
        }
    }
}
