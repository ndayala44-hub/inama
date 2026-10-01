package rw.inama.app.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import rw.inama.app.R
import rw.inama.app.domain.model.CaseStatus
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.EngineInfo
import rw.inama.app.domain.model.EngineMode
import rw.inama.app.domain.model.EvidenceKind
import rw.inama.app.domain.model.FeedbackVerdict
import rw.inama.app.domain.model.Outcome
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.StatusBarIcons
import rw.inama.app.ui.components.Banner
import rw.inama.app.ui.components.BannerKind
import rw.inama.app.ui.components.ButtonKind
import rw.inama.app.ui.components.Chip
import rw.inama.app.ui.components.ChipFlow
import rw.inama.app.ui.components.CircleIconButton
import rw.inama.app.ui.components.ConfidenceMeter
import rw.inama.app.ui.components.CropTile
import rw.inama.app.ui.components.DashedLinkCard
import rw.inama.app.ui.components.Divider
import rw.inama.app.ui.components.EmptyState
import rw.inama.app.ui.components.EvidenceLine
import rw.inama.app.ui.components.Eyebrow
import rw.inama.app.ui.components.IconTile
import rw.inama.app.ui.components.InamaButton
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.InamaIcon
import rw.inama.app.ui.components.ListenButton
import rw.inama.app.ui.components.LoadingState
import rw.inama.app.ui.components.NumberedSteps
import rw.inama.app.ui.components.PillChoice
import rw.inama.app.ui.components.ScreenHeader
import rw.inama.app.ui.components.SourcesRow
import rw.inama.app.ui.components.ThreeQuestions
import rw.inama.app.ui.components.UrgencyChip
import rw.inama.app.ui.components.formatDateTime
import rw.inama.app.ui.components.photoHintLabel
import rw.inama.app.ui.theme.Inama

@Composable
fun DiagnosisScreen(
    id: String,
    onBack: () -> Unit,
    onExpertCase: (String) -> Unit,
    onRetake: (String?) -> Unit,
    onTasks: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: DiagnosisViewModel = viewModel(key = "diagnosis_$id") {
        DiagnosisViewModel(id, container.diagnosisRepository, container.fieldRepository, container.taskRepository, container.addAdviceToTasks, container.expertRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    val d = state.diagnosis
    if (d == null) {
        Column(Modifier.fillMaxSize().background(c.ground)) {
            ScreenHeader(stringResource(R.string.diagnosis_title), onBack = onBack)
            if (state.loading) LoadingState() else EmptyState(R.drawable.ic_leaf, stringResource(R.string.diagnosis_missing_title), stringResource(R.string.diagnosis_missing_body), action = stringResource(R.string.action_back), onAction = onBack)
        }
        return
    }
    StatusBarIcons(light = true)
    val spoken = listOf(d.headline, d.whatsHappening, d.whyItMatters, d.steps.joinToString(". ")).joinToString(". ")

    LazyColumn(Modifier.fillMaxSize().background(c.ground), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Hero(d, state.fieldName, onBack, spoken) }
        item {
            Column(
                Modifier.offset(y = (-28).dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.ground).padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Summary(d)
                d.photoProblem?.let { problem ->
                    InamaCard(background = c.amberSoft, border = c.amber, spacing = 10.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            InamaIcon(R.drawable.ic_camera, tint = c.amberInk, size = 22.dp)
                            Text(problem.title, style = MaterialTheme.typography.titleSmall, color = c.amberInk)
                        }
                        Text(problem.body, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                        if (problem.tips.isNotEmpty()) NumberedSteps(problem.tips)
                        InamaButton(stringResource(R.string.diagnosis_retake), { onRetake(d.fieldId) }, icon = R.drawable.ic_camera)
                    }
                }
                when (d.outcome) {
                    Outcome.CONDITION -> ConditionBody(d, state, vm, onTasks, onExpertCase)
                    Outcome.UNCERTAIN -> UncertainBody(d, state, vm, onRetake, onExpertCase, onTasks)
                    Outcome.HEALTHY -> HealthyBody(d, onRetake)
                }
                CaseLink(d, onExpertCase)
                FeedbackCard(d.feedback, vm::feedback)
                AboutThisAnswer(d.engine, d.sources)
            }
        }
    }
}

@Composable
private fun Hero(d: Diagnosis, fieldName: String?, onBack: () -> Unit, spoken: String) {
    val c = Inama.colors
    Box(Modifier.fillMaxWidth().height(320.dp).background(c.forestDeep)) {
        if (d.imagePath != null) {
            AsyncImage(model = File(d.imagePath), contentDescription = stringResource(R.string.cd_your_photo), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x9915301A), Color.Transparent, Color(0xCC15301A)))))
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircleIconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack, background = Color.Black.copy(alpha = 0.35f), tint = Color.White, bordered = false)
            Box(Modifier.weight(1f))
            ListenButton("diagnosis_${d.id}", spoken, dark = true)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipFlow {
                Chip(d.cropName, background = Color.White.copy(alpha = 0.18f), foreground = Color.White, icon = R.drawable.ic_leaf)
                if (fieldName != null) Chip(fieldName, background = Color.White.copy(alpha = 0.18f), foreground = Color.White, icon = R.drawable.ic_farm)
            }
            Text(formatDateTime(d.createdAtMillis), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun Summary(d: Diagnosis) {
    val c = Inama.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(
            stringResource(
                when (d.outcome) {
                    Outcome.CONDITION -> R.string.diagnosis_eyebrow_condition
                    Outcome.UNCERTAIN -> R.string.diagnosis_eyebrow_uncertain
                    Outcome.HEALTHY -> R.string.diagnosis_eyebrow_healthy
                },
            ),
        )
        Text(d.headline, style = MaterialTheme.typography.headlineMedium, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (d.outcome != Outcome.HEALTHY) UrgencyChip(d.urgency)
        }
        ConfidenceMeter(d.confidence)
    }
}

