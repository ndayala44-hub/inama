// Weather → farm decisions. Extends AgriAI's buildFarmingAlerts() idea: instead of generic
// alerts, every forecast is turned into go / careful / wait verdicts for common farm jobs.
// Uses the same OpenWeatherMap key as AgriAI's weatherService, but aggregates the 3-hourly
// forecast into days (AgriAI's cnt=5 returns only 15 hours). Falls back to deterministic
// demo weather for Rwanda when no key is configured or the API is unreachable.
//
// The Android app has a Kotlin port of decide() for offline use (domain.weather.FarmDecisions).
import { config } from "../../../config/index.js";
import { t } from "../i18n.js";

const isConfigured = () =>
  Boolean(config.weather.apiKey) && !["your_weather_api_key", "your_openweathermap_api_key_here"].includes(config.weather.apiKey);

const DAY_MS = 24 * 3600 * 1000;
const isoDate = (d) => d.toISOString().slice(0, 10);
const weekday = (d) => d.toLocaleDateString("en-GB", { weekday: "short", timeZone: "Africa/Kigali" });

function conditionFor(rainChance) {
  if (rainChance >= 70) return "storm";
  if (rainChance >= 50) return "rain";
  if (rainChance >= 25) return "partly_cloudy";
  return "sunny";
}

/** Deterministic demo forecast for Rwanda (two rainy seasons, mild highland temperatures). */
export function demoWeather(place, now = new Date()) {
  const pattern = [15, 25, 80, 60, 30, 20, 10, 45, 70, 35];
  const dayIndex = Math.floor(now.getTime() / DAY_MS);
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(now.getTime() + i * DAY_MS);
    const rain = pattern[(dayIndex + i) % pattern.length];
    return {
      date: isoDate(d),
      label: i === 0 ? "Today" : weekday(d),
      highC: 27 - Math.round(rain / 40),
      lowC: 15 + ((dayIndex + i) % 3),
      rainChance: rain,
      rainMm: rain >= 50 ? Math.round(rain / 5) : rain >= 25 ? 2 : 0,
      condition: conditionFor(rain),
    };
  });
  return {
    place: place || config.inama.defaultPlace,
    current: { tempC: 24, windKph: 11, humidity: 66, condition: days[0].condition, rainChance: days[0].rainChance },
    days,
    source: "demo",
    updatedAt: now.toISOString(),
  };
}

async function fetchOpenWeather({ lat, lon, place }) {
  const q = lat != null && lon != null ? `lat=${lat}&lon=${lon}` : `q=${encodeURIComponent(place || config.inama.defaultPlace)}`;
  const key = config.weather.apiKey;
  const [curRes, foreRes] = await Promise.all([
    fetch(`${config.weather.baseUrl}/weather?${q}&appid=${key}&units=metric`),
    fetch(`${config.weather.baseUrl}/forecast?${q}&appid=${key}&units=metric&cnt=40`),
  ]);
  if (!curRes.ok || !foreRes.ok) return null;
  const cur = await curRes.json();
  const fore = await foreRes.json();
  const byDay = new Map();
  for (const f of fore.list || []) {
    const date = new Date(f.dt * 1000);
    const key2 = isoDate(new Date(date.getTime() + 2 * 3600 * 1000)); // Kigali is UTC+2
    const day = byDay.get(key2) || { date: key2, highC: -99, lowC: 99, rainChance: 0, rainMm: 0 };
    day.highC = Math.max(day.highC, Math.round(f.main.temp_max));
    day.lowC = Math.min(day.lowC, Math.round(f.main.temp_min));
    day.rainChance = Math.max(day.rainChance, Math.round((f.pop || 0) * 100));
    day.rainMm += f.rain?.["3h"] || 0;
    byDay.set(key2, day);
  }
  const days = [...byDay.values()].slice(0, 7).map((d, i) => ({
    ...d,
    rainMm: Math.round(d.rainMm),
    label: i === 0 ? "Today" : weekday(new Date(`${d.date}T12:00:00Z`)),
    condition: conditionFor(d.rainChance),
  }));
  if (!days.length) return null;
  return {
    place: `${cur.name}, ${cur.sys?.country || ""}`.trim().replace(/,$/, ""),
    current: {
      tempC: Math.round(cur.main.temp),
      windKph: Math.round((cur.wind?.speed || 0) * 3.6),
      humidity: cur.main.humidity,
      condition: days[0].condition,
      rainChance: days[0].rainChance,
    },
    days,
    source: "openweathermap",
    updatedAt: new Date().toISOString(),
  };
}

/** go / careful / wait for each farm job. */
export function decide(weather, lang = "en") {
  const [today = {}, tomorrow = {}] = weather.days;
  const next3 = weather.days.slice(0, 3);
  const avg3 = next3.reduce((a, d) => a + (d.rainChance || 0), 0) / Math.max(1, next3.length);
  const wind = weather.current?.windKph || 0;
  const item = (activity, verdict, code) => ({ activity, label: t(lang, `decisions.${activity}`), verdict, reasonCode: code, reason: t(lang, `decisions.${code}`) });

  const decisions = [
    today.rainChance >= 60 || tomorrow.rainChance >= 60
      ? item("spraying", "wait", "spray_rain")
      : wind > 15
        ? item("spraying", "careful", "spray_wind")
        : item("spraying", "go", "spray_go"),
    today.rainChance >= 70 ? item("weeding", "wait", "weed_wet") : item("weeding", "go", "weed_go"),
    avg3 >= 50 ? item("planting", "go", "plant_rain") : avg3 < 20 ? item("planting", "wait", "plant_dry") : item("planting", "careful", "plant_mixed"),
    today.rainChance >= 50 ? item("drying", "careful", "dry_rain") : item("drying", "go", "dry_go"),
  ];

  const alerts = [];
  const heavy = weather.days.slice(0, 5).find((d) => d.rainChance >= 70);
  if (heavy) alerts.push({ code: "heavy_rain", date: heavy.date, message: t(lang, "alerts.heavy_rain", { day: heavy.label === "Today" ? "today" : `on ${heavy.label}` }) });
  if ((weather.current?.tempC || 0) >= 32) alerts.push({ code: "heat", date: today.date, message: t(lang, "alerts.heat") });
  if (weather.days.length >= 5 && weather.days.slice(0, 5).every((d) => d.rainChance < 20)) alerts.push({ code: "dry_spell", date: today.date, message: t(lang, "alerts.dry_spell") });

  return { decisions, alerts };
}

export async function getWeatherAdvice({ lat, lon, place, lang = "en" }) {
  let weather = null;
  if (isConfigured()) {
    try {
      weather = await fetchOpenWeather({ lat, lon, place });
    } catch (err) {
      if (config.isDev) console.warn("⚠️  Weather API failed:", err.message);
    }
  }
  if (!weather) weather = demoWeather(place);
  return { ...weather, ...decide(weather, lang) };
}
