// Inama v1 request handlers. Each returns { status, body }. Business logic lives in services/.
import { config } from "../../../config/index.js";
import { HttpError } from "../http/core.js";
import { t } from "../i18n.js";
import { loadKnowledgeBase, loadLessons, loadDistricts, conditionById } from "../services/knowledgeBase.js";
import { assessDiagnosis, matchQuestion } from "../services/kbEngine.js";
import { buildDiagnosis, buildAnswer, newId } from "../services/advice.js";
import { diagnoseWithGemini, answerWithGemini, geminiAvailable, geminiEngineInfo } from "../services/geminiAdvisor.js";
import { getWeatherAdvice } from "../services/weatherAdvisor.js";
import { signToken, normalizeRwandaPhone, generateOtp, hashOtp } from "../auth/tokens.js";
import { strip } from "../store/memoryStore.js";

const API_VERSION = "1.0.0";
const kbEngineInfo = (kb) => ({ id: kb.engine.id, version: kb.engine.version, mode: "remote", model: null });
const IMAGE_TYPES = ["image/jpeg", "image/jpg", "image/png", "image/webp"];

// ---------------------------------------------------------------- health & catalog
export async function health({ lang }) {
  const kb = loadKnowledgeBase(lang);
  return {
    body: {
      status: "ok",
      service: "inama-api",
      version: API_VERSION,
      demoMode: config.demoMode,
      ai: geminiAvailable() ? { engine: "gemini", model: config.gemini.model } : { engine: kb.engine.id, model: null },
      weather: { live: Boolean(config.weather.apiKey) },
      languages: config.inama.languages,
      knowledgeBase: { version: kb.engine.version, reviewStatus: kb.reviewStatus },
      time: new Date().toISOString(),
    },
  };
}

export async function catalog({ lang }) {
  const kb = loadKnowledgeBase(lang);
  const cropSymptoms = {};
  for (const c of kb.conditions) {
    const set = cropSymptoms[c.cropId] || new Set();
    Object.keys(c.cues).forEach((s) => set.add(s));
    cropSymptoms[c.cropId] = set;
  }
  return {
    body: {
      language: kb.language,
      reviewStatus: kb.reviewStatus,
      reviewNote: kb.reviewNote,
      crops: kb.crops,
      symptoms: kb.symptoms,
      cropSymptoms: Object.fromEntries(Object.entries(cropSymptoms).map(([k, v]) => [k, [...v]])),
      conditions: kb.conditions.map((c) => ({ id: c.id, cropId: c.cropId, name: c.name, type: c.type })),
    },
  };
}

export async function lessons({ lang }) {
  return { body: loadLessons(lang) };
}

export async function districts() {
  return { body: loadDistricts() };
}

// ---------------------------------------------------------------- auth & profile
export async function requestOtp({ body, lang, store }) {
  const phone = normalizeRwandaPhone(body?.phone);
  if (!phone) throw new HttpError(400, "invalid_phone", t(lang, "errors.invalid_phone"));
  const code = generateOtp();
  const ttl = config.inama.otp.ttlSeconds;
  await store.saveOtp(phone, { hash: hashOtp(phone, code), expiresAt: Date.now() + ttl * 1000, attempts: 0 });
  // TODO(production): send the code through an SMS gateway instead of returning it.
  return {
    body: {
      requestId: newId("otp"),
      phone: `${phone.slice(0, 6)}•• ••• ${phone.slice(-3)}`,
      expiresInSeconds: ttl,
      ...(config.inama.otp.exposeInResponse ? { devCode: code } : {}),
    },
  };
}

export async function verifyOtp({ body, lang, store }) {
  const phone = normalizeRwandaPhone(body?.phone);
  if (!phone) throw new HttpError(400, "invalid_phone", t(lang, "errors.invalid_phone"));
  const record = await store.getOtp(phone);
  if (!record) throw new HttpError(400, "invalid_code", t(lang, "errors.invalid_code"));
  if (record.expiresAt < Date.now()) {
    await store.deleteOtp(phone);
    throw new HttpError(400, "expired_code", t(lang, "errors.expired_code"));
  }
  if (record.attempts >= 5 || record.hash !== hashOtp(phone, String(body?.code || ""))) {
    record.attempts += 1;
    await store.saveOtp(phone, record);
    throw new HttpError(400, "invalid_code", t(lang, "errors.invalid_code"));
  }
  await store.deleteOtp(phone);
  let farmer = await store.findFarmerByPhone(phone);
  const isNew = !farmer;
  if (!farmer) {
    farmer = await store.saveFarmer({ id: newId("fa"), phone, name: "", district: "", sector: "", crops: [], language: lang, createdAt: new Date().toISOString() });
  }
  return { body: { token: signToken({ sub: farmer.id, phone }), farmer, isNew } };
}

