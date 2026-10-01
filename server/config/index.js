// Centralised configuration. Everything that varies between environments comes from
// environment variables — no endpoints, keys or secrets live in application code.
//
// Inama change (vs. AgriAI): the server can now start WITHOUT MongoDB ("demo mode").
// The Inama v1 API (/api/v1) runs on an in-memory store in that case, while the
// original AgriAI routes answer 503 until MONGODB_URI is configured.
import crypto from "node:crypto";

// Load .env — prefer dotenv (installed with the full app); fall back to Node's built-in
// loader so the zero-dependency Inama dev server (src/v1/dev-server.js) also works.
try {
  const dotenv = await import("dotenv");
  dotenv.default.config();
} catch {
  try {
    process.loadEnvFile?.();
  } catch {
    /* no .env file — rely on the real environment */
  }
}

const nodeEnv = process.env.NODE_ENV || "development";
const isProduction = nodeEnv === "production";

if (isProduction && !process.env.JWT_SECRET) {
  console.error("❌  Missing required env var in production: JWT_SECRET");
  process.exit(1);
}

let jwtSecret = process.env.JWT_SECRET;
if (!jwtSecret) {
  jwtSecret = crypto.randomBytes(32).toString("hex");
  console.warn("⚠️  JWT_SECRET not set — using a random secret for this run (tokens reset on restart).");
}

const list = (value, fallback) =>
  (value || fallback)
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);

export const config = {
  port: parseInt(process.env.PORT) || 5000,
  nodeEnv,
  isDev: !isProduction,

  mongo: {
    uri: process.env.MONGODB_URI || "",
  },
  demoMode: !process.env.MONGODB_URI,

  jwt: {
    secret: jwtSecret,
    expiresIn: process.env.JWT_EXPIRES_IN || "7d",
  },

  gemini: {
    apiKey: process.env.GEMINI_API_KEY || "",
    model: process.env.GEMINI_MODEL || "gemini-2.5-flash-lite",
    baseUrl: process.env.GEMINI_BASE_URL || "https://generativelanguage.googleapis.com/v1beta",
  },

  weather: {
    apiKey: process.env.WEATHER_API_KEY || "",
    baseUrl: process.env.WEATHER_BASE_URL || "https://api.openweathermap.org/data/2.5",
  },

  cors: {
    origin: process.env.FRONTEND_URL || "http://localhost:5173",
  },

  google: {
    clientId: process.env.GOOGLE_CLIENT_ID || "",
    clientSecret: process.env.GOOGLE_CLIENT_SECRET || "",
    callbackUrl:
      process.env.GOOGLE_CALLBACK_URL ||
      "http://localhost:5000/api/auth/google/callback",
  },

  rateLimit: {
    windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 15 * 60 * 1000,
    max: parseInt(process.env.RATE_LIMIT_MAX) || 100,
  },

  // ---- Inama v1 -------------------------------------------------------------
  inama: {
    // Languages the API will answer in. Only English content ships in the MVP;
    // add "rw" / "fr" here once advisory_kb.<lang>.json and prompts are reviewed.
    languages: list(process.env.INAMA_LANGUAGES, "en"),
    defaultLanguage: process.env.INAMA_DEFAULT_LANGUAGE || "en",
    // Folder holding advisory_kb.<lang>.json (shared with the Android app).
    knowledgeDir: process.env.INAMA_KNOWLEDGE_DIR || "",
    contentDir: process.env.INAMA_CONTENT_DIR || "",
    otp: {
      length: 6,
      ttlSeconds: parseInt(process.env.INAMA_OTP_TTL_SECONDS) || 300,
      // In development the OTP is returned in the API response (no SMS gateway yet).
      exposeInResponse: !isProduction && process.env.INAMA_EXPOSE_OTP !== "false",
    },
    tokenTtlSeconds: parseInt(process.env.INAMA_TOKEN_TTL_SECONDS) || 30 * 24 * 3600,
    maxImageBytes: parseInt(process.env.INAMA_MAX_IMAGE_BYTES) || 8 * 1024 * 1024,
    // Demo: an escalated case gets a canned "expert" reply after this many seconds.
    demoExpertReplySeconds: parseInt(process.env.INAMA_DEMO_EXPERT_REPLY_SECONDS) || 90,
    defaultPlace: process.env.INAMA_DEFAULT_PLACE || "Kigali, RW",
  },
};
