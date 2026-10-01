package rw.inama.app.domain.model

enum class WeatherCondition(val wire: String) {
    SUNNY("sunny"), PARTLY_CLOUDY("partly_cloudy"), RAIN("rain"), STORM("storm");

    companion object {
        fun fromWire(value: String?): WeatherCondition = entries.firstOrNull { it.wire == value } ?: PARTLY_CLOUDY
        fun fromRainChance(chance: Int): WeatherCondition = when {
            chance >= 70 -> STORM
            chance >= 50 -> RAIN
            chance >= 25 -> PARTLY_CLOUDY
            else -> SUNNY
        }
    }
}

data class CurrentWeather(
    val tempC: Int,
    val windKph: Int,
    val humidity: Int,
    val condition: WeatherCondition,
    val rainChance: Int,
)

data class DayForecast(
    /** ISO date yyyy-MM-dd (local to Rwanda). The UI formats the day name in the user's language. */
    val date: String,
    val highC: Int,
    val lowC: Int,
    val rainChance: Int,
    val rainMm: Int,
    val condition: WeatherCondition,
)

enum class FarmActivity(val wire: String) {
    SPRAYING("spraying"), WEEDING("weeding"), PLANTING("planting"), DRYING("drying");

    companion object {
        fun fromWire(value: String?): FarmActivity? = entries.firstOrNull { it.wire == value }
    }
}

enum class Verdict(val wire: String) {
    GO("go"), CAREFUL("careful"), WAIT("wait");

    companion object {
        fun fromWire(value: String?): Verdict = entries.firstOrNull { it.wire == value } ?: CAREFUL
    }
}

/** [reasonCode] matches the server: spray_rain, spray_wind, spray_go, weed_wet, weed_go, plant_rain, plant_dry, plant_mixed, dry_rain, dry_go. */
data class FarmDecision(val activity: FarmActivity, val verdict: Verdict, val reasonCode: String)

/** [code]: heavy_rain | heat | dry_spell. */
data class WeatherAlert(val code: String, val date: String?)

enum class WeatherSource { LIVE, DEMO, CACHED }

data class WeatherReport(
    val place: String,
    val current: CurrentWeather,
    val days: List<DayForecast>,
    val decisions: List<FarmDecision>,
    val alerts: List<WeatherAlert>,
    val source: WeatherSource,
    val updatedAtMillis: Long,
)
