# Inama — AI farm advice for Rwandan farmers (Android MVP)

Inama helps smallholder farmers check a sick plant with a photo, ask questions by voice, and know what to do on their fields this week. Every answer says how sure it is. When Inama is not sure, it gives only safe steps and offers to send the case to a farmer promoter.

This repository has the **English-first MVP**: an Android app built from the Inama UI/UX designs, and a backend that extends the open-source [AgriAI Advisory System](https://github.com/anvi-sha675/AgriAI-Advisory-System). The code is structured so that Kinyarwanda, French and other languages can be added without changing how the app works ([how](docs/ARCHITECTURE.md#4-adding-a-language-kinyarwanda-french-)).

```
android/   Kotlin + Jetpack Compose app (minSdk 26)
server/    Node.js backend: AgriAI (reused) + the new Inama API at /api/v1
shared/    Advisory knowledge base, lessons, districts. Used by BOTH the app (bundled) and the server
docs/      ARCHITECTURE.md · API.md · REUSE.md (what came from AgriAI vs what is new) · TESTING.md
.github/   CI: Android unit tests + debug APK, server tests
```

## The core journey

1. **Onboarding**: choose a language, see the privacy and consent screen, sign in with a phone number and SMS code, add a profile and your first field.
2. **Home**: see today's priority, a weather decision ("Don't spray, rain is coming"), your fields and this week's tasks. A briefing can be read aloud.
3. **Check a sick plant**: take a photo (or pick one from the gallery, or use a bundled sample). The phone checks sharpness and light, then you choose the crop and tick the signs you saw.
4. **AI analysis**: the Inama server (Gemini) is used when online. With no signal, the knowledge-base engine answers on the phone.
5. **Diagnosis**: the likely problem, how sure Inama is and why, what's happening / why it matters / what to do, and the evidence and sources. You can add the steps to your tasks, give feedback, or send the case to a person.
6. **History and profile**: past checks and questions, fields, tasks, settings (language, read-aloud, text size, high contrast, data saving, AI mode, server) and help.

Screens handle loading, empty, error and offline cases (for example: sample forecast when there is no signal, on-device answers, "take a better photo").

## Run it

**Only have a phone or tablet?** Follow [docs/TESTING.md](docs/TESTING.md): GitHub builds the APK and a Codespace runs the server.

### 1. Server (optional: the app also works fully offline)

Requires Node.js 20+.

```bash
cd server
npm install
cp .env.example .env        # optional: add GEMINI_API_KEY and WEATHER_API_KEY
npm run dev:inama           # Inama API on http://localhost:5055/api/v1  (no MongoDB needed)
npm test                    # 31 API tests
```

Without keys the server still works: questions and photo checks use the knowledge-base engine, and the forecast is a clearly labelled sample. `npm start` runs the full AgriAI Express server with the Inama API mounted at `/api/v1`. The original AgriAI routes need `MONGODB_URI`.

### 2. Android app

Requires Android Studio (Ladybug or newer) or the command line with JDK 17 and the Android SDK (API 35).

```bash
cd android
./gradlew testDebugUnitTest     # JVM unit tests
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug          # onto a connected phone or emulator
```

Or open `android/` in Android Studio and press Run.

- **Emulator + local server**: works out of the box (`http://10.0.2.2:5055/api/v1`).
- **Phone on the same Wi-Fi**: build with `./gradlew installDebug -Pinama.apiBaseUrl=http://<your-computer-ip>:5055/api/v1`, or change the address in the app under Settings › Server.
- **No server at all**: sign in with the offline demo code **123456**. Everything runs on the phone.

The GitHub Actions workflow builds the debug APK on every push and attaches it as the `inama-debug-apk` artifact.

### Demo tips

- In the photo check, tap **Samples**: *Cassava leaf with spots* shows a sick-leaf result, *Healthy cassava leaf* a calm one, and *Blurry bean leaf* the "take a better photo" flow.
- On an uncertain result, tap **Ask a farmer promoter to check**. A demo reply arrives after about 90 seconds.
- **Settings › Reset all data** starts over from onboarding.

## Configuration

| Where | Setting | Default |
|---|---|---|
| `android/gradle.properties` or `-P` | `inama.apiBaseUrl` | `http://10.0.2.2:5055/api/v1` |
| | `inama.offlineDemoCode` | `123456` |
| | `inama.helpline` | placeholder number |
| `server/.env` | `GEMINI_API_KEY`, `GEMINI_MODEL` | none (knowledge-base engine), `gemini-2.5-flash-lite` |
| | `WEATHER_API_KEY` (OpenWeatherMap) | none (sample forecast) |
| | `JWT_SECRET` | random per run in development; required in production |
| | `INAMA_LANGUAGES`, `INAMA_EXPOSE_OTP`, `INAMA_PORT`… | see `server/.env.example` |

API keys never reach the phone. The app talks only to the Inama server.

## How it is built (short version)

- **Layers**: `ui`/`feature` (Compose screens + ViewModels) → `domain` (pure Kotlin models, use cases, rules) → `data` (phone storage, API client) plus `core` (camera, speech, connectivity, config). Dependencies are wired by hand in `di/AppContainer.kt`.
- **One AI seam**: `AdvisoryEngine` has a server implementation (Gemini via the Inama API) and an on-device implementation (knowledge-base engine). A fallback wrapper switches between them automatically. New models, including Kinyarwanda-capable ones, plug in there.
- **The AI classifies, the knowledge base advises**: the advice text farmers read comes from `shared/knowledge/advisory_kb.en.json`, shared by phone and server, never from free-form model output.
- **Ready for more languages**: domain objects carry codes, not sentences. Text comes from `strings.xml` and per-language content files, and `Accept-Language` is sent on every request.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the decisions and trade-offs, [docs/API.md](docs/API.md) for the endpoints, and [docs/REUSE.md](docs/REUSE.md) for exactly what was reused from AgriAI.

## Verification status

| Check | Result |
|---|---|
| Server API tests (`npm test`) | 31/31 pass |
| Android domain and data unit tests (19) | Pass, run on the JVM with the Kotlin 2.0.21 compiler |
| API contract | App mappers tested against JSON captured from the running server |
| Engine parity, phone ↔ server | Shared test vectors pass on both |
| Full Android sources (62 Kotlin files) | Compile with 0 errors against signature stubs of the pinned Compose/AndroidX/CameraX/Coil versions (K2 compiler with the Compose plugin's checks) |
| Gradle build and APK | **Not run in the authoring environment**, which had no access to Google's Maven repository. Run `./gradlew assembleDebug` locally or let CI build it |

## Known limitations (MVP)

- **Content is a draft.** The knowledge base and lessons need review by Rwandan agronomists before field use (`reviewStatus: "draft"`).
- **No SMS gateway yet.** In development the code is shown in the app, and the offline demo code is for demos only.
- **Server data is in memory.** It resets on restart, and photos are never stored. The app keeps the farmer's own data on the phone, but there is no sync yet.
- **Expert replies are simulated.** A promoter/agronomist dashboard is the next step.
- **English only in this version.** Kinyarwanda and French appear as "coming soon". Most phones lack Kinyarwanda speech engines, so voice will need a server-side speech service.
- **Licence of the starting point.** The AgriAI repository has no LICENSE file. Get the authors' permission before a public release (details in [REUSE.md](docs/REUSE.md)).

Fonts: Bricolage Grotesque and Atkinson Hyperlegible, both under the SIL Open Font License (`android/licenses/`).
