# Inama MVP — architecture

This document explains how the MVP is put together and why. It is written for the engineers who will take it to the next version (Kinyarwanda, more crops, a real SMS gateway, a database).

## 1. The system in one picture

```
┌──────────────────────── Android app (Kotlin, Jetpack Compose) ────────────────────────┐
│                                                                                       │
│  ui/ + feature/*      Screens, ViewModels (StateFlow), navigation. No business rules.  │
│        │                                                                              │
│  domain/              Pure Kotlin: models, repository interfaces, use cases,          │
│        │              AdvisoryEngine (the AI seam), on-device KnowledgeBaseEngine,     │
│        │              photo analysis, crop-stage & weather decision rules.             │
│        │                                                                              │
│  data/                Repository implementations: JSON files on the phone,            │
│        │              Inama API client (OkHttp), RemoteAdvisoryEngine, mappers.        │
│  core/                Platform services: config, connectivity, camera image prep,      │
│                       text-to-speech, speech recognition.                             │
│  di/AppContainer      The one place where implementations are chosen.                 │
└───────────────┬───────────────────────────────────────────────────────────────────────┘
                │ HTTPS  /api/v1  (Accept-Language on every call)
┌───────────────▼──────────── Inama server (Node.js — the AgriAI backend, extended) ─────┐
│  src/v1/           New Inama API: auth by SMS code, diagnoses, questions, cases,       │
│                    weather decisions. Framework-agnostic handlers + Express adapter.   │
│  services/gemini*  AgriAI's Gemini transport, reused; new classification prompts.      │
│  kbEngine.js       Rule-based engine over the same knowledge base (fallback).          │
│  (original AgriAI web API, unchanged, still mounted at /api/*)                         │
└───────────────┬───────────────────────────────────────────────────────────────────────┘
                │ read at start-up / bundled into the APK at build time
┌───────────────▼────────────────────── shared/ ────────────────────────────────────────┐
│  knowledge/advisory_kb.<lang>.json   Crops, stages, symptoms, conditions, advice text. │
│  knowledge/engine_test_vectors.json  Cases both engines must agree on.                 │
│  content/lessons.<lang>.json, rwanda_districts.json                                    │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

## 2. Key decisions

Each decision lists what was chosen, why, and what it costs.

### D1 — One reviewed knowledge base, shared by phone and server
The advice text farmers read (what's happening, why it matters, what to do, sources) lives in `shared/knowledge/advisory_kb.en.json`, not in code and not in model output. Both engines read it: the server's `kbEngine.js` and the app's `KnowledgeBaseEngine.kt` (a line-for-line port). `engine_test_vectors.json` holds cases both must answer identically; the server tests and the Android unit tests both run them.

*Why:* agronomists review one file; answers are the same online and offline; the AI cannot invent a pesticide dose.
*Cost:* two implementations of the scoring algorithm to keep in step (the test vectors catch drift).

### D2 — The AI classifies; the knowledge base advises
Gemini (through the server) is asked to *choose* among the knowledge base's conditions for the crop, report photo quality and confidence, and return JSON. `geminiAdvisor.js` validates that JSON; anything unexpected is discarded and the rule-based engine answers instead. The advice text shown is always the reviewed text for the chosen condition.

### D3 — Honest confidence, and a safe path when unsure
Confidence is shown in words (High / Medium / Low) with a short reason, never as a percentage. Thresholds live in the knowledge base (`engine.highThreshold`, `mediumThreshold`). Colour-only photo evidence is capped below "medium" (`photoOnlyFactor`), a leaf is only called healthy when blemishes are low (`healthyMaxBlemish`), and two similar conditions produce "not sure". Low confidence returns only safe interim steps ("don't spray yet…") and recommends sending the case to a person (farmer promoter → agronomist). "Add to tasks" on an uncertain result only adds those safe steps.

### D4 — A single AI seam: `AdvisoryEngine`
`domain/ai/AdvisoryEngine.kt` is the only interface the app knows:

```kotlin
interface AdvisoryEngine {
    val info: EngineInfo
    suspend fun diagnose(request: DiagnosisRequest): Diagnosis
    suspend fun ask(request: QuestionRequest): Answer
}
```

Implementations: `RemoteAdvisoryEngine` (Inama API → Gemini, or the server's engine) and `KnowledgeBaseEngine` (on the phone). `FallbackAdvisoryEngine` tries the server and answers on-device when it is unreachable — AgriAI's "call the API, else offline answer" pattern, made reusable. `SwitchingAdvisoryEngine` honours Settings › How Inama answers. Adding a TFLite model or a Kinyarwanda-capable model is a new implementation registered in `AppContainer`; no screen changes.

### D5 — Offline first
Everything the farmer creates is stored on the phone first (`data/local/JsonFileStore`, one JSON file per collection, atomic writes). Content (knowledge base, lessons, districts) is bundled in the APK from `shared/`. With no signal, sign-in accepts a demo code, photo checks and questions run on-device, weather shows the last saved forecast (or a clearly labelled sample), and expert cases are simulated locally. The UI says which engine answered.

*Cost:* the MVP does not sync phone data back to the server (the server keeps its own copy of remote checks). Sync is future work.

### D6 — Photos are measured on the phone before upload
`core/image/ImageTools` resizes and re-encodes the photo (≈150–300 KB; smaller in low-data mode) and `domain/image/PhotoAnalyzer` measures sharpness, brightness and colour ratios in the centre of the frame in a few milliseconds. A blurry or dark photo is caught while the farmer is still in the field, and the measurements travel with the photo so the server can use them too.

### D7 — Language-neutral domain, localisable edges
Domain objects carry codes, not sentences, wherever the phrase is part of the app (confidence reasons, photo hints, farm decisions, urgency). The UI turns codes into words through `res/values/strings.xml` (`ui/components/Labels.kt` is the bridge). Advice sentences come from the per-language knowledge base. Every API call sends `Accept-Language`; the server answers from the matching knowledge base when that language is enabled. See §4.

### D8 — Lean dependencies, manual DI
No Hilt/KSP/Room/kotlinx.serialization: a small hand-written JSON codec (`data/json/Json.kt`), file storage, and a manual `AppContainer`. This keeps the build fast and the domain layer testable on a plain JVM. Room or a DI framework can be introduced behind the existing interfaces when the data volume or team size justifies it.

### D9 — Configuration outside code
Build-time values (`inama.apiBaseUrl`, `inama.offlineDemoCode`, `inama.helpline`) come from `android/gradle.properties` or `-P` flags into `BuildConfig`, read only by `core/config/AppConfig`. The server URL can also be overridden at run time in Settings › Server (for testing). API keys (Gemini, OpenWeatherMap, JWT secret) live only on the server, in `.env`.

## 3. Android package map

| Package | Responsibility |
|---|---|
| `domain/model` | Farmer, Field, Diagnosis, Answer, WeatherReport, KnowledgeBase, AppSettings… (pure data) |
| `domain/ai` | `AdvisoryEngine`, request types, fallback/switching engines, `kb/KnowledgeBaseEngine` |
| `domain/usecase` | Diagnose crop, ask advisor, add advice to tasks |
| `domain/farm`, `domain/weather`, `domain/image` | Crop stage & priority rules, farm decisions from forecasts, photo analysis |
| `domain/repository` | Interfaces the features depend on |
| `data/local` | `JsonFileStore`, `LocalStores` |
| `data/remote` | `InamaApi` (OkHttp), `RemoteAdvisoryEngine` |
| `data/mapper` | API JSON ↔ domain, storage JSON ↔ domain, bundled content parsers |
| `data/repository` | Repository implementations (local, and server-backed with local fallback) |
| `core/*` | Config, connectivity, camera image preparation, TTS, speech recognition |
| `ui/theme`, `ui/components` | Design system: colours, type, cards, chips, confidence meter, advice layout… |
| `ui/navigation` | Routes and the nav graph (ids only in routes) |
| `feature/*` | One folder per area: onboarding, home, scan (photo check + diagnosis + expert case), ask, farm, tasks, weather, learn, history, profile (me/settings/help) |

ViewModels expose one `StateFlow<UiState>` and take repository interfaces or use cases in their constructor; screens create them with `viewModel { … }` from `LocalAppContainer`.

## 4. Adding a language (Kinyarwanda, French, …)

Nothing in the domain or data layers needs to change. Steps for Kinyarwanda (`rw`):

1. **App strings** — copy `android/app/src/main/res/values/strings.xml` to `res/values-rw/strings.xml` and translate the values (keep keys and `%1$s` placeholders). Add `<locale android:name="rw"/>` to `res/xml/locales_config.xml`.
2. **Advice content** — add `shared/knowledge/advisory_kb.rw.json` (same ids and structure as the English file, translated text, reviewed by agronomists) and `shared/content/lessons.rw.json`. Both the app (`CatalogRepositoryImpl`) and the server (`knowledgeBase.js`) pick up `<name>.<lang>.json` and fall back to English.
3. **Switch it on** — set `availableInThisVersion = true` for `KINYARWANDA` in `domain/model/Settings.kt`, and add `rw` to `INAMA_LANGUAGES` on the server.
4. **AI** — `geminiAdvisor.js` already tells the model which language to answer in (`LANGUAGE_NAMES`), though the displayed advice comes from the translated knowledge base anyway. A Kinyarwanda-specific model is another `AdvisoryEngine` (app) or another adapter next to `geminiAdvisor.js` (server).
5. **Voice** — `core/speech/SpeechOutput` and `SpeechInput` already request the app language and fall back to English when the phone has no voice for it. Most phones have no Kinyarwanda TTS/STT, so these two classes are where a server-side Kinyarwanda speech service (e.g. Digital Umuganda models) plugs in.

Things to check when translating: button labels may get longer (buttons wrap to two lines), and date/day names are formatted from the phone locale (`Labels.kt`).

## 5. Data and privacy

- On the phone: app-private files under `files/store/*.json` and `files/photos/`. Excluded from cloud backup (`res/xml/*_rules.xml`). Settings › Reset all data deletes them.
- On the server: in the MVP, v1 data (farmers, diagnoses without the photo bytes, answers, cases) is kept **in memory** and is lost on restart; photos are never stored. `store/memoryStore.js` has an async interface so a MongoDB store (AgriAI already uses mongoose) can replace it.
- Consent choices from onboarding (share anonymously, promoter access, SMS copies) are stored in settings; enforcing them server-side is part of the sync work.

## 6. Testing

| What | How | Where |
|---|---|---|
| Server v1 API (auth, diagnoses, questions, cases, weather, i18n, errors) | `node --test` over real HTTP, 31 tests | `server/test/v1` |
| Engine parity phone ↔ server | Shared vectors run by both test suites | `shared/knowledge/engine_test_vectors.json` |
| App domain + data mapping | JVM unit tests (JUnit 4), 19 tests; API mapping tested against JSON captured from the real server | `android/app/src/test` |
| Photo quality thresholds | Calibrated on the bundled sample photos; `PhotoAnalyzer` unit-tested on synthetic images | `assets/samples`, `DomainLogicTest` |
| Android build + unit tests | GitHub Actions on every push | `.github/workflows/android.yml` |

Regenerating the API fixtures after changing the server: `node server/scripts/capture-fixtures.mjs`.
