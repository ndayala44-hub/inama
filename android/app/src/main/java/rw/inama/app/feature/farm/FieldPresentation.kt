package rw.inama.app.feature.farm

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import rw.inama.app.R
import rw.inama.app.domain.farm.CropStageCalculator
import rw.inama.app.domain.model.Diagnosis
import rw.inama.app.domain.model.Field
import rw.inama.app.domain.model.KnowledgeBase
import rw.inama.app.domain.model.Outcome
import rw.inama.app.domain.model.Urgency
import rw.inama.app.ui.components.FieldStatus
import java.time.LocalDate
import java.time.ZoneId

object FarmClock {
    val zone: ZoneId = ZoneId.of("Africa/Kigali")
    fun today(): LocalDate = LocalDate.now(zone)
    fun todayEpochDay(): Long = today().toEpochDay()
}

/** Rwanda's agricultural seasons: A (Sep–Jan), B (Feb–Jun), C (Jul–Aug, mostly marshland). */
data class Season(val letter: String, val week: Int)

fun currentSeason(today: LocalDate = FarmClock.today()): Season {
    val (letter, start) = when (today.monthValue) {
        9, 10, 11, 12 -> "A" to LocalDate.of(today.year, 9, 1)
        1 -> "A" to LocalDate.of(today.year - 1, 9, 1)
        in 2..6 -> "B" to LocalDate.of(today.year, 2, 1)
        else -> "C" to LocalDate.of(today.year, 7, 1)
    }
    val week = ((today.toEpochDay() - start.toEpochDay()) / 7 + 1).toInt()
    return Season(letter, week)
}

fun fieldStatus(field: Field, diagnoses: List<Diagnosis>, kb: KnowledgeBase?, todayEpochDay: Long): FieldStatus {
    if (field.plantedOnEpochDay == null) return FieldStatus.NOT_PLANTED
    val fortnightAgo = System.currentTimeMillis() - 14L * 24 * 3600 * 1000
    val recent = diagnoses.filter { it.fieldId == field.id && it.createdAtMillis > fortnightAgo }
    if (recent.any { it.outcome == Outcome.CONDITION && (it.urgency == Urgency.TODAY || it.urgency == Urgency.THIS_WEEK) }) return FieldStatus.SICK
    val crop = kb?.crop(field.cropId)
    val day = (todayEpochDay - field.plantedOnEpochDay).toInt()
    val tipDue = crop?.stageTips?.any { day in it.fromDay..it.toDay && (it.urgency == Urgency.TODAY || it.urgency == Urgency.THIS_WEEK) } == true
    return if (tipDue && recent.isEmpty()) FieldStatus.CHECK else FieldStatus.GOOD
}

@Composable
fun fieldSubtitle(field: Field, kb: KnowledgeBase?, todayEpochDay: Long): String {
    val cropName = kb?.crop(field.cropId)?.name ?: field.cropId
    val planted = field.plantedOnEpochDay
    return if (planted == null) {
        stringResource(R.string.field_subtitle_not_planted, cropName, field.areaAres)
    } else {
        val days = (todayEpochDay - planted).toInt().coerceAtLeast(0)
        if (days < 14) stringResource(R.string.field_subtitle_days, cropName, days, field.areaAres)
        else if (days < 70) stringResource(R.string.field_subtitle_weeks, cropName, days / 7, field.areaAres)
        else stringResource(R.string.field_subtitle_months, cropName, days / 30, field.areaAres)
    }
}

fun fieldProgress(field: Field, kb: KnowledgeBase?, todayEpochDay: Long): Float =
    CropStageCalculator.progress(kb?.crop(field.cropId), field, todayEpochDay).fraction