export async function getMe({ auth, store }) {
  const farmer = await store.getFarmer(auth.sub);
  if (!farmer) throw new HttpError(401, "unauthorized", "Please sign in again.");
  return { body: farmer };
}

export async function updateMe({ auth, body, lang, store }) {
  const farmer = await store.getFarmer(auth.sub);
  if (!farmer) throw new HttpError(401, "unauthorized", t(lang, "errors.unauthorized"));
  const kb = loadKnowledgeBase(lang);
  const text = (v, max) => (typeof v === "string" ? v.trim().slice(0, max) : undefined);
  const next = { ...farmer };
  if (body?.name !== undefined) next.name = text(body.name, 60) ?? farmer.name;
  if (body?.district !== undefined) next.district = text(body.district, 40) ?? farmer.district;
  if (body?.sector !== undefined) next.sector = text(body.sector, 60) ?? farmer.sector;
  if (Array.isArray(body?.crops)) next.crops = body.crops.filter((c) => kb.crops.some((k) => k.id === c)).slice(0, 12);
  if (typeof body?.language === "string" && config.inama.languages.includes(body.language)) next.language = body.language;
  next.updatedAt = new Date().toISOString();
  return { body: await store.saveFarmer(next) };
}

// ---------------------------------------------------------------- diagnoses
function readList(value) {
  if (!value) return [];
  const s = String(value);
  try {
    const parsed = JSON.parse(s);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return s.split(",").map((x) => x.trim()).filter(Boolean);
  }
}
function readFeatures(value) {
  if (!value) return null;
  try {
    const f = JSON.parse(String(value));
    return f && typeof f === "object" ? f : null;
  } catch {
    return null;
  }
}

export async function createDiagnosis({ auth, form, lang, store }) {
  if (!form) throw new HttpError(400, "image_required", t(lang, "errors.image_required"));
  const image = form.get("image");
  if (!image || typeof image === "string") throw new HttpError(400, "image_required", t(lang, "errors.image_required"));
  if (!IMAGE_TYPES.includes(image.type)) throw new HttpError(400, "image_type", t(lang, "errors.image_type"));
  if (image.size > config.inama.maxImageBytes) throw new HttpError(413, "image_too_big", t(lang, "errors.image_too_big"));

  const kb = loadKnowledgeBase(lang);
  const cropId = kb.crops.some((c) => c.id === form.get("cropId")) ? String(form.get("cropId")) : null;
  const symptoms = readList(form.get("symptoms"));
  const features = readFeatures(form.get("features"));
  const fieldId = form.get("fieldId") ? String(form.get("fieldId")).slice(0, 64) : null;

  let assessment = null;
  let engine = kbEngineInfo(kb);
  let model = null;
  if (geminiAvailable()) {
    const bytes = Buffer.from(await image.arrayBuffer());
    const fromModel = await diagnoseWithGemini(kb, { imageBase64: bytes.toString("base64"), mimeType: image.type, cropId, symptoms, lang });
    if (fromModel) {
      // Keep the on-device photo quality check authoritative when the phone already flagged a bad photo.
      const kbCheck = assessDiagnosis(kb, { cropId, symptoms, features });
      assessment = ["blurry", "dark", "bright"].includes(kbCheck.quality) ? kbCheck : fromModel;
      if (assessment === fromModel) {
        engine = geminiEngineInfo();
        model = fromModel;
      }
    }
  }
  if (!assessment) assessment = assessDiagnosis(kb, { cropId, symptoms, features });

  const diagnosis = buildDiagnosis({ kb, lang, assessment, cropId, fieldId, engine, model });
  diagnosis.image = { bytes: image.size, mimeType: image.type };
  diagnosis.symptoms = symptoms;
  await store.saveDiagnosis(auth.sub, diagnosis);
  return { status: 201, body: diagnosis };
}

export async function listDiagnoses({ auth, store }) {
  const items = (await store.listDiagnoses(auth.sub)).map(strip);
  return { body: { items, total: items.length } };
}

