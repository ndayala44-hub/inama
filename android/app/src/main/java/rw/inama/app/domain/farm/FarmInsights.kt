package rw.inama.app.domain.farm

import rw.inama.app.domain.model.Crop
import rw.inama.app.domain.model.CropStage
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.KnowledgeBase
import rw.inama.app.domain.model.Outcome
import rw.inama.app.domain.model.Urgency
import rw.inama.app.domain.model.Verdict
import rw.inama.app.domain.model.WeatherReport

data class CropProgress(
    val dayNumber: Int?,
    val stageIndex: Int,
    val stage: CropStage?,
    val fraction: Float,
    val daysToHarvest: Int?,
)

object CropStageCalculator {
    fun progress(crop: Crop?, field: Field, todayEpochDay: Long): CropProgress {
        val planted = field.plantedOnEpochDay
        if (crop == null || planted == null) return CropProgress(null, -1, null, 0f, null)
        val day = (todayEpochDay - planted).toInt().coerceAtLeast(0)
        val index = crop.stages.indexOfFirst { day in it.fromDay..it.toDay }.let { if (it < 0) crop.stages.lastIndex else it }
        val fraction = (day.toFloat() / crop.seasonDays).coerceIn(0f, 1f)
        return CropProgress(day, index, crop.stages.getOrNull(index), fraction, (crop.seasonDays - day).coerceAtLeast(0))
    }
}

/** What the Home screen leads with: one priority, chosen transparently. */
data class Priority(
    val kind: Kind,
    val title: String,
    val body: String,
    val urgency: Urgency,
    val fieldId: String? = null,
    val cropId: String? = null,
    val diagnosisId: String? = null,
    val conditionId: String? = null,
) {
    enum class Kind { DIAGNOSIS_FOLLOW_UP, WEATHER, CROP_STAGE, ALL_GOOD }
}

/**
 * Ranks candidate priorities by urgency: an unresolved recent diagnosis first, then weather that
 * blocks a job today, then crop-stage advice from the knowledge base. Text for WEATHER and
 * ALL_GOOD is produced by the UI from string resources (this returns codes in [title]).
 */
object PriorityPicker {
    private fun rank(u: Urgency) = when (u) {
        Urgency.TODAY -> 0
        Urgency.THIS_WEEK -> 1
        Urgency.WATCH -> 2
        Urgency.PLAN -> 3
    }

    fun pick(
        kb: KnowledgeBase,
        fields: List<Field>,
        diagnoses: List<Diagnosis>,
        tasks: List<FarmTask>,
        weather: WeatherReport?,
        todayEpochDay: Long,
        nowMillis: Long,
    ): Priority {
        val candidates = mutableListOf<Priority>()
        val weekMillis = 7L * 24 * 3600 * 1000

        diagnoses.filter { it.outcome == Outcome.CONDITION && nowMillis - it.createdAtMillis < weekMillis }
            .filter { d -> tasks.none { it.sourceId == d.id && it.done } }
            .filter { it.urgency == Urgency.TODAY || it.urgency == Urgency.THIS_WEEK }
            .maxByOrNull { it.createdAtMillis }
            ?.let { d ->
                candidates += Priority(
                    Priority.Kind.DIAGNOSIS_FOLLOW_UP, d.headline, d.steps.firstOrNull() ?: d.whatsHappening, d.urgency,
                    fieldId = d.fieldId, cropId = d.cropId, diagnosisId = d.id, conditionId = d.condition?.id,
                )
            }

        weather?.decisions?.firstOrNull { it.verdict == Verdict.WAIT && it.reasonCode == "spray_rain" }?.let {
            candidates += Priority(Priority.Kind.WEATHER, "weather:${it.reasonCode}", "", Urgency.THIS_WEEK)
        }

        for (field in fields) {
            val crop = kb.crop(field.cropId) ?: continue
            val day = field.plantedOnEpochDay?.let { (todayEpochDay - it).toInt() } ?: continue
            crop.stageTips.filter { day in it.fromDay..it.toDay }.forEach { tip ->
                candidates += Priority(Priority.Kind.CROP_STAGE, tip.title, tip.body, tip.urgency, field.id, crop.id, conditionId = tip.conditionId)
            }
        }

        return candidates.minByOrNull { rank(it.urgency) }
            ?: Priority(Priority.Kind.ALL_GOOD, "all_good", "", Urgency.PLAN)
    }
}
