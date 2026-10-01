package rw.inama.app.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import rw.inama.app.R
import rw.inama.app.domain.model.Confidence
import rw.inama.app.domain.model.ConfidenceLevel
import rw.inama.app.domain.model.FarmActivity
import rw.inama.app.domain.model.Urgency
import rw.inama.app.domain.model.Verdict
import rw.inama.app.domain.model.WeatherCondition
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Maps domain codes to localised text. This is the bridge that keeps the domain language-neutral:
// translating Inama means translating res/values-xx/strings.xml, not touching this code.

@StringRes
fun urgencyLabel(u: Urgency): Int = when (u) {
    Urgency.TODAY -> R.string.urgency_today
    Urgency.THIS_WEEK -> R.string.urgency_this_week
    Urgency.WATCH -> R.string.urgency_watch
    Urgency.PLAN -> R.string.urgency_plan
}

@StringRes
fun confidenceLevelLabel(level: ConfidenceLevel): Int = when (level) {
    ConfidenceLevel.HIGH -> R.string.confidence_high
    ConfidenceLevel.MEDIUM -> R.string.confidence_medium
    ConfidenceLevel.LOW -> R.string.confidence_low
}

@Composable
fun confidenceReason(c: Confidence): String? = when (c.reasonCode) {
    "signs_matched" -> stringResource(R.string.reason_signs_matched, c.reasonCount)
    "photo_and_signs" -> stringResource(R.string.reason_photo_and_signs, c.reasonCount)
    "photo_only" -> stringResource(R.string.reason_photo_only)
    "ambiguous" -> stringResource(R.string.reason_ambiguous)
    "photo_blurry" -> stringResource(R.string.reason_photo_blurry)
    "photo_dark" -> stringResource(R.string.reason_photo_dark)
    "photo_bright" -> stringResource(R.string.reason_photo_bright)
    "not_a_plant" -> stringResource(R.string.reason_not_a_plant)
    "no_signs" -> stringResource(R.string.reason_no_signs)
    "healthy_looking" -> stringResource(R.string.reason_healthy_looking)
    "kb_match" -> stringResource(R.string.reason_kb_match)
    "general" -> stringResource(R.string.reason_general)
    "model" -> c.reasonText?.takeIf { it.isNotBlank() }
    else -> null
}

@StringRes
fun photoHintLabel(id: String?): Int = when (id) {
    "brown" -> R.string.photo_hint_brown
    "yellow" -> R.string.photo_hint_yellow
    else -> R.string.photo_hint_white
}

@StringRes
fun activityLabel(a: FarmActivity): Int = when (a) {
    FarmActivity.SPRAYING -> R.string.activity_spraying
    FarmActivity.WEEDING -> R.string.activity_weeding
    FarmActivity.PLANTING -> R.string.activity_planting
    FarmActivity.DRYING -> R.string.activity_drying
}

@DrawableRes
fun activityIcon(a: FarmActivity): Int = when (a) {
    FarmActivity.SPRAYING -> R.drawable.ic_spray
    FarmActivity.WEEDING -> R.drawable.ic_hoe
    FarmActivity.PLANTING -> R.drawable.ic_spade
    FarmActivity.DRYING -> R.drawable.ic_sun
}

@StringRes
fun verdictLabel(v: Verdict): Int = when (v) {
    Verdict.GO -> R.string.verdict_go
    Verdict.CAREFUL -> R.string.verdict_careful
    Verdict.WAIT -> R.string.verdict_wait
}

@StringRes
fun decisionReason(code: String): Int = when (code) {
    "spray_rain" -> R.string.decision_spray_rain
    "spray_wind" -> R.string.decision_spray_wind
    "spray_go" -> R.string.decision_spray_go
    "weed_wet" -> R.string.decision_weed_wet
    "weed_go" -> R.string.decision_weed_go
    "plant_rain" -> R.string.decision_plant_rain
    "plant_dry" -> R.string.decision_plant_dry
    "plant_mixed" -> R.string.decision_plant_mixed
    "dry_rain" -> R.string.decision_dry_rain
    else -> R.string.decision_dry_go
}

@StringRes
fun conditionLabel(c: WeatherCondition): Int = when (c) {
    WeatherCondition.SUNNY -> R.string.weather_sunny
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.STORM -> R.string.weather_storm
}

@DrawableRes
fun conditionIcon(c: WeatherCondition): Int = when (c) {
    WeatherCondition.SUNNY -> R.drawable.ic_sun
    WeatherCondition.PARTLY_CLOUDY -> R.drawable.ic_partsun
    WeatherCondition.RAIN -> R.drawable.ic_rain
    WeatherCondition.STORM -> R.drawable.ic_storm
}

/** "Today", "Thu"… in the app language. */
@Composable
fun dayLabel(isoDate: String, locale: Locale = Locale.getDefault()): String {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
    val today = LocalDate.now(java.time.ZoneId.of("Africa/Kigali"))
    return when (date) {
        today -> stringResource(R.string.day_today)
        today.plusDays(1) -> stringResource(R.string.day_tomorrow)
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    }
}

/** Quantity string ("1 week ago" / "3 weeks ago") that follows the app language. */
@Composable
fun pluralText(@androidx.annotation.PluralsRes id: Int, count: Int, vararg args: Any): String {
    androidx.compose.ui.platform.LocalConfiguration.current // re-read when the language changes
    return androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(id, count, *args)
}

/** "12 Mar 2026, 09:40" in the phone's locale. */
fun formatDateTime(millis: Long, locale: Locale = Locale.getDefault()): String =
    java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
        .withLocale(locale)
        .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("Africa/Kigali")))

/** "12 Mar 2026" in the phone's locale. */
fun formatDate(epochDay: Long, locale: Locale = Locale.getDefault()): String =
    java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale).format(LocalDate.ofEpochDay(epochDay))

/** "5 minutes ago", "Yesterday"… localised by Android itself. */
fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(millis, now, android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()

/** Icon for lesson steps, which name icons from the shared content file. */
@DrawableRes
fun lessonIcon(name: String): Int = when (name) {
    "route" -> R.drawable.ic_route
    "target" -> R.drawable.ic_target
    "eye" -> R.drawable.ic_eye
    "check" -> R.drawable.ic_check
    "spade" -> R.drawable.ic_spade
    "cow" -> R.drawable.ic_cow
    "drop" -> R.drawable.ic_drop
    "sync" -> R.drawable.ic_sync
    "layers" -> R.drawable.ic_layers
    "calendar" -> R.drawable.ic_calendar
    "sack" -> R.drawable.ic_sack
    "rain" -> R.drawable.ic_rain
    "sun" -> R.drawable.ic_sun
    "home" -> R.drawable.ic_home
    "x" -> R.drawable.ic_x
    "hoe" -> R.drawable.ic_hoe
    "shield" -> R.drawable.ic_shield
    "user" -> R.drawable.ic_user
    "wind" -> R.drawable.ic_wind
    "trash" -> R.drawable.ic_trash
    else -> R.drawable.ic_leaf
}
