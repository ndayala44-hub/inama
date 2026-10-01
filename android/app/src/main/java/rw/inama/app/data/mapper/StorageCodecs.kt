package rw.inama.app.data.mapper

import rw.inama.app.data.json.Json
import rw.inama.app.data.json.JsonObject
import rw.inama.app.domain.model.AiMode
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.AppLanguage
import rw.inama.app.domain.model.AppSettings
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
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.Farmer
import rw.inama.app.domain.model.FeedbackVerdict
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.LandType
import rw.inama.app.domain.model.Outcome
import rw.inama.app.domain.model.PhotoProblem
import rw.inama.app.domain.model.PhotoQuality
import rw.inama.app.domain.model.Session
import rw.inama.app.domain.model.SpeechRate
import rw.inama.app.domain.model.TaskSource
import rw.inama.app.domain.model.TextScale
import rw.inama.app.domain.model.Urgency
import rw.inama.app.domain.model.Verdict
import rw.inama.app.domain.model.WeatherAlert
import rw.inama.app.domain.model.WeatherCondition
import rw.inama.app.domain.model.WeatherReport
import rw.inama.app.domain.model.WeatherSource

/**
 * On-phone storage format (JSON files in app-private storage). Every record carries a schema
 * version ("v") so later versions can migrate old files.
 */
object StorageCodecs {
    const val VERSION = 1

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    // ---------------------------------------------------------------- shared pieces
    private fun confidenceToJson(c: Confidence) = Json.obj(
        "level" to c.level, "score" to c.score, "reasonCode" to c.reasonCode, "reasonCount" to c.reasonCount, "reasonText" to c.reasonText,
    )

    private fun confidenceFrom(o: JsonObject?) = Confidence(
        level = enumOf(o?.stringOrNull("level"), ConfidenceLevel.LOW),
        score = o?.double("score") ?: 0.0,
        reasonCode = o?.string("reasonCode", "general") ?: "general",
        reasonCount = o?.int("reasonCount") ?: 0,
        reasonText = o?.stringOrNull("reasonText"),
    )

    private fun engineToJson(e: EngineInfo) = Json.obj("id" to e.id, "version" to e.version, "mode" to e.mode, "model" to e.model)
    private fun engineFrom(o: JsonObject?) = EngineInfo(
        id = o?.string("id", "unknown") ?: "unknown",
        version = o?.stringOrNull("version"),
        mode = enumOf(o?.stringOrNull("mode"), EngineMode.ON_DEVICE),
        model = o?.stringOrNull("model"),
    )

    private fun refToJson(r: ConditionRef) = Json.obj("id" to r.id, "name" to r.name, "type" to r.type)
    private fun refFrom(o: JsonObject) = ConditionRef(o.stringOrNull("id"), o.string("name"), o.string("type", "unknown"))

    // ---------------------------------------------------------------- diagnosis
    fun diagnosisToJson(d: Diagnosis): JsonObject = Json.obj(
        "v" to VERSION,
        "id" to d.id,
        "createdAtMillis" to d.createdAtMillis,
        "language" to d.language,
        "cropId" to d.cropId,
        "cropName" to d.cropName,
        "fieldId" to d.fieldId,
        "outcome" to d.outcome,
        "condition" to d.condition?.let(::refToJson),
        "headline" to d.headline,
        "confidence" to confidenceToJson(d.confidence),
        "urgency" to d.urgency,
        "whatsHappening" to d.whatsHappening,
        "whyItMatters" to d.whyItMatters,
        "steps" to d.steps,
        "evidence" to d.evidence.map { Json.obj("kind" to it.kind, "id" to it.id, "label" to it.label) },
        "lookFor" to d.lookFor,
        "signs" to d.signs,
        "candidates" to d.candidates.map(::refToJson),
        "lessLikely" to d.lessLikely.map(::refToJson),
        "sources" to d.sources,
        "escalation" to Json.obj("recommended" to d.escalation.recommended, "reasonCode" to d.escalation.reasonCode),
        "photoProblem" to d.photoProblem?.let { Json.obj("quality" to it.quality, "title" to it.title, "body" to it.body, "tips" to it.tips) },
        "engine" to engineToJson(d.engine),
        "symptoms" to d.symptoms,
        "imagePath" to d.imagePath,
        "feedback" to d.feedback,
        "caseId" to d.caseId,
        "remoteId" to d.remoteId,
    )

