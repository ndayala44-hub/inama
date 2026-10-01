package rw.inama.app.data.remote

import rw.inama.app.data.json.Json
import rw.inama.app.data.mapper.ApiMappers
import rw.inama.app.domain.ai.AdvisoryEngine
import rw.inama.app.domain.ai.DiagnosisRequest
import rw.inama.app.domain.ai.EngineRejectedException
import rw.inama.app.domain.ai.EngineUnavailableException
import rw.inama.app.domain.ai.QuestionRequest
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.EngineInfo
import rw.inama.app.domain.model.EngineMode
import java.io.File
import java.util.UUID

/**
 * AI through the Inama server (Gemini vision/text, with the server's own knowledge-base fallback).
 * Reuses the AgriAI backend's Gemini integration; API keys never reach the phone.
 * Network problems become [EngineUnavailableException] so FallbackAdvisoryEngine can answer
 * on-device instead; user-facing rejections become [EngineRejectedException].
 */
class RemoteAdvisoryEngine(
    private val api: InamaApi,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AdvisoryEngine {

    override val info: EngineInfo = EngineInfo("inama-api", "v1", EngineMode.REMOTE)

    override suspend fun diagnose(request: DiagnosisRequest): Diagnosis = call {
        val photo = File(request.imagePath)
        val fields = buildMap {
            request.cropId?.let { put("cropId", it) }
            request.fieldId?.let { put("fieldId", it) }
            put("symptoms", Json.stringify(Json.of(request.symptoms)))
            request.features?.let { f ->
                put(
                    "features",
                    Json.stringify(
                        Json.obj(
                            "brownRatio" to f.brownRatio, "yellowRatio" to f.yellowRatio, "whiteRatio" to f.whiteRatio,
                            "greenRatio" to f.greenRatio, "brightness" to f.brightness, "sharpness" to f.sharpness,
                        ),
                    ),
                )
            }
        }
        val data = api.postPhoto("/diagnoses", photo, request.mimeType, fields)
        ApiMappers.diagnosis(data, localId = "dg_${UUID.randomUUID()}", imagePath = request.imagePath, nowMillis = clock())
    }

    override suspend fun ask(request: QuestionRequest): Answer = call {
        val body = Json.obj("question" to request.question, "cropId" to request.cropId)
        ApiMappers.answer(api.postJson("/advice/ask", body, auth = true), request.cropId, clock())
    }

    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: ApiUnavailableException) {
        throw EngineUnavailableException(e.message ?: "unavailable", e)
    } catch (e: ApiException) {
        // An expired session should not block the farmer: answer on-device instead.
        if (e.status == 401) throw EngineUnavailableException("session expired", e)
        throw EngineRejectedException(e.code, e.message ?: e.code)
    }
}
