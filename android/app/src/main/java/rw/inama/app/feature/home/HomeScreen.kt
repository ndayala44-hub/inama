package rw.inama.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import rw.inama.app.R
import rw.inama.app.domain.farm.Priority
import rw.inama.app.domain.model.FarmActivity
import rw.inama.app.domain.model.Urgency
import rw.inama.app.domain.model.Verdict
import rw.inama.app.feature.farm.currentSeason
import rw.inama.app.feature.farm.fieldProgress
import rw.inama.app.feature.farm.fieldStatus
import rw.inama.app.feature.farm.fieldSubtitle
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.StatusBarIcons
import rw.inama.app.ui.components.Avatar
import rw.inama.app.ui.components.ButtonKind
import rw.inama.app.ui.components.Chip
import rw.inama.app.ui.components.CircleIconButton
import rw.inama.app.ui.components.ConfidenceMeter
import rw.inama.app.ui.components.CropImage
import rw.inama.app.ui.components.Divider
import rw.inama.app.ui.components.FieldCard
import rw.inama.app.ui.components.GlassCard
import rw.inama.app.ui.components.IconTile
import rw.inama.app.ui.components.Illustration
import rw.inama.app.ui.components.InamaButton
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.InamaIcon
import rw.inama.app.ui.components.ListenButton
import rw.inama.app.ui.components.OfflineBanner
import rw.inama.app.ui.components.SectionTitle
import rw.inama.app.ui.components.TaskRow
import rw.inama.app.ui.components.UrgencyChip
import rw.inama.app.ui.components.conditionIcon
import rw.inama.app.ui.components.conditionLabel
import rw.inama.app.ui.components.dayLabel
import rw.inama.app.ui.components.decisionReason
import rw.inama.app.ui.theme.Inama
import java.time.LocalTime

