import app from "./app.js";
import { connectDB } from "./utils/db.js";
import { config } from "../config/index.js";

async function start() {
  // Connect to MongoDB when configured. Without it the server runs in demo mode:
  // the Inama v1 API uses an in-memory store and the AgriAI web routes return 503.
  if (config.mongo.uri) {
    await connectDB();
  } else {
    console.warn("⚠️  MONGODB_URI not set — starting in demo mode (in-memory Inama API only).");
  }

  const server = app.listen(config.port, () => {
    console.log("\n🌱  AgriAI Backend Server");
    console.log(`   Environment : ${config.nodeEnv}`);
    console.log(`   Port        : ${config.port}`);
    console.log(`   API Base    : http://localhost:${config.port}/api`);
    console.log(`   Inama API   : http://localhost:${config.port}/api/v1`);
    console.log(`   MongoDB     : ${config.mongo.uri ? "configured" : "not configured (demo mode)"}`);

    if (
      !config.gemini.apiKey ||
      config.gemini.apiKey === "your_gemini_api_key_here"
    ) {
      console.log(
        "\n⚠️  GEMINI_API_KEY not set — AI responses will use fallback data.",
      );
      console.log("   Get a free key: https://aistudio.google.com/app/apikey");
    }
    if (
      !config.weather.apiKey ||
      config.weather.apiKey === "your_openweathermap_api_key_here"
    ) {
      console.log(
        "⚠️  WEATHER_API_KEY not set — weather will use fallback data.",
      );
      console.log("   Get a free key: https://openweathermap.org/api");
    }

    console.log("\n📋  Demo credentials:");
    console.log(
      "    Farmer : ramesh@example.com / Demo@1234   (run: npm run seed)",
    );
    console.log("    Admin  : admin@agriai.in    / Admin@1234\n");
  });

  const shutdown = (signal) => {
    console.log(`\n${signal} — shutting down gracefully...`);
    server.close(async () => {
      const { disconnectDB } = await import("./utils/db.js");
      await disconnectDB();
      console.log("Server closed.");
      process.exit(0);
    });
  };

  process.on("SIGTERM", () => shutdown("SIGTERM"));
  process.on("SIGINT", () => shutdown("SIGINT"));

  process.on("unhandledRejection", (reason) => {
    console.error("Unhandled Rejection:", reason);
  });
  process.on("uncaughtException", (err) => {
    console.error("Uncaught Exception:", err);
    process.exit(1);
  });
}

start();
