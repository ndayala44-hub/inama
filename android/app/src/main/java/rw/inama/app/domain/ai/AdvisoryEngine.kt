package rw.inama.app.domain.ai

import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.EngineInfo

/**
 * The single seam between the app and any AI. The UI and use cases only know this interface.
 *
 * Implementations in this MVP:
 *  - RemoteAdvisoryEngine (data layer): Inama API → Gemini vision/text, with the server's own
 *    knowledge-base fallback. Credentials stay on the server.
 *  - KnowledgeBaseEngine (below): on-device, rule-based engine over the shared reviewed KB.
 *    Works offline and when no server is configured.
 *
 * A future on-device TFLite model or a Kinyarwanda-capable model is another implementation;
 * [DiagnosisRequest.language] / [QuestionRequest.language] already carry the language tag.
 */
interface AdvisoryEngine {
    val info: EngineInfo
    suspend fun diagnose(request: DiagnosisRequest): Diagnosis
    suspend fun ask(request: QuestionRequest): Answer
}

/** Colour and quality measurements computed on the phone from the photo (see PhotoAnalyzer). */
data class ImageFeatures(
    val brownRatio: Double,
    val yellowRatio: Double,
    val whiteRatio: Double,
    val greenRatio: Double,
    val brightness: Double,
    val sharpness: Double,
)

data class DiagnosisRequest(
    /** Absolute path of the (already compressed) JPEG on this phone. */
    val imagePath: String,
    val mimeType: String = "image/jpeg",
    val cropId: String?,
    val fieldId: String?,
    val symptoms: List<String>,
    val features: ImageFeatures?,
    val language: String,
)

data class QuestionRequest(
    val question: String,
    val cropId: String?,
    val language: String,
)

/** Thrown when an engine cannot be reached (offline, server down, timeout) → try the fallback. */
class EngineUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown when an engine refuses the input (bad photo type, too large…) → show the error. */
class EngineRejectedException(val code: String, message: String) : Exception(message)

/** Stages reported while a photo is analysed; drives the loading screen. */
enum class AnalysisStage { RECEIVED, CHECKING_PHOTO, LOOKING_FOR_SIGNS, MATCHING_GUIDE, DONE }
