package rw.inama.app.domain.model

// Domain models for AI advice. Pure Kotlin — no Android or serialization dependencies — so the
// same types are produced by the remote (Gemini via Inama API) and on-device engines, and can be
// unit-tested on the JVM. User-facing short phrases are NOT stored here: the UI renders them from
// codes (reasonCode, photo hint ids, verdict codes) through Android string resources, which is
// what makes adding Kinyarwanda/French a resource-only change.

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

/** Wire values: today | this_week | watch | plan. */
enum class Urgency(val wire: String) {
    TODAY("today"), THIS_WEEK("this_week"), WATCH("watch"), PLAN("plan");

    companion object {
        fun fromWire(value: String?): Urgency = entries.firstOrNull { it.wire == value } ?: WATCH
    }
}

enum class Outcome(val wire: String) {
    CONDITION("condition"), HEALTHY("healthy"), UNCERTAIN("uncertain");

    companion object {
        fun fromWire(value: String?): Outcome = entries.firstOrNull { it.wire == value } ?: UNCERTAIN
    }
}

enum class PhotoQuality(val wire: String) {
    OK("ok"), BLURRY("blurry"), DARK("dark"), BRIGHT("bright"), NOT_A_PLANT("not_a_plant"), UNKNOWN("unknown");

    val isProblem: Boolean get() = this == BLURRY || this == DARK || this == BRIGHT || this == NOT_A_PLANT

    companion object {
        fun fromWire(value: String?): PhotoQuality = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

enum class EngineMode { REMOTE, ON_DEVICE }

/**
 * Confidence is always shown in words. [reasonCode] mirrors the server codes:
 * signs_matched, photo_and_signs, photo_only, ambiguous, photo_blurry, photo_dark, photo_bright,
 * not_a_plant, no_signs, healthy_looking, kb_match, general, model.
 */
data class Confidence(
    val level: ConfidenceLevel,
    val score: Double,
    val reasonCode: String,
    val reasonCount: Int = 0,
    /** Free text only when a model explains itself (reasonCode == "model"). */
    val reasonText: String? = null,
)

enum class EvidenceKind { SYMPTOM, PHOTO, MODEL }

/** For PHOTO evidence, [id] is brown | yellow | white and the UI supplies the label. */
data class EvidenceItem(val kind: EvidenceKind, val id: String?, val label: String)

data class ConditionRef(val id: String?, val name: String, val type: String = "unknown")

data class PhotoProblem(val quality: PhotoQuality, val title: String, val body: String, val tips: List<String>)

data class Escalation(val recommended: Boolean, val reasonCode: String?)

data class EngineInfo(val id: String, val version: String?, val mode: EngineMode, val model: String? = null)

enum class FeedbackVerdict(val wire: String) { CORRECT("correct"), WRONG("wrong"), UNSURE("unsure") }

data class Diagnosis(
    val id: String,
    val createdAtMillis: Long,
    val language: String,
    val cropId: String?,
    val cropName: String,
    val fieldId: String?,
    val outcome: Outcome,
    val condition: ConditionRef?,
    val headline: String,
    val confidence: Confidence,
    val urgency: Urgency,
    val whatsHappening: String,
    val whyItMatters: String,
    val steps: List<String>,
    val evidence: List<EvidenceItem>,
    val lookFor: String?,
    val signs: List<String>,
    val candidates: List<ConditionRef>,
    val lessLikely: List<ConditionRef>,
    val sources: List<String>,
    val escalation: Escalation,
    val photoProblem: PhotoProblem?,
    val engine: EngineInfo,
    val symptoms: List<String>,
    /** Local file path of the photo on this phone (never uploaded again after analysis). */
    val imagePath: String? = null,
    val feedback: FeedbackVerdict? = null,
    val caseId: String? = null,
    /** Id on the Inama server when the check ran remotely; used for feedback/escalation. */
    val remoteId: String? = null,
)

data class Answer(
    val id: String,
    val createdAtMillis: Long,
    val language: String,
    val question: String,
    val cropId: String?,
    val headline: String,
    val confidence: Confidence,
    val urgency: Urgency,
    val whatsHappening: String,
    val whyItMatters: String,
    val steps: List<String>,
    val followUp: String?,
    val relatedConditionId: String?,
    val sources: List<String>,
    val engine: EngineInfo,
)

enum class CaseStatus { SENT, SEEN, ANSWERED }

data class ExpertCase(
    val id: String,
    val diagnosisId: String,
    val status: CaseStatus,
    val expertName: String,
    val expectedReplyHours: Int,
    val createdAtMillis: Long,
    val replyText: String? = null,
    val repliedAtMillis: Long? = null,
    val remote: Boolean = false,
)
