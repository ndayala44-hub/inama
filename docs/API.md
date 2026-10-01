# Inama API v1

Base path: `/api/v1`. Served two ways from the same handlers (`server/src/v1`):

- `npm run dev:inama` — zero-dependency Node server on port `INAMA_PORT` (default **5055**). Good for local work and the Android emulator (`http://10.0.2.2:5055/api/v1`).
- `npm start` — the full AgriAI Express server, with v1 mounted at `/api/v1` next to the original `/api/*` routes.

Real responses captured from the server are kept in `android/app/src/test/resources/api/` and used by the Android mapping tests.

## Conventions

| | |
|---|---|
| Envelope | Success: `{"success": true, "data": …}`. Error: `{"success": false, "error": {"code": "invalid_phone", "message": "…"}}` |
| Language | Send `Accept-Language: en` (or `rw`, `fr` once enabled). Unsupported languages fall back to `INAMA_DEFAULT_LANGUAGE`. The chosen language is echoed in `language` fields. |
| Auth | `Authorization: Bearer <token>` from `/auth/otp/verify`. Endpoints marked 🔒 require it. |
| Codes, not just text | Wherever the app shows its own wording, responses carry a code (`reasonCode`, `verdict`, `urgency`, alert `code`) as well as English text. The app renders the code through its string resources. |
| Errors | 400 `bad_request`, `bad_json`, `invalid_phone`, `invalid_code`, `expired_code`, `image_required`, `image_type`, `question_required` · 401 `unauthorized` · 404 `not_found` · 405 `method_not_allowed` · 413 `image_too_big` · 5xx → the app treats it as "server unavailable" and answers on the phone. |

## Health & content

### `GET /health`
```json
{"status":"ok","service":"inama-api","version":"1.0.0","demoMode":true,
 "ai":{"engine":"gemini","model":"gemini-2.5-flash-lite"},
 "weather":{"live":false},"languages":["en"],
 "knowledgeBase":{"version":"1.0.0","reviewStatus":"draft"},"time":"…"}
```
`ai.engine` is `inama-kb` when no Gemini key is configured. Used by Settings › Server › Test.

### `GET /catalog`
Crops (with stages and stage tips), symptoms, `cropSymptoms` (symptom ids per crop) and a condition index from the knowledge base in the request language, plus `reviewStatus`.

### `GET /content/lessons` · `GET /content/districts`
The bundled lesson and district files (`shared/content`). The app ships the same files, so these are only needed for updates.

## Sign-in (phone number + SMS code)

### `POST /auth/otp/request`
```json
{"phone": "0788123456"}
```
→ `{"requestId":"otp_…","phone":"+25078•• ••• 456","expiresInSeconds":300,"devCode":"149070"}`

`devCode` is returned only while `INAMA_EXPOSE_OTP=true` (development — there is no SMS gateway in the MVP). Numbers are normalised to `+2507XXXXXXXX`.

### `POST /auth/otp/verify`
```json
{"phone": "0788123456", "code": "149070"}
```
→ `{"token":"<JWT>","farmer":{"id":"fa_…","phone":"+250788123456","name":"","district":"","sector":"","crops":[],"language":"en","createdAt":"…"},"isNew":true}`

### 🔒 `GET /farmers/me` · 🔒 `PUT /farmers/me`
`PUT` accepts any of `name`, `district`, `sector`, `crops` (knowledge-base crop ids), `language`.

## Photo check

### 🔒 `POST /diagnoses` — `multipart/form-data`
| Field | |
|---|---|
| `image` | JPEG/PNG/WebP, ≤ `INAMA_MAX_IMAGE_BYTES` (8 MB). The photo is analysed and **not stored**. |
| `cropId` | Knowledge-base crop id (e.g. `cassava`). Optional but strongly recommended. |
| `symptoms` | JSON array of symptom ids the farmer ticked, e.g. `["brown_spots","wilting"]`. |
| `features` | JSON object measured on the phone: `brownRatio`, `yellowRatio`, `whiteRatio`, `greenRatio`, `brightness`, `sharpness` (0–1). |
| `fieldId` | The app's field id (opaque to the server). |