    fun diagnosisFrom(o: JsonObject): Diagnosis {
        val esc = o.obj("escalation")
        return Diagnosis(
            id = o.string("id"),
            createdAtMillis = o.long("createdAtMillis"),
            language = o.string("language", "en"),
            cropId = o.stringOrNull("cropId"),
            cropName = o.string("cropName", "plant"),
            fieldId = o.stringOrNull("fieldId"),
            outcome = enumOf(o.stringOrNull("outcome"), Outcome.UNCERTAIN),
            condition = o.obj("condition")?.let(::refFrom),
            headline = o.string("headline"),
            confidence = confidenceFrom(o.obj("confidence")),
            urgency = enumOf(o.stringOrNull("urgency"), Urgency.WATCH),
            whatsHappening = o.string("whatsHappening"),
            whyItMatters = o.string("whyItMatters"),
            steps = o.strings("steps"),
            evidence = o.objects("evidence").map { EvidenceItem(enumOf(it.stringOrNull("kind"), EvidenceKind.MODEL), it.stringOrNull("id"), it.string("label")) },
            lookFor = o.stringOrNull("lookFor"),
            signs = o.strings("signs"),
            candidates = o.objects("candidates").map(::refFrom),
            lessLikely = o.objects("lessLikely").map(::refFrom),
            sources = o.strings("sources"),
            escalation = Escalation(esc?.bool("recommended") ?: false, esc?.stringOrNull("reasonCode")),
            photoProblem = o.obj("photoProblem")?.let {
                PhotoProblem(enumOf(it.stringOrNull("quality"), PhotoQuality.BLURRY), it.string("title"), it.string("body"), it.strings("tips"))
            },
            engine = engineFrom(o.obj("engine")),
            symptoms = o.strings("symptoms"),
            imagePath = o.stringOrNull("imagePath"),
            feedback = o.stringOrNull("feedback")?.let { name -> FeedbackVerdict.entries.firstOrNull { it.name == name } },
            caseId = o.stringOrNull("caseId"),
            remoteId = o.stringOrNull("remoteId"),
        )
    }

    // ---------------------------------------------------------------- answer
    fun answerToJson(a: Answer): JsonObject = Json.obj(
        "v" to VERSION, "id" to a.id, "createdAtMillis" to a.createdAtMillis, "language" to a.language, "question" to a.question,
        "cropId" to a.cropId, "headline" to a.headline, "confidence" to confidenceToJson(a.confidence), "urgency" to a.urgency,
        "whatsHappening" to a.whatsHappening, "whyItMatters" to a.whyItMatters, "steps" to a.steps, "followUp" to a.followUp,
        "relatedConditionId" to a.relatedConditionId, "sources" to a.sources, "engine" to engineToJson(a.engine),
    )

    fun answerFrom(o: JsonObject) = Answer(
        id = o.string("id"),
        createdAtMillis = o.long("createdAtMillis"),
        language = o.string("language", "en"),
        question = o.string("question"),
        cropId = o.stringOrNull("cropId"),
        headline = o.string("headline"),
        confidence = confidenceFrom(o.obj("confidence")),
        urgency = enumOf(o.stringOrNull("urgency"), Urgency.WATCH),
        whatsHappening = o.string("whatsHappening"),
        whyItMatters = o.string("whyItMatters"),
        steps = o.strings("steps"),
        followUp = o.stringOrNull("followUp"),
        relatedConditionId = o.stringOrNull("relatedConditionId"),
        sources = o.strings("sources"),
        engine = engineFrom(o.obj("engine")),
    )

