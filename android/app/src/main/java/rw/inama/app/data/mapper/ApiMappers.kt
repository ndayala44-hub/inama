package rw.inama.app.data.mapper

import rw.inama.app.data.json.JsonObject
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.CaseStatus
import rw.inama.app.domain.model.Confidence
import rw.inama.app.domain.model.ConfidenceLevel
import rw.inama.app.domain.model.ConditionRef
import rw.inama.app.domain.model.CurrentWeather
import rw.inama.app.domain.model.DayForecast
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.EngineInfo
import rw.inama.app.domain.model.EngineMode
import rw.inama.app.domain.model.Escalation
import rw.inama.app.domain.model.EvidenceItem
import rw.inama.app.domain.model.EvidenceKind
import rw.inama.app.domain.model.ExpertCase
import rw.inama.app.domain.model.FarmActivity
import rw.inama.app.domain.model.FarmDecision
import rw.inama.app.domain.model.Outcome
import rw.inama.app.domain.model.OtpChallenge
import rw.inama.app.domain.model.PhotoProblem
import rw.inama.app.domain.model.PhotoQuality
import rw.inama.app.domain.model.Session
import rw.inama.app.domain.model.Urgency
import rw.inama.app.domain.model.Verdict
import rw.inama.app.domain.model.WeatherAlert
import rw.inama.app.domain.model.WeatherCondition
import rw.inama.app.domain.model.WeatherReport
import rw.inama.app.domain.model.WeatherSource
import java.time.Instant

/**
 * Inama API (server/src/v1) JSON → domain. Field names follow docs/API.md.
 * Verified against real responses from the server in ApiMappersTest.
 */
object ApiMappers {

    fun isoToMillis(iso: String?, fallback: Long): Long =
        try {
            if (iso.isNullOrBlank()) fallback else Instant.parse(iso).toEpochMilli()
        } catch (e: Exception) {
            fallback
        }

