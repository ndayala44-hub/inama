import { config } from "../../config/index.js";

const isConfigured = () =>
  config.weather.apiKey &&
  config.weather.apiKey !== "your_openweathermap_api_key_here";

function buildFarmingAlerts(current) {
  const alerts = [];
  if (current.rain || current.clouds?.all > 80) {
    alerts.push({
      type: "warning",
      message:
        "High rainfall likelihood — delay pesticide and fertilizer spraying.",
    });
  }
  if (current.main?.temp > 40) {
    alerts.push({
      type: "warning",
      message:
        "Extreme heat — increase irrigation frequency, schedule work in early morning.",
    });
  }
  if (current.wind?.speed > 10) {
    alerts.push({
      type: "info",
      message: "Strong winds — avoid spraying operations today.",
    });
  }
  return alerts;
}

function fallbackWeather(location) {
  return {
    location: location || "Nashik, Maharashtra",
    current: {
      temp: 29,
      condition: "Partly Cloudy",
      description: "partly cloudy",
      humidity: 64,
      windSpeed: 14,
      rainChance: 30,
      feelsLike: 31,
      visibility: 10,
    },
    forecast: [
      { day: "Today", high: 31, low: 22, condition: "Partly Cloudy", rain: 30 },
      { day: "Tomorrow", high: 33, low: 23, condition: "Sunny", rain: 5 },
      { day: "Wed", high: 28, low: 21, condition: "Rain", rain: 80 },
      { day: "Thu", high: 27, low: 20, condition: "Rain", rain: 70 },
      { day: "Fri", high: 30, low: 22, condition: "Sunny", rain: 10 },
    ],
    alerts: [
      {
        type: "warning",
        message: "Heavy rainfall expected mid-week — delay pesticide spraying.",
      },
    ],
    isFallback: true,
  };
}

export async function getWeatherData({ location, lat, lon } = {}) {
  const fallbackLabel = location || "Nashik, Maharashtra";
  if (!isConfigured()) return fallbackWeather(fallbackLabel);

  const query =
    lat != null && lon != null
      ? `lat=${lat}&lon=${lon}`
      : `q=${encodeURIComponent(location || "Nashik,IN")}`;

  try {
    const [curRes, foreRes] = await Promise.all([
      fetch(
        `${config.weather.baseUrl}/weather?${query}&appid=${config.weather.apiKey}&units=metric`,
      ),
      fetch(
        `${config.weather.baseUrl}/forecast?${query}&appid=${config.weather.apiKey}&units=metric&cnt=5`,
      ),
    ]);

    if (!curRes.ok) return fallbackWeather(fallbackLabel);

    const cur = await curRes.json();
    const fore = foreRes.ok ? await foreRes.json() : null;

    return {
      location: `${cur.name}, ${cur.sys.country}`,
      current: {
        temp: Math.round(cur.main.temp),
        condition: cur.weather[0].main,
        description: cur.weather[0].description,
        humidity: cur.main.humidity,
        windSpeed: Math.round((cur.wind?.speed || 0) * 3.6),
        rainChance: cur.rain ? 80 : 20,
        feelsLike: Math.round(cur.main.feels_like),
        visibility: cur.visibility ? Math.round(cur.visibility / 1000) : null,
      },
      forecast: fore
        ? fore.list.map((f) => ({
            day: new Date(f.dt * 1000).toLocaleDateString("en-IN", {
              weekday: "short",
            }),
            high: Math.round(f.main.temp_max),
            low: Math.round(f.main.temp_min),
            condition: f.weather[0].main,
            rain: f.pop ? Math.round(f.pop * 100) : 0,
          }))
        : fallbackWeather(fallbackLabel).forecast,
      alerts: buildFarmingAlerts(cur),
      isFallback: false,
    };
  } catch (err) {
    console.error("Weather API error:", err.message);
    return fallbackWeather(fallbackLabel);
  }
}
