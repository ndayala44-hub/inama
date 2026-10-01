package rw.inama.app.domain.weather

import rw.inama.app.domain.model.CurrentWeather
import rw.inama.app.domain.model.DayForecast
import rw.inama.app.domain.model.FarmActivity
import rw.inama.app.domain.model.FarmDecision
import rw.inama.app.domain.model.Verdict
import rw.inama.app.domain.model.WeatherAlert
import rw.inama.app.domain.model.WeatherCondition
import rw.inama.app.domain.model.WeatherReport
import rw.inama.app.domain.model.WeatherSource
import java.time.LocalDate

/**
 * Weather → go / careful / wait for common farm jobs. Kotlin port of decide() in
 * server/src/v1/services/weatherAdvisor.js (itself an extension of AgriAI's buildFarmingAlerts),
 * used when the phone is offline and for cached forecasts.
 */
object FarmDecisions {
    fun decide(current: CurrentWeather, days: List<DayForecast>): Pair<List<FarmDecision>, List<WeatherAlert>> {
        val today = days.getOrNull(0)
        val tomorrow = days.getOrNull(1)
        val next3 = days.take(3)
        val avg3 = if (next3.isEmpty()) 0.0 else next3.sumOf { it.rainChance }.toDouble() / next3.size
        val rainToday = today?.rainChance ?: 0
        val rainTomorrow = tomorrow?.rainChance ?: 0

        val decisions = listOf(
            when {
                rainToday >= 60 || rainTomorrow >= 60 -> FarmDecision(FarmActivity.SPRAYING, Verdict.WAIT, "spray_rain")
                current.windKph > 15 -> FarmDecision(FarmActivity.SPRAYING, Verdict.CAREFUL, "spray_wind")
                else -> FarmDecision(FarmActivity.SPRAYING, Verdict.GO, "spray_go")
            },
            if (rainToday >= 70) FarmDecision(FarmActivity.WEEDING, Verdict.WAIT, "weed_wet")
            else FarmDecision(FarmActivity.WEEDING, Verdict.GO, "weed_go"),
            when {
                avg3 >= 50 -> FarmDecision(FarmActivity.PLANTING, Verdict.GO, "plant_rain")
                avg3 < 20 -> FarmDecision(FarmActivity.PLANTING, Verdict.WAIT, "plant_dry")
                else -> FarmDecision(FarmActivity.PLANTING, Verdict.CAREFUL, "plant_mixed")
            },
            if (rainToday >= 50) FarmDecision(FarmActivity.DRYING, Verdict.CAREFUL, "dry_rain")
            else FarmDecision(FarmActivity.DRYING, Verdict.GO, "dry_go"),
        )

        val alerts = mutableListOf<WeatherAlert>()
        days.take(5).firstOrNull { it.rainChance >= 70 }?.let { alerts += WeatherAlert("heavy_rain", it.date) }
        if (current.tempC >= 32) alerts += WeatherAlert("heat", today?.date)
        if (days.size >= 5 && days.take(5).all { it.rainChance < 20 }) alerts += WeatherAlert("dry_spell", today?.date)
        return decisions to alerts
    }
}

/** Deterministic demo forecast for Rwanda — same pattern as the server's demoWeather(). */
object DemoWeather {
    private val pattern = intArrayOf(15, 25, 80, 60, 30, 20, 10, 45, 70, 35)

    fun forecast(place: String, today: LocalDate, nowMillis: Long): WeatherReport {
        val dayIndex = today.toEpochDay()
        val days = (0 until 7).map { i ->
            val rain = pattern[((dayIndex + i) % pattern.size).toInt()]
            DayForecast(
                date = today.plusDays(i.toLong()).toString(),
                highC = 27 - Math.round(rain / 40.0).toInt(),
                lowC = 15 + ((dayIndex + i) % 3).toInt(),
                rainChance = rain,
                rainMm = if (rain >= 50) Math.round(rain / 5.0).toInt() else if (rain >= 25) 2 else 0,
                condition = WeatherCondition.fromRainChance(rain),
            )
        }
        val current = CurrentWeather(tempC = 24, windKph = 11, humidity = 66, condition = days[0].condition, rainChance = days[0].rainChance)
        val (decisions, alerts) = FarmDecisions.decide(current, days)
        return WeatherReport(place, current, days, decisions, alerts, WeatherSource.DEMO, nowMillis)
    }
}