export async function getDiagnosis({ auth, params, lang, store }) {
  const d = await store.getDiagnosis(auth.sub, params.id);
  if (!d) throw new HttpError(404, "not_found", t(lang, "errors.not_found"));
  return { body: strip(d) };
}

export async function diagnosisFeedback({ auth, params, body, lang, store }) {
  const verdict = ["correct", "wrong", "unsure"].includes(body?.verdict) ? body.verdict : null;
  if (!verdict) throw new HttpError(400, "bad_request", t(lang, "errors.bad_request"));
  const updated = await store.updateDiagnosis(auth.sub, params.id, {
    feedback: { verdict, comment: typeof body?.comment === "string" ? body.comment.slice(0, 500) : null, at: new Date().toISOString() },
  });
  if (!updated) throw new HttpError(404, "not_found", t(lang, "errors.not_found"));
  return { body: { id: updated.id, feedback: updated.feedback } };
}

export async function escalateDiagnosis({ auth, params, body, lang, store }) {
  const d = await store.getDiagnosis(auth.sub, params.id);
  if (!d) throw new HttpError(404, "not_found", t(lang, "errors.not_found"));
  const expertCase = {
    id: newId("case"),
    farmerId: auth.sub,
    diagnosisId: d.id,
    note: typeof body?.note === "string" ? body.note.slice(0, 500) : null,
    status: "sent",
    expert: { name: "Your farmer promoter", role: "farmer_promoter" },
    expectedReplyHours: 3,
    createdAt: new Date().toISOString(),
    reply: null,
  };
  await store.saveCase(expertCase);
  await store.updateDiagnosis(auth.sub, d.id, { caseId: expertCase.id });
  return { status: 201, body: strip(expertCase) };
}

export async function getCase({ auth, params, lang, store }) {
  const c = await store.getCase(auth.sub, params.id);
  if (!c) throw new HttpError(404, "not_found", t(lang, "errors.not_found"));
  // Demo only: simulate a human reply so the full loop can be shown end to end.
  if (config.demoMode && c.status !== "answered") {
    const ageSeconds = (Date.now() - Date.parse(c.createdAt)) / 1000;
    if (ageSeconds >= config.inama.demoExpertReplySeconds) {
      const d = await store.getDiagnosis(auth.sub, c.diagnosisId);
      const kb = loadKnowledgeBase(lang);
      const top = d?.candidates?.[0] ? conditionById(kb, d.candidates[0].id) : d?.condition?.id ? conditionById(kb, d.condition.id) : null;
      const text = top ? `It looks like ${top.name.toLowerCase()}. ${top.steps[0]}` : "I can’t see a serious problem. Keep checking the plants each week.";
      c.status = "answered";
      c.reply = { text: t(lang, "expertReply", { text }), conditionId: top?.id || null, at: new Date().toISOString(), byPerson: true };
      await store.saveCase(c);
    } else if (ageSeconds >= Math.min(20, config.inama.demoExpertReplySeconds / 3)) {
      c.status = "seen";
    }
  }
  return { body: strip(c) };
}

// ---------------------------------------------------------------- advice
export async function ask({ auth, body, lang, store }) {
  const question = typeof body?.question === "string" ? body.question.trim().slice(0, 1000) : "";
  if (!question) throw new HttpError(400, "question_required", t(lang, "errors.question_required"));
  const kb = loadKnowledgeBase(lang);
  const cropId = kb.crops.some((c) => c.id === body?.cropId) ? body.cropId : null;
  let answer = null;
  if (geminiAvailable()) {
    const model = await answerWithGemini(kb, { question, cropId, lang });
    if (model) answer = buildAnswer({ kb, lang, question, match: null, engine: geminiEngineInfo(), model });
  }
  if (!answer) answer = buildAnswer({ kb, lang, question, match: matchQuestion(kb, question, cropId), engine: kbEngineInfo(kb) });
  await store.saveAnswer(auth.sub, answer);
  return { body: answer };
}

export async function listAnswers({ auth, store }) {
  const items = (await store.listAnswers(auth.sub)).map(strip);
  return { body: { items, total: items.length } };
}

// ---------------------------------------------------------------- weather
export async function weather({ query, lang }) {
  const lat = query.lat != null && query.lat !== "" ? Number(query.lat) : null;
  const lon = query.lon != null && query.lon !== "" ? Number(query.lon) : null;
  const valid = Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
  const data = await getWeatherAdvice({ lat: valid ? lat : null, lon: valid ? lon : null, place: query.place ? String(query.place).slice(0, 80) : null, lang });
  return { body: data };
}
