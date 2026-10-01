// Zero-dependency Inama API server: `node src/v1/dev-server.js` (no npm install needed).
// Serves only the Inama v1 API on an in-memory store — ideal for demoing the Android app.
// For the full AgriAI + Inama backend, use `npm start` (Express, optional MongoDB).
import { config } from "../../config/index.js";
import { buildInamaRouter } from "./routes.js";
import { createNodeServer } from "./http/nodeAdapter.js";
import { getStore } from "./store/memoryStore.js";
import { geminiAvailable } from "./services/geminiAdvisor.js";

const port = parseInt(process.env.INAMA_PORT || process.env.PORT) || 5055;
const server = createNodeServer(buildInamaRouter(), { store: getStore() });

server.listen(port, "0.0.0.0", () => {
  console.log(`\n🌱  Inama API (dev server) on http://localhost:${port}/api/v1`);
  console.log(`   Android emulator: http://10.0.2.2:${port}/api/v1`);
  console.log(`   AI engine : ${geminiAvailable() ? `Gemini (${config.gemini.model})` : "knowledge base (set GEMINI_API_KEY for Gemini)"}`);
  console.log(`   Weather   : ${config.weather.apiKey ? "OpenWeatherMap" : "demo forecast (set WEATHER_API_KEY)"}`);
  console.log(`   OTP codes : ${config.inama.otp.exposeInResponse ? "returned in the response (development only)" : "hidden"}\n`);
});
