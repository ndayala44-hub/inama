package rw.inama.app.feature.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rw.inama.app.R
import rw.inama.app.domain.model.District
import rw.inama.app.domain.model.WeatherReport
import rw.inama.app.domain.model.WeatherSource
import rw.inama.app.domain.repository.CatalogRepository
import rw.inama.app.domain.repository.ProfileRepository
import rw.inama.app.domain.repository.SettingsRepository
import rw.inama.app.domain.repository.WeatherRepository
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.StatusBarIcons
import rw.inama.app.ui.components.Banner
import rw.inama.app.ui.components.BannerKind
import rw.inama.app.ui.components.CircleIconButton
import rw.inama.app.ui.components.Divider
import rw.inama.app.ui.components.ErrorState
import rw.inama.app.ui.components.IconTile
import rw.inama.app.ui.components.Illustration
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.InamaIcon
import rw.inama.app.ui.components.ListenButton
import rw.inama.app.ui.components.LoadingState
import rw.inama.app.ui.components.OfflineBanner
import rw.inama.app.ui.components.SectionTitle
import rw.inama.app.ui.components.VerdictChip
import rw.inama.app.ui.components.activityIcon
import rw.inama.app.ui.components.activityLabel
import rw.inama.app.ui.components.conditionIcon
import rw.inama.app.ui.components.conditionLabel
import rw.inama.app.ui.components.dayLabel
import rw.inama.app.ui.components.decisionReason
import rw.inama.app.ui.components.relativeTime
import rw.inama.app.ui.theme.Inama

data class WeatherUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val report: WeatherReport? = null,
    val failed: Boolean = false,
    val online: Boolean = true,
)

class WeatherViewModel(
    private val weather: WeatherRepository,
    private val profile: ProfileRepository,
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
    online: StateFlow<Boolean>,
) : ViewModel() {
    private val local = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = combine(local, online) { l, on -> l.copy(online = on) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherUiState())

    private var district: District? = null

    init {
        viewModelScope.launch {
            val id = profile.current()?.districtId
            district = catalog.districts().firstOrNull { it.id == id }
            load(force = false)
        }
    }

    fun refresh() {
        viewModelScope.launch { load(force = true) }
    }

    private suspend fun load(force: Boolean) {
        local.update { it.copy(refreshing = true, failed = false) }
        val report = runCatching { weather.forecast(district, settings.current().language.tag, force) }.getOrNull()
        local.update { it.copy(loading = false, refreshing = false, report = report ?: it.report, failed = report == null) }
    }
}

@Composable
fun WeatherScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: WeatherViewModel = viewModel {
        WeatherViewModel(container.weatherRepository, container.profileRepository, container.catalogRepository, container.settingsRepository, container.connectivity.isOnline)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    StatusBarIcons(light = true)
    val w = state.report

    LazyColumn(Modifier.fillMaxSize().background(c.ground), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(c.forestDeep)) {
                Illustration(R.drawable.img_hills_day, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x8015301A), Color(0x3315301A), Color(0xE615301A)))))
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircleIconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack, background = Color.Black.copy(alpha = 0.3f), tint = Color.White, bordered = false)
                    Text(stringResource(R.string.weather_title), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                    CircleIconButton(R.drawable.ic_sync, stringResource(R.string.action_refresh), { vm.refresh() }, background = Color.Black.copy(alpha = 0.3f), tint = Color.White, bordered = false)
                }
                if (w != null) {
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(w.place, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${w.current.tempC}°", style = MaterialTheme.typography.displayLarge, color = Color.White)
                            InamaIcon(conditionIcon(w.current.condition), tint = Color.White, size = 48.dp)
                        }
                        Text(
                            stringResource(R.string.weather_now_line, stringResource(conditionLabel(w.current.condition)), w.current.humidity, w.current.windKph),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                    }
                }
            }
        }
        if (!state.online) item { OfflineBanner(Modifier.padding(horizontal = 20.dp)) }
        when {
            state.loading -> item { LoadingState(label = stringResource(R.string.loading_weather)) }
            w == null -> item { ErrorState(stringResource(R.string.weather_failed_title), stringResource(R.string.weather_failed_body), retry = { vm.refresh() }) }
            else -> {
                if (w.source != WeatherSource.LIVE) {
                    item {
                        Banner(
                            stringResource(if (w.source == WeatherSource.DEMO) R.string.weather_demo_banner else R.string.weather_saved_banner, relativeTime(w.updatedAtMillis)),
                            BannerKind.INFO,
                            Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
                w.alerts.forEach { alert ->
                    item {
                        Banner(stringResource(alertLabel(alert.code), alert.date?.let { dayLabel(it) } ?: ""), BannerKind.WARNING, Modifier.padding(horizontal = 20.dp))
                    }
                }
                item {
                    SectionTitle(stringResource(R.string.weather_good_time), Modifier.padding(horizontal = 20.dp))
                }
                item {
                    InamaCard(Modifier.padding(horizontal = 20.dp), padding = 14.dp, spacing = 0.dp) {
                        w.decisions.forEachIndexed { i, d ->
                            if (i > 0) Divider()
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconTile(activityIcon(d.activity), size = 40.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(stringResource(activityLabel(d.activity)), style = MaterialTheme.typography.labelLarge, color = c.ink)
                                    Text(stringResource(decisionReason(d.reasonCode)), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                                }
                                VerdictChip(d.verdict)
                            }
                        }
                    }
                }
                item {
                    val reasons = w.decisions.map { stringResource(decisionReason(it.reasonCode)) }
                    Box(Modifier.padding(horizontal = 20.dp)) { ListenButton("weather", reasons.joinToString(" ")) }
                }
                item { SectionTitle(stringResource(R.string.weather_next_days), Modifier.padding(horizontal = 20.dp)) }
                item {
                    InamaCard(Modifier.padding(horizontal = 20.dp), padding = 14.dp, spacing = 0.dp) {
                        w.days.forEachIndexed { i, day ->
                            if (i > 0) Divider()
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(dayLabel(day.date), style = MaterialTheme.typography.labelLarge, color = c.ink, modifier = Modifier.width(84.dp))
                                InamaIcon(conditionIcon(day.condition), tint = c.forest, size = 26.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.weather_rain_chance, day.rainChance), style = MaterialTheme.typography.bodyMedium, color = if (day.rainChance >= 60) c.skyInk else c.ink)
                                    if (day.rainMm > 0) Text(stringResource(R.string.weather_rain_mm, day.rainMm), style = MaterialTheme.typography.bodySmall, color = c.muted)
                                }
                                Text("${day.highC}° / ${day.lowC}°", style = MaterialTheme.typography.labelLarge, color = c.ink)
                            }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.weather_updated, relativeTime(w.updatedAtMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}

private fun alertLabel(code: String): Int = when (code) {
    "heavy_rain" -> R.string.alert_heavy_rain
    "heat" -> R.string.alert_heat
    else -> R.string.alert_dry_spell
}