@Composable
fun HomeScreen(
    onOpenDiagnosis: (String) -> Unit,
    onScan: (String?) -> Unit,
    onAsk: () -> Unit,
    onWeather: () -> Unit,
    onField: (String) -> Unit,
    onAddField: () -> Unit,
    onLearn: () -> Unit,
    onHistory: () -> Unit,
    onTasks: () -> Unit,
    onProfile: () -> Unit,
    onFarm: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel {
        HomeViewModel(
            container.profileRepository, container.fieldRepository, container.diagnosisRepository, container.taskRepository,
            container.settingsRepository, container.catalogRepository, container.weatherRepository, container.connectivity.isOnline,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val c = Inama.colors
    StatusBarIcons(light = true)

    val hour = LocalTime.now(rw.inama.app.feature.farm.FarmClock.zone).hour
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.greeting_morning
            hour < 17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        },
    )
    val season = currentSeason()
    val priorityTitle = priorityTitle(state.priority)
    val priorityBody = priorityBody(state.priority)
    val spraying = state.weather?.decisions?.firstOrNull { it.activity == FarmActivity.SPRAYING }
    val sprayingLine = spraying?.let { stringResource(decisionReason(it.reasonCode)) }
    val briefing = buildString {
        append(greeting).append(", ").append(state.farmer?.firstName.orEmpty()).append(". ")
        if (priorityTitle.isNotBlank()) append(priorityTitle).append(". ").append(priorityBody).append(" ")
        if (sprayingLine != null) append(sprayingLine)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.ground),
        contentPadding = PaddingValues(bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------------------------------------------------------------- header
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(c.forestDeep)) {
                Illustration(R.drawable.bg_aerial_header, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x8C15301A), Color(0xD115301A), Color(0xF015301A)))))
                Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onProfile)) { Avatar(state.farmer?.initials ?: "?", 48.dp) }
                        Column(Modifier.weight(1f)) {
                            Text("$greeting,", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                            Text(state.farmer?.firstName.orEmpty(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        }
                        CircleIconButton(R.drawable.ic_clock, stringResource(R.string.cd_history), onHistory, background = Color.White.copy(alpha = 0.14f), tint = Color.White, bordered = false)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(state.district?.name ?: stringResource(R.string.home_location_unknown), background = Color.White.copy(alpha = 0.14f), foreground = Color.White, icon = R.drawable.ic_pin)
                        Chip(stringResource(R.string.home_season, season.letter, season.week), background = Color.White.copy(alpha = 0.14f), foreground = Color.White, icon = R.drawable.ic_calendar)
                    }
                    WeatherGlass(state, onWeather)
                    ListenButton("briefing", briefing, dark = true)
                }
            }
        }
        if (!state.online) item { OfflineBanner(Modifier.padding(horizontal = 20.dp)) }

        // ---------------------------------------------------------------- priority
        item { SectionTitle(stringResource(R.string.home_priority), Modifier.padding(horizontal = 20.dp)) }
        item {
            PriorityCard(
                priority = state.priority,
                title = priorityTitle,
                body = priorityBody,
                onAct = {
                    val p = state.priority ?: return@PriorityCard
                    when (p.kind) {
                        Priority.Kind.DIAGNOSIS_FOLLOW_UP -> p.diagnosisId?.let(onOpenDiagnosis)
                        Priority.Kind.CROP_STAGE -> onScan(p.fieldId)
                        Priority.Kind.WEATHER -> onWeather()
                        Priority.Kind.ALL_GOOD -> onScan(null)
                    }
                },
                onAddField = onAddField,
                hasFields = state.fields.isNotEmpty(),
            )
        }

        // ---------------------------------------------------------------- fields
        item { SectionTitle(stringResource(R.string.home_my_fields), Modifier.padding(horizontal = 20.dp), stringResource(R.string.action_see_all), onFarm) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.fields, key = { it.id }) { field ->
                    FieldCard(
                        name = field.name,
                        subtitle = fieldSubtitle(field, state.kb, state.todayEpochDay),
                        cropId = field.cropId,
                        progress = fieldProgress(field, state.kb, state.todayEpochDay),
                        status = fieldStatus(field, state.diagnoses, state.kb, state.todayEpochDay),
                        onClick = { onField(field.id) },
                    )
                }
                item { AddFieldCard(onAddField) }
            }
        }

        // ---------------------------------------------------------------- quick actions
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(R.drawable.ic_camera, stringResource(R.string.home_check_plant), Modifier.weight(1f), dark = true) { onScan(null) }
                    QuickAction(R.drawable.ic_mic, stringResource(R.string.home_ask), Modifier.weight(1f), onClick = onAsk)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(R.drawable.ic_book, stringResource(R.string.home_learn), Modifier.weight(1f), onClick = onLearn)
                    QuickAction(R.drawable.ic_records, stringResource(R.string.home_history), Modifier.weight(1f), onClick = onHistory)
                }
            }
        }

        // ---------------------------------------------------------------- tasks
        item { SectionTitle(stringResource(R.string.home_this_week), Modifier.padding(horizontal = 20.dp), stringResource(R.string.action_all_tasks), onTasks) }
        item {
            InamaCard(Modifier.padding(horizontal = 20.dp), padding = 8.dp, spacing = 0.dp) {
                if (state.openTasks.isEmpty()) {
                    Text(stringResource(R.string.home_no_tasks), style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(12.dp))
                }
                state.openTasks.forEachIndexed { i, task ->
                    if (i > 0) Divider()
                    TaskRow(
                        task,
                        fieldName = state.fields.firstOrNull { it.id == task.fieldId }?.name,
                        onToggle = { done -> scope.launch { container.taskRepository.setDone(task.id, done) } },
                    )
                }
            }
        }

        // ---------------------------------------------------------------- recent checks
        val recent = state.diagnoses.take(2)
        if (recent.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_recent_checks), Modifier.padding(horizontal = 20.dp), stringResource(R.string.action_see_all), onHistory) }
            items(recent, key = { it.id }) { d ->
                InamaCard(Modifier.padding(horizontal = 20.dp), padding = 14.dp, onClick = { onOpenDiagnosis(d.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CropImage(d.cropId, 44.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(d.headline, style = MaterialTheme.typography.labelLarge, color = c.ink)
                            ConfidenceMeter(d.confidence, showReason = false)
                        }
                        InamaIcon(R.drawable.ic_chev, tint = c.muted, size = 20.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun priorityTitle(p: Priority?): String = when (p?.kind) {
    null -> ""
    Priority.Kind.WEATHER -> stringResource(R.string.priority_weather_title)
    Priority.Kind.ALL_GOOD -> stringResource(R.string.priority_all_good_title)
    else -> p?.title.orEmpty()
}

@Composable
private fun priorityBody(p: Priority?): String = when (p?.kind) {
    null -> ""
    Priority.Kind.WEATHER -> stringResource(R.string.priority_weather_body)
    Priority.Kind.ALL_GOOD -> stringResource(R.string.priority_all_good_body)
    else -> p?.body.orEmpty()
}

@Composable
private fun WeatherGlass(state: HomeState, onWeather: () -> Unit) {
    val w = state.weather
    GlassCard(onClick = onWeather) {
        if (w == null) {
            Text(stringResource(R.string.loading_weather), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            return@GlassCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InamaIcon(conditionIcon(w.current.condition), tint = Color.White, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text("${w.current.tempC}°", style = MaterialTheme.typography.displayMedium, color = Color.White)
                Text(stringResource(conditionLabel(w.current.condition)), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(w.place, style = MaterialTheme.typography.labelMedium, color = Color.White)
                if (w.source != rw.inama.app.domain.model.WeatherSource.LIVE) {
                    Text(
                        stringResource(if (w.source == rw.inama.app.domain.model.WeatherSource.DEMO) R.string.weather_demo else R.string.weather_saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }
        val heavy = w.days.drop(1).firstOrNull { it.rainChance >= 70 }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassStat(stringResource(R.string.weather_rain_today), "${w.days.firstOrNull()?.rainChance ?: w.current.rainChance}%", Modifier.weight(1f))
            GlassStat(stringResource(R.string.weather_next_heavy_rain), heavy?.let { dayLabel(it.date) } ?: stringResource(R.string.weather_none_soon), Modifier.weight(1f))
            GlassStat(stringResource(R.string.weather_wind), stringResource(R.string.weather_kmh, w.current.windKph), Modifier.weight(1f))
        }
        val spraying = w.decisions.firstOrNull { it.activity == FarmActivity.SPRAYING }
        val weeding = w.decisions.firstOrNull { it.activity == FarmActivity.WEEDING }
        val line = when {
            spraying?.verdict == Verdict.WAIT -> stringResource(decisionReason(spraying.reasonCode))
            weeding?.verdict == Verdict.GO -> stringResource(decisionReason(weeding.reasonCode))
            else -> spraying?.let { stringResource(decisionReason(it.reasonCode)) }
        }
        if (line != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Inama.colors.sprout.copy(alpha = 0.22f)).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InamaIcon(R.drawable.ic_check, tint = Inama.colors.sprout, size = 20.dp)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
    }
}

@Composable
private fun GlassStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun PriorityCard(priority: Priority?, title: String, body: String, onAct: () -> Unit, onAddField: () -> Unit, hasFields: Boolean) {
    val c = Inama.colors
    val urgent = priority?.urgency == Urgency.TODAY
    InamaCard(Modifier.padding(horizontal = 20.dp), border = if (urgent) c.clay else c.line, spacing = 12.dp) {
        if (priority == null) {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            return@InamaCard
        }
        if (!hasFields) {
            Text(stringResource(R.string.priority_no_fields_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
            Text(stringResource(R.string.priority_no_fields_body), style = MaterialTheme.typography.bodyLarge, color = c.muted)
            InamaButton(stringResource(R.string.action_add_field), onAddField, icon = R.drawable.ic_plus)
            return@InamaCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            UrgencyChip(priority.urgency)
            if (priority.cropId != null) CropImage(priority.cropId, 32.dp)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink)
        if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyLarge, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            InamaButton(
                text = stringResource(
                    when (priority.kind) {
                        Priority.Kind.DIAGNOSIS_FOLLOW_UP -> R.string.priority_cta_open
                        Priority.Kind.WEATHER -> R.string.priority_cta_weather
                        else -> R.string.priority_cta_check
                    },
                ),
                onClick = onAct,
                modifier = Modifier.weight(1f),
                height = 52.dp,
            )
            ListenButton("priority", "$title. $body")
        }
    }
}

@Composable
private fun AddFieldCard(onClick: () -> Unit) {
    val c = Inama.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.width(160.dp).height(196.dp).clip(shape).border(1.5.dp, c.line2, shape).background(c.paper).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(R.drawable.ic_plus)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.action_add_field), style = MaterialTheme.typography.labelLarge, color = c.forest)
    }
}

@Composable
private fun QuickAction(icon: Int, label: String, modifier: Modifier = Modifier, dark: Boolean = false, onClick: () -> Unit) {
    val c = Inama.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .heightIn(min = 104.dp)
            .clip(shape)
            .background(if (dark) c.forest else c.paper)
            .border(1.dp, if (dark) c.forest else c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, background = if (dark) Color.White.copy(alpha = 0.14f) else c.mint, tint = if (dark) Color.White else c.forest)
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (dark) Color.White else c.ink)
    }
}