@Composable
private fun ConditionBody(d: Diagnosis, state: DiagnosisUiState, vm: DiagnosisViewModel, onTasks: () -> Unit, onExpertCase: (String) -> Unit) {
    val c = Inama.colors
    InamaCard { ThreeQuestions(d.whatsHappening, d.whyItMatters, d.steps) }
    TasksAction(state, vm, onTasks)
    Evidence(d)
    if (d.lessLikely.isNotEmpty()) {
        InamaCard(spacing = 10.dp) {
            Text(stringResource(R.string.diagnosis_less_likely), style = MaterialTheme.typography.labelLarge, color = c.ink)
            ChipFlow { d.lessLikely.forEach { Chip(it.name, background = c.sand, foreground = c.ink) } }
        }
    }
    if (d.escalation.recommended) {
        EscalateButton(d, state, vm, onExpertCase, primary = true)
    } else if (d.caseId == null) {
        DashedLinkCard(stringResource(R.string.diagnosis_person_title), stringResource(R.string.diagnosis_person_body)) { vm.escalate(onExpertCase) }
    }
    if (state.escalateFailed) Banner(stringResource(R.string.diagnosis_escalate_failed), BannerKind.WARNING)
}

@Composable
private fun UncertainBody(
    d: Diagnosis,
    state: DiagnosisUiState,
    vm: DiagnosisViewModel,
    onRetake: (String?) -> Unit,
    onExpertCase: (String) -> Unit,
    onTasks: () -> Unit,
) {
    val c = Inama.colors
    InamaCard(background = c.skySoft, border = null, spacing = 10.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(R.drawable.ic_info, background = c.paper, tint = c.skyInk)
            Text(stringResource(R.string.diagnosis_not_sure_title), style = MaterialTheme.typography.titleSmall, color = c.skyInk, modifier = Modifier.weight(1f))
        }
        Text(stringResource(R.string.diagnosis_not_sure_body), style = MaterialTheme.typography.bodyLarge, color = c.ink)
        if (d.candidates.isNotEmpty()) {
            Text(stringResource(R.string.diagnosis_might_be), style = MaterialTheme.typography.labelLarge, color = c.ink)
            ChipFlow { d.candidates.forEach { Chip(it.name, background = c.paper, foreground = c.ink, border = c.line2) } }
        }
    }
    EscalateButton(d, state, vm, onExpertCase, primary = true)
    if (state.escalateFailed) Banner(stringResource(R.string.diagnosis_escalate_failed), BannerKind.WARNING)
    InamaButton(stringResource(R.string.diagnosis_another_photo), { onRetake(d.fieldId) }, kind = ButtonKind.SECONDARY, icon = R.drawable.ic_camera)
    Eyebrow(stringResource(R.string.diagnosis_safe_now))
    InamaCard { ThreeQuestions(d.whatsHappening, d.whyItMatters, d.steps) }
    TasksAction(state, vm, onTasks)
    Evidence(d)
}

