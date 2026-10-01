package rw.inama.app.feature.farm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import rw.inama.app.R
import rw.inama.app.domain.farm.CropStageCalculator
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.LandType
import rw.inama.app.feature.onboarding.fieldColors
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.StatusBarIcons
import rw.inama.app.ui.components.ButtonKind
import rw.inama.app.ui.components.Chip
import rw.inama.app.ui.components.ChipFlow
import rw.inama.app.ui.components.CircleIconButton
import rw.inama.app.ui.components.ConfidenceMeter
import rw.inama.app.ui.components.CropChoice
import rw.inama.app.ui.components.CropImage
import rw.inama.app.ui.components.CropTile
import rw.inama.app.ui.components.Divider
import rw.inama.app.ui.components.EmptyState
import rw.inama.app.ui.components.Eyebrow
import rw.inama.app.ui.components.FieldStatus
import rw.inama.app.ui.components.Illustration
import rw.inama.app.ui.components.InamaButton
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.InamaIcon
import rw.inama.app.ui.components.ListenButton
import rw.inama.app.ui.components.LoadingState
import rw.inama.app.ui.components.PillChoice
import rw.inama.app.ui.components.ProgressLine
import rw.inama.app.ui.components.ScreenHeader
import rw.inama.app.ui.components.SectionTitle
import rw.inama.app.ui.components.StageTrack
import rw.inama.app.ui.components.StatusChip
import rw.inama.app.ui.components.TaskRow
import rw.inama.app.ui.components.UrgencyChip
import rw.inama.app.ui.components.formatDate
import rw.inama.app.ui.components.pluralText
import rw.inama.app.ui.components.relativeTime
import rw.inama.app.ui.theme.Inama

@androidx.annotation.StringRes
fun landLabel(t: LandType): Int = when (t) {
    LandType.HILLSIDE -> R.string.land_hillside
    LandType.FLAT -> R.string.land_flat
    LandType.VALLEY -> R.string.land_valley
}

@androidx.annotation.DrawableRes
fun landIcon(t: LandType): Int = when (t) {
    LandType.HILLSIDE -> R.drawable.ic_trend
    LandType.FLAT -> R.drawable.ic_flat
    LandType.VALLEY -> R.drawable.ic_drop
}

// =============================================================================== Farm tab