    fun confidence(o: JsonObject?): Confidence {
        val level = when (o?.stringOrNull("level")) {
            "high" -> ConfidenceLevel.HIGH
            "medium" -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
        val code = o?.stringOrNull("reasonCode") ?: "general"
        val reason = o?.stringOrNull("reason")
        return Confidence(
            level = level,
            score = o?.double("score") ?: 0.0,
            reasonCode = code,
            reasonCount = o?.int("count") ?: 0,
            reasonText = if (code == "model") reason else null,
        )
    }

    fun engine(o: JsonObject?): EngineInfo = EngineInfo(
        id = o?.stringOrNull("id") ?: "inama-api",
        version = o?.stringOrNull("version"),
        mode = EngineMode.REMOTE,
        model = o?.stringOrNull("model"),
    )

    private fun ref(o: JsonObject) = ConditionRef(o.stringOrNull("id"), o.string("name"), o.string("type", "unknown"))

    fun diagnosis(o: JsonObject, localId: String, imagePath: String?, nowMillis: Long): Diagnosis {
        val crop = o.obj("crop")
        val evidence = o.obj("evidence")
        val photo = o.obj("photoProblem")
        val escalation = o.obj("escalation")
        return Diagnosis(
            id = localId,
            createdAtMillis = isoToMillis(o.stringOrNull("createdAt"), nowMillis),
            language = o.string("language", "en"),
            cropId = crop?.stringOrNull("id"),
            cropName = crop?.string("name", "plant") ?: "plant",
            fieldId = o.stringOrNull("fieldId"),
            outcome = Outcome.fromWire(o.stringOrNull("outcome")),
            condition = o.obj("condition")?.let(::ref),
            headline = o.string("headline"),
            confidence = confidence(o.obj("confidence")),
            urgency = Urgency.fromWire(o.stringOrNull("urgency")),
            whatsHappening = o.string("whatsHappening"),
            whyItMatters = o.string("whyItMatters"),
            steps = o.strings("steps"),
            evidence = evidence?.objects("matched")?.map {
                val kind = when (it.stringOrNull("kind")) {
                    "symptom" -> EvidenceKind.SYMPTOM
                    "photo" -> EvidenceKind.PHOTO
                    else -> EvidenceKind.MODEL
                }
                EvidenceItem(kind, it.stringOrNull("id"), it.string("label"))
            } ?: emptyList(),
            lookFor = evidence?.stringOrNull("lookFor"),
            signs = evidence?.strings("signs") ?: emptyList(),
            candidates = o.objects("candidates").map(::ref),
            lessLikely = o.objects("lessLikely").map(::ref),
            sources = o.strings("sources"),
            escalation = Escalation(escalation?.bool("recommended") ?: false, escalation?.stringOrNull("reasonCode")),
            photoProblem = photo?.let {
                PhotoProblem(PhotoQuality.fromWire(it.stringOrNull("code")), it.string("title"), it.string("body"), it.strings("tips"))
            },
            engine = engine(o.obj("engine")),
            symptoms = o.strings("symptoms"),
            imagePath = imagePath,
            remoteId = o.stringOrNull("id"),
        )
    }

    fun answer(o: JsonObject, cropId: String?, nowMillis: Long): Answer = Answer(
        id = o.string("id").ifEmpty { "an_$nowMillis" },
        createdAtMillis = isoToMillis(o.stringOrNull("createdAt"), nowMillis),
        language = o.string("language", "en"),
        question = o.string("question"),
        cropId = cropId,
        headline = o.string("headline"),
        confidence = confidence(o.obj("confidence")),
        urgency = Urgency.fromWire(o.stringOrNull("urgency")),
        whatsHappening = o.string("whatsHappening"),
        whyItMatters = o.string("whyItMatters"),
        steps = o.strings("steps"),
        followUp = o.stringOrNull("followUp"),
        relatedConditionId = o.stringOrNull("relatedConditionId"),
        sources = o.strings("sources"),
        engine = engine(o.obj("engine")),
    )

    fun weather(o: JsonObject, nowMillis: Long): WeatherReport {
        val cur = o.obj("current")
        val days = o.objects("days").map {
            DayForecast(
                date = it.string("date"),
                highC = it.int("highC"),
                lowC = it.int("lowC"),
                rainChance = it.int("rainChance"),
                rainMm = it.int("rainMm"),
                condition = WeatherCondition.fromWire(it.stringOrNull("condition")),
            )
        }
        return WeatherReport(
            place = o.string("place"),
            current = CurrentWeather(
                tempC = cur?.int("tempC") ?: 0,
                windKph = cur?.int("windKph") ?: 0,
                humidity = cur?.int("humidity") ?: 0,
                condition = WeatherCondition.fromWire(cur?.stringOrNull("condition")),
                rainChance = cur?.int("rainChance") ?: 0,
            ),
            days = days,
            decisions = o.objects("decisions").mapNotNull { d ->
                FarmActivity.fromWire(d.stringOrNull("activity"))?.let { FarmDecision(it, Verdict.fromWire(d.stringOrNull("verdict")), d.string("reasonCode")) }
            },
            alerts = o.objects("alerts").map { WeatherAlert(it.string("code"), it.stringOrNull("date")) },
            source = if (o.string("source") == "demo") WeatherSource.DEMO else WeatherSource.LIVE,
            updatedAtMillis = isoToMillis(o.stringOrNull("updatedAt"), nowMillis),
        )
    }

    fun expertCase(o: JsonObject, localDiagnosisId: String, nowMillis: Long): ExpertCase {
        val reply = o.obj("reply")
        return ExpertCase(
            id = o.string("id"),
            diagnosisId = localDiagnosisId,
            status = when (o.stringOrNull("status")) {
                "answered" -> CaseStatus.ANSWERED
                "seen" -> CaseStatus.SEEN
                else -> CaseStatus.SENT
            },
            expertName = o.obj("expert")?.string("name", "Farmer promoter") ?: "Farmer promoter",
            expectedReplyHours = o.int("expectedReplyHours", 3),
            createdAtMillis = isoToMillis(o.stringOrNull("createdAt"), nowMillis),
            replyText = reply?.stringOrNull("text"),
            repliedAtMillis = reply?.stringOrNull("at")?.let { isoToMillis(it, nowMillis) },
            remote = true,
        )
    }

    fun otpChallenge(o: JsonObject, phone: String): OtpChallenge = OtpChallenge(
        phone = phone,
        maskedPhone = o.string("phone", phone),
        expiresInSeconds = o.int("expiresInSeconds", 300),
        devCode = o.stringOrNull("devCode"),
    )

    fun session(o: JsonObject): Session {
        val farmer = o.obj("farmer")
        return Session(
            farmerId = farmer?.string("id") ?: "",
            phone = farmer?.string("phone") ?: "",
            token = o.stringOrNull("token"),
            remote = true,
        )
    }
}
