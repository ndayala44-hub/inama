package rw.inama.app.data.mapper

import rw.inama.app.data.json.JsonObject
import rw.inama.app.domain.model.AdviceTemplate
import rw.inama.app.domain.model.ConditionInfo
import rw.inama.app.domain.model.Crop
import rw.inama.app.domain.model.CropStage
import rw.inama.app.domain.model.District
import rw.inama.app.domain.model.EngineParams
import rw.inama.app.domain.model.KnowledgeBase
import rw.inama.app.domain.model.Lesson
import rw.inama.app.domain.model.LessonStep
import rw.inama.app.domain.model.PhotoProblemText
import rw.inama.app.domain.model.PhotoProblemTexts
import rw.inama.app.domain.model.QuestionEntry
import rw.inama.app.domain.model.StageTip
import rw.inama.app.domain.model.Symptom
import rw.inama.app.domain.model.Urgency

/** Parses the shared content files (shared/knowledge, shared/content) bundled as app assets. */
object ContentParsers {

    fun knowledgeBase(root: JsonObject): KnowledgeBase {
        val e = root.obj("engine") ?: error("advisory KB has no engine section")
        val thresholds = e.obj("thresholds")
        val weights = e.obj("weights")
        val scale = e.obj("visualScale")
        val quality = e.obj("quality")
        val engine = EngineParams(
            id = e.string("id", "inama-kb"),
            version = e.string("version", "1"),
            highThreshold = thresholds?.double("high", 0.75) ?: 0.75,
            mediumThreshold = thresholds?.double("medium", 0.5) ?: 0.5,
            ambiguityMargin = e.double("ambiguityMargin", 0.08),
            ambiguousCap = e.double("ambiguousCap", 0.6),
            photoOnlyFactor = e.double("photoOnlyFactor", 0.45),
            healthyCap = e.double("healthyCap", 0.7),
            healthyMaxBlemish = e.double("healthyMaxBlemish", 0.35),
            weightSymptomMatch = weights?.double("symptomMatch", 0.55) ?: 0.55,
            weightCoverage = weights?.double("coverage", 0.25) ?: 0.25,
            weightVisual = weights?.double("visual", 0.2) ?: 0.2,
            scaleBrown = scale?.double("brown", 0.15) ?: 0.15,
            scaleYellow = scale?.double("yellow", 0.2) ?: 0.2,
            scaleWhite = scale?.double("white", 0.1) ?: 0.1,
            scaleGreen = scale?.double("green", 0.6) ?: 0.6,
            minSharpness = quality?.double("minSharpness", 0.12) ?: 0.12,
            minBrightness = quality?.double("minBrightness", 0.15) ?: 0.15,
            maxBrightness = quality?.double("maxBrightness", 0.92) ?: 0.92,
        )
        val photo = root.obj("photoProblems")
        fun problem(key: String) = photo?.obj(key).let { PhotoProblemText(it?.string("title") ?: "", it?.string("body") ?: "") }
        return KnowledgeBase(
            schemaVersion = root.int("schemaVersion", 1),
            language = root.string("language", "en"),
            reviewStatus = root.string("reviewStatus", "draft"),
            reviewNote = root.string("reviewNote"),
            engine = engine,
            symptoms = root.objects("symptoms").map { Symptom(it.string("id"), it.string("label")) },
            crops = root.objects("crops").map(::crop),
            conditions = root.objects("conditions").map(::condition),
            lowConfidence = template(root.obj("lowConfidence")),
            healthy = template(root.obj("healthy")),
            photoProblems = PhotoProblemTexts(problem("blurry"), problem("dark"), problem("bright"), photo?.strings("tips") ?: emptyList()),
            questions = root.objects("questions").map(::question),
            defaultAnswer = question(root.obj("defaultAnswer") ?: JsonObject(emptyMap())),
        )
    }

    private fun crop(o: JsonObject) = Crop(
        id = o.string("id"),
        name = o.string("name"),
        seasonDays = o.int("seasonDays", 120),
        stages = o.objects("stages").map { CropStage(it.string("id"), it.string("name"), it.int("fromDay"), it.int("toDay")) },
        stageTips = o.objects("stageTips").map {
            StageTip(it.int("fromDay"), it.int("toDay"), Urgency.fromWire(it.stringOrNull("urgency")), it.string("title"), it.string("body"), it.stringOrNull("conditionId"))
        },
    )

    private fun condition(o: JsonObject) = ConditionInfo(
        id = o.string("id"),
        cropId = o.string("cropId"),
        name = o.string("name"),
        type = o.string("type", "disease"),
        headline = o.string("headline"),
        cues = o.doubleMap("cues"),
        visual = o.doubleMap("visual"),
        signs = o.strings("signs"),
        notSeenHint = o.stringOrNull("notSeenHint"),
        urgency = Urgency.fromWire(o.stringOrNull("urgency")),
        whatsHappening = o.string("whatsHappening"),
        whyItMatters = o.string("whyItMatters"),
        steps = o.strings("steps"),
        sources = o.strings("sources"),
    )

    private fun template(o: JsonObject?) = AdviceTemplate(
        headline = o?.string("headline") ?: "",
        whatsHappening = o?.string("whatsHappening") ?: "",
        whyItMatters = o?.string("whyItMatters") ?: "",
        steps = o?.strings("steps") ?: emptyList(),
    )

    private fun question(o: JsonObject) = QuestionEntry(
        id = o.string("id", "default"),
        keywords = o.strings("keywords"),
        cropId = o.stringOrNull("cropId"),
        conditionId = o.stringOrNull("conditionId"),
        headline = o.string("headline"),
        urgency = Urgency.fromWire(o.stringOrNull("urgency")),
        confidence = o.double("confidence", 0.4),
        whatsHappening = o.string("whatsHappening"),
        whyItMatters = o.string("whyItMatters"),
        steps = o.strings("steps"),
        followUp = o.stringOrNull("followUp"),
        sources = o.strings("sources"),
    )

    fun lessons(root: JsonObject): List<Lesson> = root.objects("lessons").map { o ->
        Lesson(
            id = o.string("id"),
            title = o.string("title"),
            topic = o.string("topic"),
            cropId = o.stringOrNull("cropId"),
            minutes = o.int("minutes", 3),
            summary = o.string("summary"),
            steps = o.objects("steps").map { LessonStep(it.string("icon"), it.string("text")) },
        )
    }

    fun districts(root: JsonObject): List<District> = root.objects("districts").map {
        District(it.string("id"), it.string("province"), it.string("name"), it.double("lat"), it.double("lon"))
    }
}