    // ---------------------------------------------------------------- farm
    fun farmerToJson(f: Farmer) = Json.obj(
        "v" to VERSION, "id" to f.id, "phone" to f.phone, "name" to f.name, "districtId" to f.districtId, "sector" to f.sector,
        "keepsCrops" to f.keepsCrops, "keepsAnimals" to f.keepsAnimals, "cooperative" to f.cooperative,
    )

    fun farmerFrom(o: JsonObject) = Farmer(
        id = o.string("id"), phone = o.string("phone"), name = o.string("name"), districtId = o.stringOrNull("districtId"),
        sector = o.string("sector"), keepsCrops = o.bool("keepsCrops", true), keepsAnimals = o.bool("keepsAnimals"),
        cooperative = o.stringOrNull("cooperative"),
    )

    fun fieldToJson(f: Field) = Json.obj(
        "v" to VERSION, "id" to f.id, "name" to f.name, "areaAres" to f.areaAres, "cropId" to f.cropId,
        "plantedOnEpochDay" to f.plantedOnEpochDay, "landType" to f.landType, "createdAtMillis" to f.createdAtMillis,
    )

    fun fieldFrom(o: JsonObject) = Field(
        id = o.string("id"), name = o.string("name"), areaAres = o.int("areaAres", 10), cropId = o.string("cropId"),
        plantedOnEpochDay = o.longOrNull("plantedOnEpochDay"), landType = enumOf(o.stringOrNull("landType"), LandType.HILLSIDE),
        createdAtMillis = o.long("createdAtMillis"),
    )

    fun taskToJson(t: FarmTask) = Json.obj(
        "v" to VERSION, "id" to t.id, "title" to t.title, "fieldId" to t.fieldId, "cropId" to t.cropId, "urgency" to t.urgency,
        "source" to t.source, "sourceId" to t.sourceId, "done" to t.done, "createdAtMillis" to t.createdAtMillis, "doneAtMillis" to t.doneAtMillis,
    )

    fun taskFrom(o: JsonObject) = FarmTask(
        id = o.string("id"), title = o.string("title"), fieldId = o.stringOrNull("fieldId"), cropId = o.stringOrNull("cropId"),
        urgency = enumOf(o.stringOrNull("urgency"), Urgency.PLAN), source = enumOf(o.stringOrNull("source"), TaskSource.MANUAL),
        sourceId = o.stringOrNull("sourceId"), done = o.bool("done"), createdAtMillis = o.long("createdAtMillis"),
        doneAtMillis = o.longOrNull("doneAtMillis"),
    )

    fun caseToJson(c: ExpertCase) = Json.obj(
        "v" to VERSION, "id" to c.id, "diagnosisId" to c.diagnosisId, "status" to c.status, "expertName" to c.expertName,
        "expectedReplyHours" to c.expectedReplyHours, "createdAtMillis" to c.createdAtMillis, "replyText" to c.replyText,
        "repliedAtMillis" to c.repliedAtMillis, "remote" to c.remote,
    )

    fun caseFrom(o: JsonObject) = ExpertCase(
        id = o.string("id"), diagnosisId = o.string("diagnosisId"), status = enumOf(o.stringOrNull("status"), CaseStatus.SENT),
        expertName = o.string("expertName", "Farmer promoter"), expectedReplyHours = o.int("expectedReplyHours", 3),
        createdAtMillis = o.long("createdAtMillis"), replyText = o.stringOrNull("replyText"), repliedAtMillis = o.longOrNull("repliedAtMillis"),
        remote = o.bool("remote"),
    )

    fun sessionToJson(s: Session) = Json.obj("v" to VERSION, "farmerId" to s.farmerId, "phone" to s.phone, "token" to s.token, "remote" to s.remote)
    fun sessionFrom(o: JsonObject) = Session(o.string("farmerId"), o.string("phone"), o.stringOrNull("token"), o.bool("remote"))