@Composable
fun FarmScreen(onField: (String) -> Unit, onAddField: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: FarmViewModel = viewModel {
        FarmViewModel(container.fieldRepository, container.diagnosisRepository, container.taskRepository, container.catalogRepository, container.settingsRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    StatusBarIcons(light = true)
    val statuses = state.fields.associate { it.id to fieldStatus(it, state.diagnoses, state.kb, state.todayEpochDay) }

    LazyColumn(Modifier.fillMaxSize().background(c.ground), contentPadding = PaddingValues(bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(c.forestDeep)) {
                Illustration(R.drawable.img_field_map, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x6615301A), Color(0xE615301A)))))
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.farm_title), style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))
                    CircleIconButton(R.drawable.ic_plus, stringResource(R.string.action_add_field), onAddField, background = c.sprout, tint = c.ink, bordered = false)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.farm_summary, state.fields.size, state.totalAres),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    ChipFlow {
                        val good = statuses.values.count { it == FieldStatus.GOOD }
                        val check = statuses.values.count { it == FieldStatus.CHECK }
                        val sick = statuses.values.count { it == FieldStatus.SICK }
                        if (good > 0) Chip(stringResource(R.string.farm_count_good, good), background = c.mint, foreground = c.mintInk, icon = R.drawable.ic_check)
                        if (check > 0) Chip(stringResource(R.string.farm_count_check, check), background = c.amberSoft, foreground = c.amberInk, icon = R.drawable.ic_eye)
                        if (sick > 0) Chip(stringResource(R.string.farm_count_sick, sick), background = c.claySoft, foreground = c.clayInk, icon = R.drawable.ic_alert)
                    }
                }
            }
        }
        when {
            state.loading -> item { LoadingState() }
            state.fields.isEmpty() -> item {
                EmptyState(
                    R.drawable.ic_farm,
                    stringResource(R.string.farm_empty_title),
                    stringResource(R.string.farm_empty_body),
                    Modifier.padding(horizontal = 20.dp),
                    action = stringResource(R.string.action_add_field),
                    onAction = onAddField,
                )
            }
            else -> {
                items(state.fields, key = { it.id }) { field ->
                    FieldListCard(field, state, statuses[field.id] ?: FieldStatus.GOOD) { onField(field.id) }
                }
                item {
                    InamaButton(stringResource(R.string.action_add_field), onAddField, kind = ButtonKind.SECONDARY, icon = R.drawable.ic_plus, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun FieldListCard(field: Field, state: FarmUiState, status: FieldStatus, onClick: () -> Unit) {
    val c = Inama.colors
    val crop = state.kb?.crop(field.cropId)
    val progress = CropStageCalculator.progress(crop, field, state.todayEpochDay)
    InamaCard(Modifier.padding(horizontal = 20.dp), padding = 14.dp, onClick = onClick, spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CropTile(field.cropId, 60.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(field.name, style = MaterialTheme.typography.titleSmall, color = c.ink)
                Text(fieldSubtitle(field, state.kb, state.todayEpochDay), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                progress.stage?.let { Text(it.name, style = MaterialTheme.typography.labelMedium, color = c.forest) }
            }
            StatusChip(status)
        }
        if (field.plantedOnEpochDay != null) ProgressLine(progress.fraction, height = 6.dp)
    }
}

// =============================================================================== Field detail

@Composable
fun FieldDetailScreen(
    id: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onScan: (String?) -> Unit,
    onDiagnosis: (String) -> Unit,
    onTasks: () -> Unit,
    onDeleted: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: FieldDetailViewModel = viewModel(key = "field_$id") {
        FieldDetailViewModel(id, container.fieldRepository, container.diagnosisRepository, container.taskRepository, container.catalogRepository, container.settingsRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    var confirmDelete by remember { mutableStateOf(false) }
    val field = state.fields.firstOrNull { it.id == id }
    if (field == null) {
        Column(Modifier.fillMaxSize().background(c.ground)) {
            ScreenHeader(stringResource(R.string.field_title), onBack = onBack)
            if (state.loading) LoadingState() else EmptyState(R.drawable.ic_farm, stringResource(R.string.field_missing_title), stringResource(R.string.field_missing_body), action = stringResource(R.string.action_back), onAction = onBack)
        }
        return
    }
    val crop = state.kb?.crop(field.cropId)
    val progress = CropStageCalculator.progress(crop, field, state.todayEpochDay)
    val tips = crop?.stageTips?.filter { t -> progress.dayNumber != null && progress.dayNumber in t.fromDay..t.toDay }.orEmpty()
    val checks = state.diagnoses.filter { it.fieldId == id }.sortedByDescending { it.createdAtMillis }.take(3)
    val openTasks = state.tasks.filter { it.fieldId == id && !it.done }.sortedBy { it.urgency.ordinal }

    LazyColumn(Modifier.fillMaxSize().background(c.ground), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(c.sand)) {
                Column {
                    ScreenHeader(field.name, subtitle = listOfNotNull(crop?.name, stringResource(R.string.area_ares, field.areaAres), stringResource(landLabel(field.landType))).joinToString(" · "), onBack = onBack, actions = {
                        CircleIconButton(R.drawable.ic_edit, stringResource(R.string.action_edit), { onEdit(id) })
                        CircleIconButton(R.drawable.ic_trash, stringResource(R.string.action_delete), { confirmDelete = true })
                    })
                    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                        if (field.cropId == "maize" && progress.stageIndex >= 0) {
                            Image(painterResource(maizeStageDrawable(progress.stageIndex)), contentDescription = null, modifier = Modifier.size(160.dp))
                        } else {
                            CropImage(field.cropId, 140.dp)
                        }
                    }
                }
            }
        }
        item {
            InamaCard(Modifier.padding(horizontal = 20.dp), spacing = 12.dp) {
                if (crop != null && field.plantedOnEpochDay != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(progress.stage?.name ?: "", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.field_day_of, progress.dayNumber ?: 0, crop.seasonDays), style = MaterialTheme.typography.labelMedium, color = c.muted)
                    }
                    StageTrack(crop.stages.map { it.name }, progress.stageIndex)
                    ProgressLine(progress.fraction)
                    Text(
                        if ((progress.daysToHarvest ?: 0) > 0) stringResource(R.string.field_harvest_in, progress.daysToHarvest ?: 0) else stringResource(R.string.field_harvest_now),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                    )
                    Text(stringResource(R.string.field_planted_on, formatDate(field.plantedOnEpochDay)), style = MaterialTheme.typography.bodySmall, color = c.muted)
                } else {
                    Text(stringResource(R.string.field_not_planted_title), style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text(stringResource(R.string.field_not_planted_body), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    InamaButton(stringResource(R.string.field_add_planting_date), { onEdit(id) }, kind = ButtonKind.SECONDARY, icon = R.drawable.ic_calendar)
                }
            }
        }
        item {
            InamaButton(stringResource(R.string.field_check_plant), { onScan(id) }, icon = R.drawable.ic_camera, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (tips.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.field_this_stage), Modifier.padding(horizontal = 20.dp)) }
            items(tips) { tip ->
                InamaCard(Modifier.padding(horizontal = 20.dp), spacing = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UrgencyChip(tip.urgency)
                        Box(Modifier.weight(1f))
                        ListenButton("tip_${field.id}_${tip.fromDay}", "${tip.title}. ${tip.body}")
                    }
                    Text(tip.title, style = MaterialTheme.typography.titleSmall, color = c.ink)
                    Text(tip.body, style = MaterialTheme.typography.bodyLarge, color = c.muted)
                }
            }
        }
        item { SectionTitle(stringResource(R.string.field_tasks), Modifier.padding(horizontal = 20.dp), stringResource(R.string.action_all_tasks), onTasks) }
        item {
            InamaCard(Modifier.padding(horizontal = 20.dp), padding = 8.dp, spacing = 0.dp) {
                if (openTasks.isEmpty()) Text(stringResource(R.string.field_no_tasks), style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(12.dp))
                openTasks.forEachIndexed { i, t ->
                    if (i > 0) Divider()
                    TaskRow(t, fieldName = null, onToggle = { done -> vm.setTaskDone(t.id, done) })
                }
            }
        }
        if (checks.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.field_recent_checks), Modifier.padding(horizontal = 20.dp)) }
            items(checks, key = { it.id }) { d ->
                InamaCard(Modifier.padding(horizontal = 20.dp), padding = 14.dp, onClick = { onDiagnosis(d.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(d.headline, style = MaterialTheme.typography.labelLarge, color = c.ink)
                            ConfidenceMeter(d.confidence, showReason = false)
                            Text(relativeTime(d.createdAtMillis), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        InamaIcon(R.drawable.ic_chev, tint = c.muted, size = 20.dp)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.field_delete_title, field.name)) },
            text = { Text(stringResource(R.string.field_delete_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDeleted) }) { Text(stringResource(R.string.action_delete), color = c.clay) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun maizeStageDrawable(index: Int): Int = when (index) {
    0 -> R.drawable.maize_stage_0
    1 -> R.drawable.maize_stage_1
    2 -> R.drawable.maize_stage_2
    3 -> R.drawable.maize_stage_3
    else -> R.drawable.maize_stage_4
}

// =============================================================================== Field editor

@Composable
fun FieldEditorScreen(fieldId: String?, firstRun: Boolean, onBack: () -> Unit, onSaved: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: FieldEditorViewModel = viewModel(key = "field_edit_${fieldId ?: "new"}") {
        FieldEditorViewModel(fieldId, container.fieldRepository, container.catalogRepository, container.settingsRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    val kb = state.kb
    Column(Modifier.fillMaxSize().background(c.ground).imePadding()) {
        ScreenHeader(
            stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit),
            subtitle = if (firstRun) stringResource(R.string.step_of, 2, 2) else null,
            onBack = onBack,
        )
        if (state.loading || kb == null) {
            LoadingState()
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (firstRun) Text(stringResource(R.string.editor_first_run_body), style = MaterialTheme.typography.bodyLarge, color = c.muted)
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onName,
                label = { Text(stringResource(R.string.editor_name)) },
                placeholder = { Text(stringResource(R.string.editor_name_hint)) },
                isError = state.showErrors && state.name.isBlank(),
                supportingText = { if (state.showErrors && state.name.isBlank()) Text(stringResource(R.string.editor_name_error)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Eyebrow(stringResource(R.string.editor_crop))
            if (state.showErrors && state.cropId == null) Text(stringResource(R.string.editor_crop_error), style = MaterialTheme.typography.bodyMedium, color = c.clay)
            kb.crops.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { crop -> CropChoice(crop, crop.id == state.cropId, { vm.onCrop(crop.id) }, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }

            Eyebrow(stringResource(R.string.editor_size))
            InamaCard(spacing = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircleIconButton(R.drawable.ic_minus, stringResource(R.string.cd_less), { vm.changeArea(-1) })
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.area_ares, state.areaAres), style = MaterialTheme.typography.headlineMedium, color = c.ink)
                        Text(stringResource(R.string.editor_size_m2, state.areaAres * 100), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    CircleIconButton(R.drawable.ic_plus, stringResource(R.string.cd_more), { vm.changeArea(1) })
                }
                ChipFlow {
                    listOf(5, 10, 20, 50, 100).forEach { a -> PillChoice(stringResource(R.string.area_ares, a), state.areaAres == a, { vm.setArea(a) }) }
                }
                Text(stringResource(R.string.editor_size_hint), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }

            Eyebrow(stringResource(R.string.editor_land))
            ChipFlow {
                LandType.entries.forEach { t -> PillChoice(stringResource(landLabel(t)), state.landType == t, { vm.onLand(t) }, icon = landIcon(t)) }
            }

            Eyebrow(stringResource(R.string.editor_planted))
            ChipFlow {
                PillChoice(stringResource(R.string.editor_not_yet), state.plantedOnEpochDay == null, { vm.setPlanted(false) })
                PillChoice(stringResource(R.string.editor_already_planted), state.plantedOnEpochDay != null, { vm.setPlanted(true) }, icon = R.drawable.ic_spade)
            }
            state.plantedOnEpochDay?.let { planted ->
                val weeks = ((FarmClock.todayEpochDay() - planted) / 7).toInt()
                InamaCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircleIconButton(R.drawable.ic_minus, stringResource(R.string.cd_earlier), { vm.shiftPlanted(-7) })
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (weeks == 0) stringResource(R.string.editor_this_week) else pluralText(R.plurals.weeks_ago, weeks, weeks),
                                style = MaterialTheme.typography.titleLarge,
                                color = c.ink,
                            )
                            Text(formatDate(planted), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        CircleIconButton(R.drawable.ic_plus, stringResource(R.string.cd_later), { vm.shiftPlanted(7) })
                    }
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InamaButton(stringResource(if (state.saving) R.string.saving else R.string.editor_save), { vm.save(onSaved) }, enabled = !state.saving, icon = R.drawable.ic_check)
            if (firstRun) InamaButton(stringResource(R.string.editor_skip), onSaved, kind = ButtonKind.GHOST)
        }
    }
}
