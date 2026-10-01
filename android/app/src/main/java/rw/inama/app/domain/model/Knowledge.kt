package rw.inama.app.domain.model

// The reviewed advisory knowledge base (shared/knowledge/advisory_kb.<lang>.json), as domain types.
// One instance per language — Kinyarwanda content arrives as a new JSON file, not new code.

data class KnowledgeBase(
    val schemaVersion: Int,
    val language: String,
    val reviewStatus: String,
    val reviewNote: String,
    val engine: EngineParams,
    val symptoms: List<Symptom>,
    val crops: List<Crop>,
    val conditions: List<ConditionInfo>,
    val lowConfidence: AdviceTemplate,
    val healthy: AdviceTemplate,
    val photoProblems: PhotoProblemTexts,
    val questions: List<QuestionEntry>,
    val defaultAnswer: QuestionEntry,
) {
    private val cropIndex by lazy { crops.associateBy { it.id } }
    private val conditionIndex by lazy { conditions.associateBy { it.id } }
    private val symptomIndex by lazy { symptoms.associateBy { it.id } }

    fun crop(id: String?): Crop? = id?.let { cropIndex[it] }
    fun condition(id: String?): ConditionInfo? = id?.let { conditionIndex[it] }
    fun symptom(id: String?): Symptom? = id?.let { symptomIndex[it] }

    /** Symptoms worth offering for a crop: every cue used by that crop's conditions, in KB order. */
    fun symptomsFor(cropId: String?): List<Symptom> {
        if (cropId == null) return symptoms
        val used = conditions.filter { it.cropId == cropId }.flatMap { it.cues.keys }.toSet()
        return symptoms.filter { it.id in used }
    }

    fun conditionsFor(cropId: String): List<ConditionInfo> = conditions.filter { it.cropId == cropId }
}

data class EngineParams(
    val id: String,
    val version: String,
    val highThreshold: Double,
    val mediumThreshold: Double,
    val ambiguityMargin: Double,
    val ambiguousCap: Double,
    /** Colour-only evidence is multiplied by this (kept below "medium" on purpose). */
    val photoOnlyFactor: Double,
    val healthyCap: Double,
    /** Above this colour-blemish level a photo is never called healthy. */
    val healthyMaxBlemish: Double,
    val weightSymptomMatch: Double,
    val weightCoverage: Double,
    val weightVisual: Double,
    val scaleBrown: Double,
    val scaleYellow: Double,
    val scaleWhite: Double,
    val scaleGreen: Double,
    val minSharpness: Double,
    val minBrightness: Double,
    val maxBrightness: Double,
) {
    fun levelFor(score: Double): ConfidenceLevel = when {
        score >= highThreshold -> ConfidenceLevel.HIGH
        score >= mediumThreshold -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.LOW
    }
}

data class Symptom(val id: String, val label: String)

data class CropStage(val id: String, val name: String, val fromDay: Int, val toDay: Int)

data class StageTip(
    val fromDay: Int,
    val toDay: Int,
    val urgency: Urgency,
    val title: String,
    val body: String,
    val conditionId: String?,
)

data class Crop(
    val id: String,
    val name: String,
    val seasonDays: Int,
    val stages: List<CropStage>,
    val stageTips: List<StageTip>,
)

data class ConditionInfo(
    val id: String,
    val cropId: String,
    val name: String,
    val type: String,
    val headline: String,
    val cues: Map<String, Double>,
    val visual: Map<String, Double>,
    val signs: List<String>,
    val notSeenHint: String?,
    val urgency: Urgency,
    val whatsHappening: String,
    val whyItMatters: String,
    val steps: List<String>,
    val sources: List<String>,
)

data class AdviceTemplate(
    val headline: String,
    val whatsHappening: String,
    val whyItMatters: String,
    val steps: List<String>,
)

data class PhotoProblemText(val title: String, val body: String)

data class PhotoProblemTexts(
    val blurry: PhotoProblemText,
    val dark: PhotoProblemText,
    val bright: PhotoProblemText,
    val tips: List<String>,
) {
    fun forQuality(quality: PhotoQuality): PhotoProblemText = when (quality) {
        PhotoQuality.DARK -> dark
        PhotoQuality.BRIGHT -> bright
        else -> blurry
    }
}

data class QuestionEntry(
    val id: String,
    val keywords: List<String>,
    val cropId: String?,
    val conditionId: String?,
    val headline: String,
    val urgency: Urgency,
    val confidence: Double,
    val whatsHappening: String,
    val whyItMatters: String,
    val steps: List<String>,
    val followUp: String?,
    val sources: List<String>,
)

data class Lesson(
    val id: String,
    val title: String,
    val topic: String,
    val cropId: String?,
    val minutes: Int,
    val summary: String,
    val steps: List<LessonStep>,
)

data class LessonStep(val icon: String, val text: String)

data class District(val id: String, val province: String, val name: String, val lat: Double, val lon: Double)
