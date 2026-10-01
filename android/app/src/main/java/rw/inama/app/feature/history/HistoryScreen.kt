package rw.inama.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import rw.inama.app.R
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.Outcome
import rw.inama.app.domain.repository.AnswerRepository
import rw.inama.app.domain.repository.DiagnosisRepository
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.components.Chip
import rw.inama.app.ui.components.ConfidenceMeter
import rw.inama.app.ui.components.CropTile
import rw.inama.app.ui.components.EmptyState
import rw.inama.app.ui.components.Eyebrow
import rw.inama.app.ui.components.IconTile
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.InamaIcon
import rw.inama.app.ui.components.LoadingState
import rw.inama.app.ui.components.ScreenHeader
import rw.inama.app.ui.components.Segmented
import rw.inama.app.ui.components.formatDate
import rw.inama.app.ui.components.relativeTime
import rw.inama.app.ui.theme.Inama
import java.io.File
import java.time.Instant
import java.time.ZoneId

enum class HistoryFilter { ALL, CHECKS, QUESTIONS }

/** One row in the combined history, newest first. */
sealed interface HistoryItem {
    val id: String
    val createdAtMillis: Long

    data class Check(val diagnosis: Diagnosis) : HistoryItem {
        override val id get() = diagnosis.id
        override val createdAtMillis get() = diagnosis.createdAtMillis
    }

    data class Question(val answer: Answer) : HistoryItem {
        override val id get() = answer.id
        override val createdAtMillis get() = answer.createdAtMillis
    }
}

data class HistoryUiState(val loading: Boolean = true, val filter: HistoryFilter = HistoryFilter.ALL, val items: List<HistoryItem> = emptyList()) {
    /** Items grouped by local day (epoch day), newest day first. */
    val byDay: List<Pair<Long, List<HistoryItem>>>
        get() = items.groupBy { Instant.ofEpochMilli(it.createdAtMillis).atZone(ZoneId.of("Africa/Kigali")).toLocalDate().toEpochDay() }
            .toList().sortedByDescending { it.first }
}

class HistoryViewModel(diagnoses: DiagnosisRepository, answers: AnswerRepository) : ViewModel() {
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val state: StateFlow<HistoryUiState> = combine(diagnoses.diagnoses, answers.answers, filter) { d, a, f ->
        val items = buildList {
            if (f != HistoryFilter.QUESTIONS) addAll(d.map { HistoryItem.Check(it) })
            if (f != HistoryFilter.CHECKS) addAll(a.map { HistoryItem.Question(it) })
        }.sortedByDescending { it.createdAtMillis }
        HistoryUiState(loading = false, filter = f, items = items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(f: HistoryFilter) {
        filter.value = f
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onDiagnosis: (String) -> Unit, onAnswer: (String) -> Unit, onScan: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: HistoryViewModel = viewModel { HistoryViewModel(container.diagnosisRepository, container.answerRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenHeader(stringResource(R.string.history_title), subtitle = stringResource(R.string.history_subtitle), onBack = onBack)
        Segmented(
            options = listOf(
                HistoryFilter.ALL to stringResource(R.string.history_all),
                HistoryFilter.CHECKS to stringResource(R.string.history_checks),
                HistoryFilter.QUESTIONS to stringResource(R.string.history_questions),
            ),
            selected = state.filter,
            onSelect = vm::setFilter,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        when {
            state.loading -> LoadingState()
            state.items.isEmpty() -> EmptyState(
                R.drawable.ic_records,
                stringResource(R.string.history_empty_title),
                stringResource(R.string.history_empty_body),
                Modifier.padding(horizontal = 20.dp),
                action = stringResource(R.string.home_check_plant),
                onAction = onScan,
            )
            else -> LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.byDay.forEach { (day, dayItems) ->
                    item(key = "day_$day") { Eyebrow(formatDate(day)) }
                    items(dayItems, key = { it.id }) { item ->
                        when (item) {
                            is HistoryItem.Check -> CheckRow(item.diagnosis) { onDiagnosis(item.id) }
                            is HistoryItem.Question -> QuestionRow(item.answer) { onAnswer(item.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(d: Diagnosis, onClick: () -> Unit) {
    val c = Inama.colors
    InamaCard(padding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (d.imagePath != null && File(d.imagePath).exists()) {
                AsyncImage(model = File(d.imagePath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
            } else {
                CropTile(d.cropId, 64.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    InamaIcon(R.drawable.ic_camera, tint = c.muted, size = 14.dp)
                    Text("${d.cropName} · ${relativeTime(d.createdAtMillis)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Text(d.headline, style = MaterialTheme.typography.labelLarge, color = c.ink, maxLines = 2)
                ConfidenceMeter(d.confidence, showReason = false)
                if (d.caseId != null) {
                    Chip(stringResource(R.string.history_sent_to_person), background = c.sproutSoft, foreground = c.forest, icon = R.drawable.ic_headset, height = 26.dp)
                } else if (d.outcome == Outcome.HEALTHY) {
                    Chip(stringResource(R.string.status_good), background = c.mint, foreground = c.mintInk, icon = R.drawable.ic_check, height = 26.dp)
                }
            }
            InamaIcon(R.drawable.ic_chev, tint = c.muted, size = 20.dp)
        }
    }
}

@Composable
private fun QuestionRow(a: Answer, onClick: () -> Unit) {
    val c = Inama.colors
    InamaCard(padding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(R.drawable.ic_mic, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.history_you_asked, relativeTime(a.createdAtMillis)), style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text(a.question, style = MaterialTheme.typography.labelLarge, color = c.ink, maxLines = 2)
                Text(a.headline, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1)
            }
            InamaIcon(R.drawable.ic_chev, tint = c.muted, size = 20.dp)
        }
    }
}
