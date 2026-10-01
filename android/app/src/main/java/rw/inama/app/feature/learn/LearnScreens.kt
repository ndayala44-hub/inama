package rw.inama.app.feature.learn

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rw.inama.app.R
import rw.inama.app.domain.model.Lesson
import rw.inama.app.domain.repository.CatalogRepository
import rw.inama.app.domain.repository.SettingsRepository
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.components.Chip
import rw.inama.app.ui.components.CropTile
import rw.inama.app.ui.components.EmptyState
import rw.inama.app.ui.components.ErrorState
import rw.inama.app.ui.components.IconTile
import rw.inama.app.ui.components.InamaButton
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.InamaIcon
import rw.inama.app.ui.components.ListenButton
import rw.inama.app.ui.components.LoadingState
import rw.inama.app.ui.components.PillChoice
import rw.inama.app.ui.components.ScreenHeader
import rw.inama.app.ui.components.lessonIcon
import rw.inama.app.ui.theme.Inama

data class LearnUiState(val loading: Boolean = true, val failed: Boolean = false, val lessons: List<Lesson> = emptyList(), val topic: String? = null) {
    val topics: List<String> get() = lessons.map { it.topic }.distinct()
    val visible: List<Lesson> get() = if (topic == null) lessons else lessons.filter { it.topic == topic }
}

/** Short, reviewed lessons from shared/content/lessons.<lang>.json (bundled, work offline). */
class LearnViewModel(private val catalog: CatalogRepository, private val settings: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(LearnUiState())
    val state: StateFlow<LearnUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val lessons = runCatching { catalog.lessons(settings.current().language.tag) }.getOrNull()
            _state.update { it.copy(loading = false, failed = lessons == null, lessons = lessons.orEmpty()) }
        }
    }

    fun selectTopic(topic: String?) = _state.update { it.copy(topic = topic) }
}

@Composable
fun LearnScreen(onBack: () -> Unit, onLesson: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: LearnViewModel = viewModel { LearnViewModel(container.catalogRepository, container.settingsRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenHeader(stringResource(R.string.learn_title), subtitle = stringResource(R.string.learn_subtitle), onBack = onBack)
        when {
            state.loading -> LoadingState()
            state.failed -> ErrorState(stringResource(R.string.learn_failed_title), stringResource(R.string.learn_failed_body), retry = vm::load)
            state.lessons.isEmpty() -> EmptyState(R.drawable.ic_book, stringResource(R.string.learn_empty_title), stringResource(R.string.learn_empty_body))
            else -> LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { PillChoice(stringResource(R.string.learn_all), state.topic == null, { vm.selectTopic(null) }) }
                        items(state.topics) { t -> PillChoice(t, state.topic == t, { vm.selectTopic(t) }) }
                    }
                }
                items(state.visible, key = { it.id }) { lesson ->
                    InamaCard(padding = 14.dp, onClick = { onLesson(lesson.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (lesson.cropId != null) CropTile(lesson.cropId, 60.dp) else IconTile(lessonIcon(lesson.steps.firstOrNull()?.icon ?: "leaf"), size = 60.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(lesson.title, style = MaterialTheme.typography.titleSmall, color = c.ink)
                                Text(lesson.summary, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 2)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip(lesson.topic, background = c.sand, foreground = c.ink, height = 26.dp)
                                    Chip(stringResource(R.string.learn_minutes, lesson.minutes), background = c.sand, foreground = c.muted, icon = R.drawable.ic_clock, height = 26.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LessonScreen(id: String, onBack: () -> Unit, onAsk: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: LearnViewModel = viewModel(key = "lesson_$id") { LearnViewModel(container.catalogRepository, container.settingsRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    val lesson = state.lessons.firstOrNull { it.id == id }
    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenHeader(lesson?.topic ?: stringResource(R.string.learn_title), subtitle = lesson?.let { stringResource(R.string.learn_minutes, it.minutes) }, onBack = onBack)
        when {
            state.loading -> LoadingState()
            lesson == null -> EmptyState(R.drawable.ic_book, stringResource(R.string.lesson_missing_title), stringResource(R.string.lesson_missing_body), action = stringResource(R.string.action_back), onAction = onBack)
            else -> {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (lesson.cropId != null) CropTile(lesson.cropId, 72.dp)
                            Text(lesson.title, style = MaterialTheme.typography.headlineMedium, color = c.ink)
                            Text(lesson.summary, style = MaterialTheme.typography.bodyLarge, color = c.muted)
                            ListenButton("lesson_${lesson.id}", lesson.title + ". " + lesson.steps.joinToString(". ") { it.text })
                        }
                    }
                    itemsIndexed(lesson.steps) { index, step ->
                        InamaCard(padding = 14.dp) {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    IconTile(lessonIcon(step.icon), size = 48.dp)
                                    Box(Modifier.size(20.dp).clip(CircleShape).background(c.forest), contentAlignment = Alignment.Center) {
                                        Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = c.white)
                                    }
                                }
                                Text(step.text, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InamaIcon(R.drawable.ic_shield, tint = c.muted, size = 16.dp)
                            Text(stringResource(R.string.lesson_reviewed_note), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                }
                InamaButton(stringResource(R.string.lesson_ask), onAsk, icon = R.drawable.ic_mic, modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }
    }
}