    // ---------------------------------------------------------------- weather cache
    fun weatherToJson(w: WeatherReport) = Json.obj(
        "v" to VERSION, "place" to w.place,
        "current" to Json.obj("tempC" to w.current.tempC, "windKph" to w.current.windKph, "humidity" to w.current.humidity, "condition" to w.current.condition, "rainChance" to w.current.rainChance),
        "days" to w.days.map { Json.obj("date" to it.date, "highC" to it.highC, "lowC" to it.lowC, "rainChance" to it.rainChance, "rainMm" to it.rainMm, "condition" to it.condition) },
        "decisions" to w.decisions.map { Json.obj("activity" to it.activity, "verdict" to it.verdict, "reasonCode" to it.reasonCode) },
        "alerts" to w.alerts.map { Json.obj("code" to it.code, "date" to it.date) },
        "source" to w.source, "updatedAtMillis" to w.updatedAtMillis,
    )

    fun weatherFrom(o: JsonObject): WeatherReport {
        val c = o.obj("current")
        return WeatherReport(
            place = o.string("place"),
            current = CurrentWeather(c?.int("tempC") ?: 0, c?.int("windKph") ?: 0, c?.int("humidity") ?: 0, enumOf(c?.stringOrNull("condition"), WeatherCondition.PARTLY_CLOUDY), c?.int("rainChance") ?: 0),
            days = o.objects("days").map { DayForecast(it.string("date"), it.int("highC"), it.int("lowC"), it.int("rainChance"), it.int("rainMm"), enumOf(it.stringOrNull("condition"), WeatherCondition.PARTLY_CLOUDY)) },
            decisions = o.objects("decisions").map { FarmDecision(enumOf(it.stringOrNull("activity"), FarmActivity.SPRAYING), enumOf(it.stringOrNull("verdict"), Verdict.CAREFUL), it.string("reasonCode")) },
            alerts = o.objects("alerts").map { WeatherAlert(it.string("code"), it.stringOrNull("date")) },
            source = enumOf(o.stringOrNull("source"), WeatherSource.CACHED),
            updatedAtMillis = o.long("updatedAtMillis"),
        )
    }

    // ---------------------------------------------------------------- settings
    fun settingsToJson(s: AppSettings) = Json.obj(
        "v" to VERSION, "language" to s.language.tag, "readAloud" to s.readAloud, "speechRate" to s.speechRate, "textScale" to s.textScale,
        "highContrast" to s.highContrast, "lowDataMode" to s.lowDataMode, "aiMode" to s.aiMode, "serverUrlOverride" to s.serverUrlOverride,
        "onboardingComplete" to s.onboardingComplete, "shareAnonymously" to s.shareAnonymously, "promoterCanSeeFields" to s.promoterCanSeeFields,
        "smsCopies" to s.smsCopies,
    )

    fun settingsFrom(o: JsonObject): AppSettings {
        val d = AppSettings()
        return AppSettings(
            language = AppLanguage.fromTag(o.stringOrNull("language")),
            readAloud = o.bool("readAloud", d.readAloud),
            speechRate = enumOf(o.stringOrNull("speechRate"), d.speechRate),
            textScale = enumOf(o.stringOrNull("textScale"), d.textScale),
            highContrast = o.bool("highContrast", d.highContrast),
            lowDataMode = o.bool("lowDataMode", d.lowDataMode),
            aiMode = enumOf(o.stringOrNull("aiMode"), AiMode.AUTO),
            serverUrlOverride = o.stringOrNull("serverUrlOverride"),
            onboardingComplete = o.bool("onboardingComplete", d.onboardingComplete),
            shareAnonymously = o.bool("shareAnonymously", d.shareAnonymously),
            promoterCanSeeFields = o.bool("promoterCanSeeFields", d.promoterCanSeeFields),
            smsCopies = o.bool("smsCopies", d.smsCopies),
        )
    }
}
