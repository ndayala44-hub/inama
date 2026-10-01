package rw.inama.app.feature.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rw.inama.app.domain.ai.EngineRejectedException
import rw.inama.app.domain.ai.QuestionRequest
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.Crop
import rw.inama.app.domain.repository.AnswerRepository
import rw.inama.app.domain.repository.CatalogRepository
import rw.inama.app.domain.repository.FieldRepository
import rw.inama.app.domain.repository.SettingsRepository
import rw.inama.app.domain.repository.TaskRepository
import rw.inama.app.domain.usecase.AddAdviceToTasksUseCase
import rw.inama.app.domain.usecase.AskAdvisorUseCase

data class AskUiState(
    val input: String = "",
    val question: String? = null,
    val cropId: String? = null,
    val crops: List<Crop> = emptyList(),
    val thinking: Boolean = false,
    val answer: Answer? = null,
    val failed: Boolean = false,
    val recent: List<Answer> = emptyList(),
    val addedCount: Int? = null,
    val online: Boolean = true,
)

/** Voice/text questions. Speech is handled by the screen (platform SpeechRecognizer); this holds the conversation. */
class AskViewModel(
    private val askAdvisor: AskAdvisorUseCase,
    answers: AnswerRepository,
    fields: FieldRepository,
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
    private val tasks: TaskRepository,
    private val addToTasks: AddAdviceToTasksUseCase,
    online: StateFlow<Boolean>,
) : ViewModel() {

    private val local = MutableStateFlow(AskUiState())
    private var job: Job? = null

    val state: StateFlow<AskUiState> = combine(local, answers.answers, online) { l, recent, on ->
        l.copy(recent = recent.sortedByDescending { it.createdAtMillis }.take(5), online = on)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskUiState())

    init {
        viewModelScope.launch {
            val kb = catalog.knowledgeBase(settings.current().language.tag)
            val mine = fields.fields.first().map { it.cropId }.distinct()
            // The farmer's own crops first; otherwise the most common crops in the guide.
            val crops = if (mine.isNotEmpty()) mine.mapNotNull { kb.crop(it) } else kb.crops.take(5)
            local.update { it.copy(crops = crops) }
        }
    }

    fun onInput(text: String) = local.update { it.copy(input = text.take(500)) }

    fun selectCrop(cropId: String?) = local.update { it.copy(cropId = cropId) }

    fun send() = ask(local.value.input)

    fun ask(text: String) {
        val question = text.trim()
        if (question.length < 3 || local.value.thinking) return
        job?.cancel()
        local.update { it.copy(input = "", question = question, thinking = true, answer = null, failed = false, addedCount = null) }
        job = viewModelScope.launch {
            try {
                val answer = askAdvisor(QuestionRequest(question, local.value.cropId, settings.current().language.tag))
                local.update { it.copy(thinking = false, answer = answer) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: EngineRejectedException) {
                local.update { it.copy(thinking = false, failed = true) }
            } catch (e: Exception) {
                local.update { it.copy(thinking = false, failed = true) }
            }
        }
    }

    fun retry() {
        local.value.question?.let { ask(it) }
    }

    /** Shows a previous answer again (from the "Recent" list). */
    fun show(answer: Answer) = local.update { it.copy(question = answer.question, answer = answer, failed = false, thinking = false, addedCount = null) }

    fun startOver() {
        job?.cancel()
        local.update { it.copy(question = null, answer = null, failed = false, thinking = false, addedCount = null) }
    }

    fun addStepsToTasks() {
        val answer = local.value.answer ?: return
        viewModelScope.launch {
            val added = addToTasks.fromAnswer(answer, tasks.tasks.first())
            local.update { it.copy(addedCount = added) }
        }
    }
}

data class AnswerDetailUiState(val loading: Boolean = true, val answer: Answer? = null, val inTasks: Boolean = false, val addedCount: Int? = null)

class AnswerDetailViewModel(
    private val id: String,
    private val answers: AnswerRepository,
    private val tasks: TaskRepository,
    private val addToTasks: AddAdviceToTasksUseCase,
) : ViewModel() {
    private val added = MutableStateFlow<Int?>(null)

    val state: StateFlow<AnswerDetailUiState> = combine(answers.answers, tasks.tasks, added) { list, ts, n ->
        AnswerDetailUiState(loading = false, answer = list.firstOrNull { it.id == id }, inTasks = ts.any { it.sourceId == id }, addedCount = n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnswerDetailUiState())

    fun addStepsToTasks() {
        viewModelScope.launch {
            val a = answers.get(id) ?: return@launch
            added.value = addToTasks.fromAnswer(a, tasks.tasks.first())
        }
    }
}
