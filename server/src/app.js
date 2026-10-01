import express from "express";
import helmet from "helmet";
import cors from "cors";
import rateLimit from "express-rate-limit";
import passport from "./config/passport.js";
import { config } from "../config/index.js";
import { requestLogger } from "./middleware/logger.js";
import { errorHandler, notFound } from "./middleware/errorHandler.js";
// Inama v1 API (Android app). Framework-agnostic handlers mounted through an Express adapter.
import { buildInamaRouter } from "./v1/routes.js";
import { inamaExpressMiddleware } from "./v1/http/expressAdapter.js";
import { getStore } from "./v1/store/memoryStore.js";

// Routes
import { authRouter } from "./routes/auth.js";
import chatRouter from "./routes/chat.js";
import diseaseRouter from "./routes/disease.js";
import adminRouter from "./routes/admin.js";
import contactRouter from "./routes/contact.js";
import {
  cropRouter,
  soilRouter,
  weatherRouter,
  notificationRouter,
  bookmarkRouter,
  schemeRouter,
} from "./routes/features.js";

const app = express();

const allowedOriginsFromEnv = (process.env.FRONTEND_URL || "")
  .split(",")
  .map((url) => url.trim().replace(/\/$/, ""))
  .filter(Boolean);

const defaultAllowedOrigins = [
  "http://localhost:3000",
  "http://localhost:5173",
  "http://localhost:5000",
  "https://agri-ai-advisory-system.vercel.app",
];

app.use(
  cors({
    origin: (origin, callback) => {
      if (!origin) return callback(null, true);
      const normalizedOrigin = origin.replace(/\/$/, "");
      if (
        allowedOriginsFromEnv.includes(normalizedOrigin) ||
        defaultAllowedOrigins.includes(normalizedOrigin) ||
        /\.vercel\.app$/.test(new URL(origin).hostname)
      ) {
        return callback(null, true);
      }
      return callback(new Error(`CORS policy error: Origin ${origin} not allowed`));
    },
    methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allowedHeaders: ["Content-Type", "Authorization"],
    credentials: true,
  }),
);

app.use(
  "/api/",
  rateLimit({
    windowMs: config.rateLimit.windowMs,
    max: config.rateLimit.max,
    standardHeaders: true,
    legacyHeaders: false,
    message: {
      success: false,
      message: "Too many requests. Please try again later.",
    },
  }),
);
app.use(
  "/api/auth/login",
  rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 5,
    standardHeaders: true,
    legacyHeaders: false,
    message: {
      success: false,
      message: "Too many login attempts. Please try again in 15 minutes.",
    },
  }),
);
app.use(
  "/api/auth/register",
  rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 10,
    standardHeaders: true,
    legacyHeaders: false,
    message: {
      success: false,
      message: "Too many registration attempts. Please try again later.",
    },
  }),
);

// Inama v1 — mounted before the body parsers because it reads JSON and multipart bodies itself.
app.use("/api/v1", inamaExpressMiddleware(buildInamaRouter(), { store: getStore() }));

// Without MongoDB the original AgriAI routes cannot work; answer clearly instead of timing out.
if (config.demoMode) {
  app.use(/^\/api\/(?!health)/, (_req, res) =>
    res.status(503).json({
      success: false,
      message: "Demo mode: set MONGODB_URI to enable the AgriAI web routes. The Inama API is available under /api/v1.",
    }),
  );
}

app.use(express.json({ limit: "10mb" }));
app.use(express.urlencoded({ extended: true, limit: "10mb" }));
app.use(passport.initialize());
if (config.isDev) app.use(requestLogger);

app.get("/", (_req, res) => {
  res.json({
    success: true,
    message: "AgriAI API Server is running. API endpoints are under /api",
    health: "/api/health",
    version: "1.0.0",
    environment: config.nodeEnv,
    timestamp: new Date().toISOString(),
  });
});

app.get("/api/health", (_req, res) => {
  res.json({
    success: true,
    message: "AgriAI API is running",
    version: "1.0.0",
    environment: config.nodeEnv,
    timestamp: new Date().toISOString(),
  });
});

app.use("/api/auth", authRouter);
app.use("/api/chat", chatRouter);
app.use("/api/disease-detection", diseaseRouter);
app.use("/api/crop-recommendation", cropRouter);
app.use("/api/soil-health", soilRouter);
app.use("/api/weather", weatherRouter);
app.use("/api/notifications", notificationRouter);
app.use("/api/bookmarks", bookmarkRouter);
app.use("/api/schemes", schemeRouter);
app.use("/api/admin", adminRouter);
app.use("/api/contact", contactRouter);

app.use(notFound);
app.use(errorHandler);

export default app;
