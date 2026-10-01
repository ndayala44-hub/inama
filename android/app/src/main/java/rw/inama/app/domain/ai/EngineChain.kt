package rw.inama.app.domain.ai

import rw.inama.app.domain.model.AiMode
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.EngineInfo

/**
 * Tries [primary]; if it is unreachable, answers with [fallback] instead. This ports AgriAI's
 * client pattern ("call the API, and if the network fails use an offline answer") into a
 * reusable engine so every feature gets the same behaviour.
 *
 * Only [EngineUnavailableException] triggers the fallback. Rejections (e.g. unsupported photo)
 * are real answers and are passed on to the caller.
 */
class FallbackAdvisoryEngine(
    private val primary: AdvisoryEngine,
    private val fallback: AdvisoryEngine,
) : AdvisoryEngine {
    override val info: EngineInfo get() = primary.info

    override suspend fun diagnose(request: DiagnosisRequest): Diagnosis = try {
        primary.diagnose(request)
    } catch (e: EngineUnavailableException) {
        fallback.diagnose(request)
    }

    override suspend fun ask(request: QuestionRequest): Answer = try {
        primary.ask(request)
    } catch (e: EngineUnavailableException) {
        fallback.ask(request)
    }
}

/** Picks the engine for each call from the current settings (Settings › How Inama answers). */
class SwitchingAdvisoryEngine(
    private val mode: suspend () -> AiMode,
    private val auto: AdvisoryEngine,
    private val onDevice: AdvisoryEngine,
) : AdvisoryEngine {
    override val info: EngineInfo get() = auto.info

    private suspend fun current(): AdvisoryEngine = if (mode() == AiMode.ON_DEVICE_ONLY) onDevice else auto

    override suspend fun diagnose(request: DiagnosisRequest): Diagnosis = current().diagnose(request)
    override suspend fun ask(request: QuestionRequest): Answer = current().ask(request)
}