@Composable
private fun HealthyBody(d: Diagnosis, onRetake: (String?) -> Unit) {
    val c = Inama.colors
    InamaCard(background = c.mint, border = null, spacing = 10.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.sprout), contentAlignment = Alignment.Center) { InamaIcon(R.drawable.ic_check, tint = c.ink, size = 24.dp) }
            Text(stringResource(R.string.diagnosis_healthy_note), style = MaterialTheme.typography.bodyLarge, color = c.mintInk, modifier = Modifier.weight(1f))
        }
    }
    InamaCard { ThreeQuestions(d.whatsHappening, d.whyItMatters, d.steps) }
    Evidence(d)
    InamaButton(stringResource(R.string.diagnosis_check_another), { onRetake(d.fieldId) }, kind = ButtonKind.SECONDARY, icon = R.drawable.ic_camera)
}

@Composable
private fun TasksAction(state: DiagnosisUiState, vm: DiagnosisViewModel, onTasks: () -> Unit) {
    when {
        state.addedCount != null && state.addedCount > 0 ->
            Banner(stringResource(R.string.diagnosis_tasks_added, state.addedCount), BannerKind.SUCCESS, action = stringResource(R.string.action_see_tasks), onAction = onTasks)
        state.inTasks ->
            Banner(stringResource(R.string.diagnosis_tasks_already), BannerKind.SUCCESS, action = stringResource(R.string.action_see_tasks), onAction = onTasks)
        else -> InamaButton(stringResource(R.string.diagnosis_add_tasks), vm::addStepsToTasks, icon = R.drawable.ic_tasks)
    }
}

@Composable
private fun EscalateButton(d: Diagnosis, state: DiagnosisUiState, vm: DiagnosisViewModel, onExpertCase: (String) -> Unit, primary: Boolean) {
    if (d.caseId != null) return
    InamaButton(
        text = stringResource(if (state.escalating) R.string.diagnosis_sending else R.string.diagnosis_escalate),
        onClick = { vm.escalate(onExpertCase) },
        enabled = !state.escalating,
        kind = if (primary) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
        icon = R.drawable.ic_headset,
    )
}

@Composable
private fun Evidence(d: Diagnosis) {
    val c = Inama.colors
    if (d.evidence.isEmpty() && d.lookFor == null && d.signs.isEmpty()) return
    InamaCard(spacing = 10.dp) {
        Text(stringResource(R.string.diagnosis_why_title), style = MaterialTheme.typography.labelLarge, color = c.ink)
        d.evidence.forEach { e ->
            val text = when (e.kind) {
                EvidenceKind.PHOTO -> stringResource(photoHintLabel(e.id))
                else -> e.label
            }
            EvidenceLine(ok = true, text = text)
        }
        d.lookFor?.let { EvidenceLine(ok = false, text = stringResource(R.string.diagnosis_look_for, it)) }
        if (d.signs.isNotEmpty() && d.outcome == Outcome.CONDITION) {
            Divider()
            Text(stringResource(R.string.diagnosis_signs_title), style = MaterialTheme.typography.labelMedium, color = c.muted)
            d.signs.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = c.ink) }
        }
    }
}

@Composable
private fun CaseLink(d: Diagnosis, onExpertCase: (String) -> Unit) {
    val caseId = d.caseId ?: return
    val c = Inama.colors
    InamaCard(background = c.sproutSoft, border = null, onClick = { onExpertCase(caseId) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(R.drawable.ic_headset, background = c.paper)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.diagnosis_case_sent), style = MaterialTheme.typography.labelLarge, color = c.ink)
                Text(stringResource(R.string.diagnosis_case_view), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            InamaIcon(R.drawable.ic_chev, tint = c.muted, size = 20.dp)
        }
    }
}

