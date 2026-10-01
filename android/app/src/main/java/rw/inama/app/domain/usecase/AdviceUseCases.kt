package rw.inama.app.domain.usecase

import rw.inama.app.domain.ai.AdvisoryEngine
import rw.inama.app.domain.ai.AnalysisStage
import rw.inama.app.domain.ai.DiagnosisRequest
import rw.inama.app.domain.ai.QuestionRequest
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.Outcome
import rw.inama.app.domain.model.TaskSource
import rw.inama.app.domain.model.Urgency
import rw.inama.app.domain.repository.AnswerRepository
import rw.inama.app.domain.repository.DiagnosisRepository
import rw.inama.app.domain.repository.TaskRepository

/** Photo check: runs the AI engine, reports progress, and stores the result in history. */
class DiagnoseCropUseCase(
    private val engine: AdvisoryEngine,
    private val diagnoses: DiagnosisRepository,
) {
    suspend operator fun invoke(request: DiagnosisRequest, onStage: (AnalysisStage) -> Unit = {}): Diagnosis {
        onStage(AnalysisStage.RECEIVED)
        onStage(AnalysisStage.CHECKING_PHOTO)
        onStage(AnalysisStage.LOOKING_FOR_SIGNS)
        val result = engine.diagnose(request)
        onStage(AnalysisStage.MATCHING_GUIDE)
        val stored = if (result.imagePath == null) result.copy(imagePath = request.imagePath) else result
        diagnoses.save(stored)
        onStage(AnalysisStage.DONE)
        return stored
    }
}

/** Voice or text question → structured answer, stored in history. */
class AskAdvisorUseCase(
    private val engine: AdvisoryEngine,
    private val answers: AnswerRepository,
) {
    suspend operator fun invoke(request: QuestionRequest): Answer {
        val answer = engine.ask(request)
        answers.save(answer)
        return answer
    }
}

/**
 * "Add these steps to my tasks". Uncertain diagnoses only produce the safe interim steps
 * (they come from the low-confidence template), so no treatment task is created on a guess.
 * Re-adding the same advice does not create duplicates.
 */
class AddAdviceToTasksUseCase(
    private val tasks: TaskRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: (String) -> String = { "${it}_${java.util.UUID.randomUUID()}" },
) {
    suspend fun fromDiagnosis(d: Diagnosis, existing: List<FarmTask>): Int {
        val urgency = if (d.outcome == Outcome.UNCERTAIN) Urgency.WATCH else d.urgency
        return add(d.steps, d.id, d.fieldId, d.cropId, urgency, existing)
    }

    suspend fun fromAnswer(a: Answer, existing: List<FarmTask>): Int =
        add(a.steps, a.id, null, a.cropId, a.urgency, existing)

    suspend fun manual(title: String, fieldId: String?, existing: List<FarmTask>): Int {
        val clean = title.trim()
        if (clean.isEmpty()) return 0
        return add(listOf(clean), null, fieldId, null, Urgency.PLAN, existing, TaskSource.MANUAL)
    }

    private suspend fun add(
        steps: List<String>,
        sourceId: String?,
        fieldId: String?,
        cropId: String?,
        urgency: Urgency,
        existing: List<FarmTask>,
        source: TaskSource = TaskSource.ADVICE,
    ): Int {
        val already = existing.filter { sourceId != null && it.sourceId == sourceId }.map { it.title }.toSet()
        val now = clock()
        val fresh = steps.filter { it.isNotBlank() && it !in already }.mapIndexed { i, step ->
            FarmTask(
                id = newId("task"),
                title = step,
                fieldId = fieldId,
                cropId = cropId,
                urgency = urgency,
                source = source,
                sourceId = sourceId,
                done = false,
                createdAtMillis = now + i,
            )
        }
        if (fresh.isNotEmpty()) tasks.upsertAll(fresh)
        return fresh.size
    }
}
