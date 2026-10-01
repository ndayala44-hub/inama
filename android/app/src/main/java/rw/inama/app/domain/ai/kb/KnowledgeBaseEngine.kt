package rw.inama.app.domain.ai.kb

import rw.inama.app.domain.ai.AdvisoryEngine
import rw.inama.app.domain.ai.DiagnosisRequest
import rw.inama.app.domain.ai.ImageFeatures
import rw.inama.app.domain.ai.QuestionRequest
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.Confidence
import rw.inama.app.domain.model.ConfidenceLevel
import rw.inama.app.domain.model.ConditionInfo
import rw.inama.app.domain.model.ConditionRef
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.EngineInfo
import rw.inama.app.domain.model.EngineMode
import rw.inama.app.domain.model.Escalation
import rw.inama.app.domain.model.EvidenceItem
import rw.inama.app.domain.model.EvidenceKind
import rw.inama.app.domain.model.KnowledgeBase
import rw.inama.app.domain.model.Outcome
import rw.inama.app.domain.model.PhotoProblem
import rw.inama.app.domain.model.PhotoQuality
import rw.inama.app.domain.model.QuestionEntry
import rw.inama.app.domain.model.Urgency
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * On-device advisory engine over the shared, reviewed knowledge base.
 *
 * This is a Kotlin port of server/src/v1/services/kbEngine.js + advice.js (which in turn extends
 * AgriAI's keyword fallback). Both implementations are verified against
 * shared/knowledge/engine_test_vectors.json — keep them in step.
 *
 * It is a transparent scorer, not a vision model: it combines the symptoms the farmer ticks with
 * colour measurements from the photo, and it refuses to guess (confidence LOW → a person checks)
 * when the evidence is weak or the photo is poor.
 */
class KnowledgeBaseEngine(
    private val knowledgeBase: suspend (language: String) -> KnowledgeBase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: (prefix: String) -> String = { prefix -> "${prefix}_${java.util.UUID.randomUUID()}" },
) : AdvisoryEngine {

    override val info: EngineInfo = EngineInfo(id = "inama-kb", version = "1.0.0", mode = EngineMode.ON_DEVICE)

    override suspend fun diagnose(request: DiagnosisRequest): Diagnosis {
        val kb = knowledgeBase(request.language)
        val assessment = assess(kb, request.cropId, request.symptoms, request.features)
        return buildDiagnosis(kb, assessment, request)
    }

    override suspend fun ask(request: QuestionRequest): Answer {
        val kb = knowledgeBase(request.language)
        val match = matchQuestion(kb, request.question, request.cropId)
        val src = match ?: kb.defaultAnswer
        return Answer(
            id = newId("an"),
            createdAtMillis = clock(),
            language = kb.language,
            question = request.question,
            cropId = request.cropId,
            headline = src.headline,
            confidence = Confidence(
                level = kb.engine.levelFor(src.confidence),
                score = src.confidence,
                reasonCode = if (match != null) "kb_match" else "general",
            ),
            urgency = src.urgency,
            whatsHappening = src.whatsHappening,
            whyItMatters = src.whyItMatters,
            steps = src.steps,
            followUp = src.followUp,
            relatedConditionId = match?.conditionId,
            sources = src.sources,
            engine = EngineInfo(kb.engine.id, kb.engine.version, EngineMode.ON_DEVICE),
        )
    }

    // ------------------------------------------------------------------ scoring (pure)

    data class Scored(val condition: ConditionInfo, val score: Double, val matched: List<String>, val visual: Double?)

    data class Assessment(
        val quality: PhotoQuality,
        val level: ConfidenceLevel,
        val score: Double,
        val reasonCode: String,
        val matchedCount: Int,
        val outcome: Outcome,
        /** Condition id, "healthy", or null when uncertain. */
        val conditionId: String?,
        val candidates: List<Scored>,
        val matchedSymptoms: List<String>,
        val visualHints: List<String>,
    )

    private class Levels(val brown: Double, val yellow: Double, val white: Double, val green: Double) {
        operator fun get(key: String): Double = when (key) {
            "brown" -> brown
            "yellow" -> yellow
            "white" -> white
            "green" -> green
            else -> 0.0
        }
    }

    fun assessQuality(kb: KnowledgeBase, features: ImageFeatures?): PhotoQuality {
        if (features == null) return PhotoQuality.UNKNOWN
        val e = kb.engine
        return when {
            features.sharpness < e.minSharpness -> PhotoQuality.BLURRY
            features.brightness < e.minBrightness -> PhotoQuality.DARK
            features.brightness > e.maxBrightness -> PhotoQuality.BRIGHT
            else -> PhotoQuality.OK
        }
    }

    private fun levels(kb: KnowledgeBase, f: ImageFeatures): Levels {
        val e = kb.engine
        fun clamp(x: Double) = max(0.0, min(1.0, x))
        return Levels(
            brown = clamp(f.brownRatio / e.scaleBrown),
            yellow = clamp(f.yellowRatio / e.scaleYellow),
            white = clamp(f.whiteRatio / e.scaleWhite),
            green = clamp(f.greenRatio / e.scaleGreen),
        )
    }

    private fun visualMatch(profile: Map<String, Double>, levels: Levels?): Double? {
        if (levels == null || profile.isEmpty()) return null
        var diff = 0.0
        for ((k, v) in profile) diff += abs(v - levels[k])
        return 1 - diff / profile.size
    }

    private fun specificity(symptomId: String, candidates: List<ConditionInfo>): Double {
        val n = candidates.count { (it.cues[symptomId] ?: 0.0) >= 0.5 }
        return if (n <= 1) 1.0 else 1.0 / n
    }

    private fun score(kb: KnowledgeBase, c: ConditionInfo, symptoms: List<String>, levels: Levels?, all: List<ConditionInfo>): Scored {
        val w = kb.engine
        val vis = visualMatch(c.visual, levels)
        // Colour alone is weak evidence: capped below "medium" so the farmer is asked what they see.
        if (symptoms.isEmpty()) return Scored(c, if (vis == null) 0.0 else w.photoOnlyFactor * vis, emptyList(), vis)
        val total = c.cues.values.sum().takeIf { it > 0 } ?: 1.0
        var support = 0.0
        var covered = 0.0
        val matched = mutableListOf<String>()
        for (s in symptoms) {
            val weight = c.cues[s] ?: 0.0
            support += weight
            covered += weight
            if (weight > 0) matched += s
        }
        support /= symptoms.size
        val coverage = covered / total
        var score = if (vis == null) {
            (w.weightSymptomMatch * support + w.weightCoverage * coverage) / (w.weightSymptomMatch + w.weightCoverage)
        } else {
            w.weightSymptomMatch * support + w.weightCoverage * coverage + w.weightVisual * vis
        }
        val spec = if (matched.isEmpty()) 1.0 else matched.sumOf { specificity(it, all) } / matched.size
        score *= 0.75 + 0.25 * spec
        return Scored(c, score, matched, vis)
    }

    fun assess(kb: KnowledgeBase, cropId: String?, symptoms: List<String>, features: ImageFeatures?): Assessment {
        val e = kb.engine
        val known = kb.symptoms.map { it.id }.toSet()
        val selected = symptoms.filter { it in known }.distinct()
        val candidates = kb.conditions.filter { cropId == null || it.cropId == cropId }
        val quality = assessQuality(kb, features)
        val lv = if (quality == PhotoQuality.UNKNOWN || features == null) null else levels(kb, features)

        val ranked = candidates.map { score(kb, it, selected, lv, candidates) }.sortedByDescending { it.score }
        val shortlist = ranked.filter { it.score > 0.2 }.take(3)
        val hints = if (lv == null) emptyList() else listOf("brown", "yellow", "white").filter { lv[it] >= 0.5 }

        fun uncertain(reason: String, keepCandidates: Boolean = true) = Assessment(
            quality, ConfidenceLevel.LOW, 0.0, reason, 0, Outcome.UNCERTAIN, null,
            if (keepCandidates) shortlist else emptyList(), emptyList(), hints,
        )

        when (quality) {
            PhotoQuality.BLURRY -> return uncertain("photo_blurry")
            PhotoQuality.DARK -> return uncertain("photo_dark")
            PhotoQuality.BRIGHT -> return uncertain("photo_bright")
            else -> Unit
        }
        if (selected.isEmpty() && lv == null) return uncertain("no_signs", keepCandidates = false)

        // "Looks healthy" needs a clean leaf: any real blemish means we ask instead of reassuring.
        val blemish = if (lv != null) maxOf(lv.brown, lv.yellow, lv.white) else 1.0
        val healthy = if (selected.isEmpty() && lv != null && blemish < e.healthyMaxBlemish) {
            min(e.healthyCap, lv.green * (1 - blemish))
        } else 0.0
        val topScore = ranked.firstOrNull()?.score ?: 0.0
        val secondScore = ranked.getOrNull(1)?.score ?: 0.0

        if (healthy > topScore) {
            var s = healthy
            var reason = "healthy_looking"
            if (healthy - topScore < e.ambiguityMargin) {
                s = min(s, e.ambiguousCap)
                reason = "ambiguous"
            }
            val level = e.levelFor(s)
            val isLow = level == ConfidenceLevel.LOW
            return Assessment(
                quality, level, s, reason, 0,
                if (isLow) Outcome.UNCERTAIN else Outcome.HEALTHY,
                if (isLow) null else "healthy",
                shortlist, emptyList(), hints,
            )
        }

        val top = ranked.first()
        var s = top.score
        var reason = when {
            selected.isEmpty() -> "photo_only"
            lv != null && top.visual != null -> "photo_and_signs"
            else -> "signs_matched"
        }
        if (top.score - secondScore < e.ambiguityMargin) {
            s = min(s, e.ambiguousCap)
            reason = "ambiguous"
        }
        val level = e.levelFor(s)
        val isLow = level == ConfidenceLevel.LOW
        return Assessment(
            quality, level, s, reason, top.matched.size,
            if (isLow) Outcome.UNCERTAIN else Outcome.CONDITION,
            if (isLow) null else top.condition.id,
            shortlist, top.matched, hints,
        )
    }

    fun matchQuestion(kb: KnowledgeBase, question: String, cropId: String?): QuestionEntry? {
        val text = " ${question.lowercase()} "
        var best: QuestionEntry? = null
        var bestScore = 0.0
        for (q in kb.questions) {
            var score = 0.0
            for (k in q.keywords) if (text.contains(k.lowercase())) score += 1.0
            if (score > 0 && q.cropId != null && cropId != null && q.cropId == cropId) score += 0.5
            if (score > bestScore) {
                best = q
                bestScore = score
            }
        }
        return if (bestScore >= 1.0) best else null
    }

    // ------------------------------------------------------------------ advice shaping (pure)

    fun buildDiagnosis(kb: KnowledgeBase, a: Assessment, request: DiagnosisRequest): Diagnosis {
        val crop = kb.crop(request.cropId)
        val cropName = crop?.name ?: "plant"
        val evidence = a.matchedSymptoms.map { EvidenceItem(EvidenceKind.SYMPTOM, it, kb.symptom(it)?.label ?: it) } +
            a.visualHints.map { EvidenceItem(EvidenceKind.PHOTO, it, "") }
        val confidence = Confidence(a.level, a.score, a.reasonCode, a.matchedCount)
        val photoProblem = if (a.quality.isProblem) {
            val text = kb.photoProblems.forQuality(a.quality)
            PhotoProblem(a.quality, text.title, text.body, kb.photoProblems.tips)
        } else null
        val candidateRefs = a.candidates.map { ConditionRef(it.condition.id, it.condition.name, it.condition.type) }
        val engine = EngineInfo(kb.engine.id, kb.engine.version, EngineMode.ON_DEVICE)

        fun base(
            outcome: Outcome,
            condition: ConditionRef?,
            headline: String,
            urgency: Urgency,
            happening: String,
            why: String,
            steps: List<String>,
            lookFor: String?,
            signs: List<String>,
            candidates: List<ConditionRef>,
            lessLikely: List<ConditionRef>,
            sources: List<String>,
            escalation: Escalation,
        ) = Diagnosis(
            id = newId("dg"),
            createdAtMillis = clock(),
            language = kb.language,
            cropId = request.cropId,
            cropName = cropName,
            fieldId = request.fieldId,
            outcome = outcome,
            condition = condition,
            headline = headline,
            confidence = confidence,
            urgency = urgency,
            whatsHappening = happening,
            whyItMatters = why,
            steps = steps,
            evidence = evidence,
            lookFor = lookFor,
            signs = signs,
            candidates = candidates,
            lessLikely = lessLikely,
            sources = sources,
            escalation = escalation,
            photoProblem = photoProblem,
            engine = engine,
            symptoms = request.symptoms,
            imagePath = request.imagePath,
        )

        return when (a.outcome) {
            Outcome.CONDITION -> {
                val c = kb.condition(a.conditionId)!!
                val urgentButUnsure = c.urgency == Urgency.TODAY && a.level != ConfidenceLevel.HIGH
                base(
                    Outcome.CONDITION, ConditionRef(c.id, c.name, c.type), c.headline, c.urgency,
                    c.whatsHappening, c.whyItMatters, c.steps, c.notSeenHint, c.signs,
                    emptyList(), candidateRefs.filter { it.id != c.id }.take(1), c.sources,
                    if (urgentButUnsure) Escalation(true, "urgent") else Escalation(false, null),
                )
            }
            Outcome.HEALTHY -> base(
                Outcome.HEALTHY, null, kb.healthy.headline.replace("{crop}", cropName.lowercase()), Urgency.WATCH,
                kb.healthy.whatsHappening, kb.healthy.whyItMatters, kb.healthy.steps, null, emptyList(),
                emptyList(), emptyList(), emptyList(), Escalation(false, null),
            )
            Outcome.UNCERTAIN -> base(
                Outcome.UNCERTAIN, null, kb.lowConfidence.headline, Urgency.WATCH,
                photoProblem?.body ?: kb.lowConfidence.whatsHappening, kb.lowConfidence.whyItMatters,
                kb.lowConfidence.steps, null, emptyList(), candidateRefs.take(2), emptyList(), emptyList(),
                Escalation(true, "low_confidence"),
            )
        }
    }
}