@Composable
private fun FeedbackCard(current: FeedbackVerdict?, onVerdict: (FeedbackVerdict) -> Unit) {
    val c = Inama.colors
    InamaCard(spacing = 10.dp) {
        Text(stringResource(R.string.feedback_title), style = MaterialTheme.typography.labelLarge, color = c.ink)
        ChipFlow {
            PillChoice(stringResource(R.string.feedback_yes), current == FeedbackVerdict.CORRECT, { onVerdict(FeedbackVerdict.CORRECT) }, icon = R.drawable.ic_thumbup)
            PillChoice(stringResource(R.string.feedback_no), current == FeedbackVerdict.WRONG, { onVerdict(FeedbackVerdict.WRONG) }, icon = R.drawable.ic_thumbdown)
            PillChoice(stringResource(R.string.feedback_unsure), current == FeedbackVerdict.UNSURE, { onVerdict(FeedbackVerdict.UNSURE) }, icon = R.drawable.ic_help)
        }
        if (current != null) Text(stringResource(R.string.feedback_thanks), style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

/** Where the answer came from — shown on every AI result so farmers and agronomists can judge it. */
@Composable
fun AboutThisAnswer(engine: EngineInfo, sources: List<String>) {
    val c = Inama.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            InamaIcon(if (engine.mode == EngineMode.REMOTE) R.drawable.ic_sparkle else R.drawable.ic_wifioff, tint = c.muted, size = 18.dp)
            Text(
                if (engine.mode == EngineMode.REMOTE) stringResource(R.string.engine_remote, engine.model ?: engine.id)
                else stringResource(R.string.engine_on_device, engine.version ?: "1"),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
        }
        SourcesRow(sources)
        Text(stringResource(R.string.advice_disclaimer), style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

// =============================================================================== Expert case

@Composable
fun ExpertCaseScreen(caseId: String, onBack: () -> Unit, onHome: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: ExpertCaseViewModel = viewModel(key = "case_$caseId") { ExpertCaseViewModel(caseId, container.expertRepository, container.diagnosisRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    val case = state.case
    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenHeader(stringResource(R.string.case_title), onBack = onBack)
        when {
            state.loading -> LoadingState()
            case == null -> EmptyState(R.drawable.ic_headset, stringResource(R.string.case_missing_title), stringResource(R.string.case_missing_body), action = stringResource(R.string.action_back), onAction = onBack)
            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(R.drawable.ic_headset, background = c.sprout, tint = c.forest, size = 60.dp)
                        Column(Modifier.weight(1f)) {
                            Text(case.expertName, style = MaterialTheme.typography.titleLarge, color = c.ink)
                            Text(stringResource(R.string.case_expected, case.expectedReplyHours), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                        }
                    }
                }
                item {
                    InamaCard(spacing = 0.dp) {
                        val stepIndex = when (case.status) {
                            CaseStatus.SENT -> 0
                            CaseStatus.SEEN -> 1
                            CaseStatus.ANSWERED -> 2
                        }
                        TimelineStep(stringResource(R.string.case_step_sent), formatDateTime(case.createdAtMillis), done = true, last = false)
                        TimelineStep(stringResource(R.string.case_step_seen), null, done = stepIndex >= 1, last = false)
                        TimelineStep(stringResource(R.string.case_step_answered), case.repliedAtMillis?.let { formatDateTime(it) }, done = stepIndex >= 2, last = true)
                    }
                }
                if (case.replyText != null) {
                    item {
                        InamaCard(background = c.mint, border = null, spacing = 10.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                InamaIcon(R.drawable.ic_msg, tint = c.mintInk, size = 22.dp)
                                Text(stringResource(R.string.case_reply_from, case.expertName), style = MaterialTheme.typography.labelLarge, color = c.mintInk, modifier = Modifier.weight(1f))
                            }
                            Text(case.replyText, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                            ListenButton("case_${case.id}", case.replyText)
                        }
                    }
                } else {
                    item { Banner(stringResource(R.string.case_waiting), BannerKind.INFO) }
                    if (!case.remote) item { Text(stringResource(R.string.case_demo_note), style = MaterialTheme.typography.bodySmall, color = c.muted) }
                }
                state.diagnosis?.let { d ->
                    item {
                        InamaCard(padding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (d.imagePath != null) {
                                    AsyncImage(model = File(d.imagePath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                                } else CropTile(d.cropId, 56.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.case_your_check), style = MaterialTheme.typography.labelMedium, color = c.muted)
                                    Text(d.headline, style = MaterialTheme.typography.labelLarge, color = c.ink)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (case != null) {
            InamaButton(stringResource(R.string.action_back_home), onHome, kind = ButtonKind.SECONDARY, modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun TimelineStep(title: String, detail: String?, done: Boolean, last: Boolean) {
    val c = Inama.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (done) c.forest else c.line),
                contentAlignment = Alignment.Center,
            ) { if (done) InamaIcon(R.drawable.ic_check, tint = Color.White, size = 14.dp) }
            if (!last) Box(Modifier.size(width = 2.dp, height = 30.dp).background(if (done) c.forest else c.line))
        }
        Column(Modifier.padding(top = 2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (done) c.ink else c.muted)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}