Response (abridged, a confident result):
```json
{"id":"dg_…","language":"en","crop":{"id":"cassava","name":"Cassava"},"fieldId":"field_1",
 "outcome":"condition",
 "condition":{"id":"cassava_bacterial_blight","name":"Cassava bacterial blight","type":"disease"},
 "headline":"Your cassava may have bacterial blight",
 "confidence":{"level":"high","score":0.83,"reasonCode":"photo_and_signs","count":3,"reason":"your photo and 3 of your signs agree"},
 "urgency":"this_week",
 "whatsHappening":"…","whyItMatters":"…","steps":["…"],
 "evidence":{"matched":[{"kind":"symptom","id":"brown_spots","label":"Brown spots on leaves"},{"kind":"photo","id":"brown","label":"Brown areas in the photo"}],
             "lookFor":"Sticky gum on stems — look for it","signs":["…"]},
 "candidates":[…],"lessLikely":[…],"sources":["…"],
 "escalation":{"recommended":false,"reasonCode":null,"reason":null},
 "photoProblem":null,
 "engine":{"id":"gemini","version":"v1","mode":"remote","model":"gemini-2.5-flash-lite"},
 "image":{"bytes":184233,"mimeType":"image/jpeg"},"symptoms":["brown_spots","wilting"]}
```

- `outcome`: `condition` | `healthy` | `uncertain`.
- `urgency`: `today` | `this_week` | `watch` | `plan`.
- `confidence.reasonCode`: `signs_matched`, `photo_and_signs`, `photo_only`, `ambiguous`, `photo_blurry`, `photo_dark`, `photo_bright`, `not_a_plant`, `no_signs`, `healthy_looking`, `model`.
- When uncertain, `steps` are the safe interim steps, `candidates` lists what it might be, and `escalation.recommended` is `true`.
- `photoProblem` (`blurry` / `dark` / `bright`) carries retake tips.
- `engine.id` is `gemini` when the model answered, `inama-kb` when the knowledge-base engine did (no key, model error, or invalid model output).

### 🔒 `GET /diagnoses` · 🔒 `GET /diagnoses/:id`
The farmer's diagnoses, newest first / one diagnosis.

### 🔒 `POST /diagnoses/:id/feedback`
`{"verdict": "correct" | "wrong" | "unsure", "comment": "optional"}`

### 🔒 `POST /diagnoses/:id/escalate`
`{"note": "optional"}` → a case:
```json
{"id":"case_…","diagnosisId":"dg_…","note":"Please check","status":"sent",
 "expert":{"name":"Your farmer promoter","role":"farmer_promoter"},
 "expectedReplyHours":3,"createdAt":"…","reply":null}
```

### 🔒 `GET /cases/:id`
`status` moves `sent` → `seen` → `answered`. In the MVP a demo reply built from the knowledge base arrives after `INAMA_DEMO_EXPERT_REPLY_SECONDS` (90 s); a real promoter/agronomist dashboard replaces this.
```json
"reply":{"text":"I looked at your photos…","conditionId":"cassava_bacterial_blight","at":"…","byPerson":true}
```

## Questions

### 🔒 `POST /advice/ask`
`{"question": "My maize has small holes in the leaves", "cropId": "maize"}`
```json
{"id":"an_…","language":"en","question":"…",
 "headline":"This sounds like fall armyworm",
 "confidence":{"level":"medium","score":0.62,"reasonCode":"kb_match","reason":"…"},
 "urgency":"today","whatsHappening":"…","whyItMatters":"…","steps":["…"],
 "followUp":"Add a photo so Inama can be surer.",
 "relatedConditionId":"maize_fall_armyworm","sources":["…"],
 "engine":{"id":"inama-kb","version":"1.0.0","mode":"remote","model":null}}
```

### 🔒 `GET /advice/answers`
The farmer's answers, newest first.

## Weather

### `GET /weather?place=Bugesera&lat=-2.17&lon=30.14`
Five days built from the OpenWeatherMap 3-hourly forecast (`WEATHER_API_KEY`), or a deterministic sample forecast for Rwanda when no key is set (`source: "demo"`).
```json
{"place":"Bugesera","source":"live",
 "current":{"tempC":24,"windKph":11,"humidity":66,"condition":"sunny","rainChance":10},
 "days":[{"date":"2026-09-30","label":"Today","highC":27,"lowC":17,"rainChance":10,"rainMm":0,"condition":"sunny"}],
 "decisions":[{"activity":"spraying","label":"Spraying","verdict":"go","reasonCode":"spray_go","reason":"…"}],
 "alerts":[{"code":"heavy_rain","date":"2026-10-02","message":"Heavy rain expected on Fri."}],
 "updatedAt":"…"}
```
- `condition`: `sunny` | `partly_cloudy` | `rain` | `storm`.
- `decisions[].activity`: `spraying` | `weeding` | `planting` | `drying`; `verdict`: `go` | `careful` | `wait`.
- `decisions[].reasonCode`: `spray_rain`, `spray_wind`, `spray_go`, `weed_wet`, `weed_go`, `plant_rain`, `plant_dry`, `plant_mixed`, `dry_rain`, `dry_go`.
- `alerts[].code`: `heavy_rain` | `heat` | `dry_spell`.

The same decision rules run on the phone (`domain/weather/FarmDecisions.kt`) for the offline sample forecast.
