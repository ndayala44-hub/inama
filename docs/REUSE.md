# What came from AgriAI, and what is new

Starting point: [anvi-sha675/AgriAI-Advisory-System](https://github.com/anvi-sha675/AgriAI-Advisory-System), a MERN web app: React/Vite client and a Node/Express/MongoDB backend with Google Gemini and OpenWeatherMap. It has **no Android code**, so the Android app is new. The backend and the AI/advisory logic were reused and extended.

> **Licence:** at the time of review the AgriAI repository had **no LICENSE file**, which by default means all rights are reserved by its authors. Before any public or commercial release, get written permission from the authors or replace the reused server code. The reused parts are listed below so that is easy to scope.

## Server (`server/`)

| Part | Status | Notes |
|---|---|---|
| Express app, routes, controllers, models, middleware, validators, seed (`src/*` outside `src/v1`) | **Reused unchanged** | The original AgriAI web API still works at `/api/*` when `MONGODB_URI` is set. |
| `src/services/geminiService.js` | **Reused, lightly changed** | Same Gemini REST transport. `geminiGenerate`/`geminiVision` are now exported and accept a system instruction and generation config; base URL comes from config. |
| `config/index.js` | **Reused, rewritten** | Same env-var pattern; `dotenv` is optional; adds a demo mode (no MongoDB needed) and the `INAMA_*` settings. |
| `src/app.js`, `src/server.js` | **Reused, small additions** | Mount `/api/v1`; connect to MongoDB only when configured; clear 503 for legacy routes in demo mode. |
| AgriAI's `parseAdvisory` + keyword fallback (`FALLBACK_CHATS`) | **Pattern reused → `src/v1/services/kbEngine.js`, `advice.js`** | Became a scored, transparent engine over a reviewed knowledge base, with honest confidence and escalation. |
| AgriAI's weather service + `buildFarmingAlerts()` | **Pattern reused → `src/v1/services/weatherAdvisor.js`** | Same OpenWeatherMap key. Now aggregates the 3-hourly forecast into days (the original `cnt=5` call returned only 15 hours) and turns it into go/careful/wait decisions for spraying, weeding, planting and drying. |
| `src/v1/**` (routes, handlers, HTTP core + Node and Express adapters, OTP auth, JWT, in-memory store, i18n, Gemini classification adapter) | **New** | The API the Android app uses. See [API.md](API.md). |
| `test/v1/**`, `scripts/capture-fixtures.mjs` | **New** | 31 API tests; fixture capture for the Android tests. |

## Web client (`client/` in AgriAI)

Not carried over: the Android app replaces it. These patterns were ported to Kotlin:

| AgriAI client | Inama Android |
|---|---|
| `services/aiService.js` — call the API, fall back to an offline answer on network failure | `domain/ai/EngineChain.kt` → `FallbackAdvisoryEngine` (used for every AI call) |
| `hooks/useSpeechRecognition.js` | `core/speech/SpeechInput.kt` (platform `SpeechRecognizer`, live transcript and level) |
| `hooks/useSpeechSynthesis.js` | `core/speech/SpeechOutput.kt` (text-to-speech, follows the app language, English fallback) |
| Pages: Disease Detection, Chat/Voice Assistant, Weather, Chat History, Profile, Settings | Photo check → Diagnosis, Ask, Weather, History, Me, Settings — redesigned for the Inama UX |

## Android app (`android/`) — new

Everything under `android/` is new: Kotlin + Jetpack Compose, following the Inama designs.

- Onboarding: splash, language (English now; Kinyarwanda/French shown as coming soon), intro, privacy and consent, phone + SMS code, profile, first field, done.
- Home: greeting, weather decision, today's priority, fields, quick actions, this week's tasks, recent checks, read-aloud briefing.
- Photo check: camera with guide (CameraX), gallery, bundled sample photos, on-device quality check, crop/field/signs, analysis progress.
- Diagnosis: outcome (likely condition / not sure / healthy), confidence in words, what's happening / why it matters / what to do, evidence, sources, add to tasks, feedback, send to a person, case status.
- Ask by voice or text, answer details; Farm and field detail with crop stage; add/edit field; Tasks; Weather; Learn and lessons; History; Me, Settings, Help.
- Offline: on-device knowledge-base engine, local storage, cached weather, local demo sign-in and expert replies.

## Shared (`shared/`) — new

The advisory knowledge base (10 crops, 29 conditions, 19 symptoms, 9 common questions), engine test vectors, 8 lessons and the 30 districts of Rwanda. **All advice content is a draft** written from public guidance (Rwanda Agriculture Board, FAO, IITA) and must be reviewed by Rwandan agronomists before use in the field (`reviewStatus: "draft"`).

## Mock and demo data

| Where | What | Replaced by |
|---|---|---|
| Server `INAMA_EXPOSE_OTP=true` | SMS code returned in the API response | An SMS gateway (e.g. Africa's Talking) |
| Server & app | Expert/promoter reply generated from the knowledge base after 90 s | A promoter/agronomist dashboard |
| Server & app | Sample Rwanda forecast when there is no `WEATHER_API_KEY` or no signal | Live OpenWeatherMap (already supported) |
| Server | In-memory store for v1 data | MongoDB store behind the same interface |
| App | Offline sign-in code `123456` (`inama.offlineDemoCode`) when the server can't be reached | Remove for production |
| App | Three sample photos in `assets/samples` | — (kept for demos) |
