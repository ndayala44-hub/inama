// Gemini adapter for Inama. Reuses AgriAI's Gemini transport (geminiVision / geminiGenerate)
// with new prompts that ask the model to CLASSIFY against the reviewed knowledge base.
// The model's JSON is validated and normalised here; anything unexpected returns null so the
// caller falls back to the knowledge-base engine. Swapping Gemini for another model (or a
// Kinyarwanda-capable one) means replacing this file only.
import { geminiGenerate, geminiVision, isConfigured } from "../../services/geminiService.js";
import { config } from "../../../config/index.js";
import { cropById, symptomById } from "./knowledgeBase.js";
import { levelFor } from "./kbEngine.js";

const LANGUAGE_NAMES = { en: "English", rw: "Kinyarwanda", fr: "French" };
const QUALITIES = ["ok", "blurry", "dark", "bright", "not_a_plant"];
const URGENCIES = ["today", "this_week", "watch", "plan"];

export const geminiAvailable = () => isConfigured();
export const geminiEngineInfo = () => ({ id: "gemini", model: config.gemini.model, mode: "remote", version: "v1" });

function stripFences(raw) {
  return String(raw || "")
    .replace(/```json|```/g, "")
    .trim();
}

function parseJson(raw) {
  try {
    const text = stripFences(raw);
    const start = text.indexOf("{");
    const end = text.lastIndexOf("}");
    if (start < 0 || end <= start) return null;
    return JSON.parse(text.slice(start, end + 1));
  } catch {
    return null;
  }
}

const strList = (v, max) =>
  Array.isArray(v) ? v.filter((x) => typeof x === "string" && x.trim()).map((x) => x.trim()).slice(0, max) : [];

export function diagnosisPrompt(kb, { cropId, symptoms, lang }) {
  const crop = cropById(kb, cropId);
  const conditions = kb.conditions.filter((c) => !cropId || c.cropId === cropId);
  const reported = (symptoms || []).map((s) => symptomById(kb, s)?.label).filter(Boolean);
  return [
    `You are Inama, a careful plant-health assistant for smallholder farmers in Rwanda.`,
    `Crop: ${crop ? crop.name : "unknown"}.`,
    `The farmer reports: ${reported.length ? reported.join("; ") : "nothing specific"}.`,
    `Known problems for this crop (use the id exactly):`,
    ...conditions.map((c) => `- ${c.id}: ${c.name} — signs: ${c.signs.join("; ")}`),
    `Look at the photo and respond ONLY with JSON, no markdown:`,
    `{"photoQuality":"ok|blurry|dark|bright|not_a_plant","conditionId":"<one id above, or healthy, or other>","otherName":"<name if other>","confidence":0.0,"signsSeen":["short phrase"],"alternativeIds":["id"],"summary":"one short sentence in ${LANGUAGE_NAMES[lang] || "English"}"}`,
    `Rules: confidence is 0 to 1. Use a value below 0.5 when you are not sure or when two problems fit.`,
    `Never invent ids. Do not give treatment advice.`,
  ].join("\n");
}

/** Validates the model's JSON and converts it into an engine assessment. */
export function normalizeGeminiDiagnosis(raw, kb, { cropId }) {
  const data = parseJson(raw);
  if (!data || typeof data !== "object") return null;
  const quality = QUALITIES.includes(data.photoQuality) ? data.photoQuality : "ok";
  let confidence = Number(data.confidence);
  if (!Number.isFinite(confidence)) return null;
  if (confidence > 1) confidence = confidence / 100; // tolerate 0–100 answers
  confidence = Math.max(0, Math.min(0.97, confidence));
  const allowed = new Set(kb.conditions.filter((c) => !cropId || c.cropId === cropId).map((c) => c.id));
  const id = String(data.conditionId || "");
  const signsSeen = strList(data.signsSeen, 4);
  const candidates = [id, ...strList(data.alternativeIds, 2)]
    .filter((c) => allowed.has(c))
    .map((c, i) => ({ conditionId: c, score: i === 0 ? confidence : confidence / 2 }));
  const common = {
    quality: quality === "ok" ? "ok" : quality,
    score: confidence,
    reasonCode: "model",
    reasonText: typeof data.summary === "string" ? data.summary.trim().slice(0, 160) : "",
    reasonArgs: {},
    matchedSymptoms: [],
    visualHints: [],
    modelSignsSeen: signsSeen,
    candidates,
  };
  if (quality !== "ok") {
    return { ...common, level: "low", score: 0, reasonCode: quality === "not_a_plant" ? "not_a_plant" : `photo_${quality}`, outcome: "uncertain", conditionId: null };
  }
  const level = levelFor(confidence, kb.engine.thresholds);
  if (level === "low") return { ...common, level, outcome: "uncertain", conditionId: null };
  if (id === "healthy") return { ...common, level: level === "high" ? "medium" : level, outcome: "healthy", conditionId: "healthy" };
  if (allowed.has(id)) return { ...common, level, outcome: "condition", conditionId: id };
  if (id === "other" && typeof data.otherName === "string" && data.otherName.trim()) {
    // Unreviewed problem: never report it with high confidence.
    return {
      ...common,
      level: "medium",
      score: Math.min(confidence, 0.7),
      outcome: "condition",
      conditionId: "other",
      otherName: data.otherName.trim().slice(0, 60),
      summary: common.reasonText,
    };
  }
  return null;
}

export async function diagnoseWithGemini(kb, { imageBase64, mimeType, cropId, symptoms, lang }) {
  if (!geminiAvailable()) return null;
  const raw = await geminiVision(imageBase64, mimeType, diagnosisPrompt(kb, { cropId, symptoms, lang }), {
    generationConfig: { temperature: 0.2, responseMimeType: "application/json" },
  });
  if (!raw) return null;
  return normalizeGeminiDiagnosis(raw, kb, { cropId });
}

export function answerPrompt(kb, { question, cropId, lang }) {
  const crop = cropById(kb, cropId);
  return [
    `Farmer's question: ${question}`,
    crop ? `The farmer grows ${crop.name}.` : "",
    `Respond ONLY with JSON, no markdown, written in ${LANGUAGE_NAMES[lang] || "English"}:`,
    `{"headline":"the answer as one short action","whatsHappening":"one sentence","whyItMatters":"one sentence","steps":["2 to 5 short steps"],"urgency":"today|this_week|watch|plan","confidence":0.0,"followUp":"optional short suggestion","relatedConditionId":"optional id from: ${kb.conditions.map((c) => c.id).join(", ")}"}`,
  ]
    .filter(Boolean)
    .join("\n");
}

const ANSWER_SYSTEM = `You are Inama, a practical farming advisor for smallholder farmers in Rwanda.
Rules:
- Use simple words. Farm sizes are small (ares). Money is in Rwandan francs (Frw).
- Never name pesticide brands or doses. Say to use only products on RAB's approved list from a registered agro-dealer, following the label.
- If you are not sure, set confidence below 0.5 and suggest asking the farmer promoter or an agronomist.
- Keep each step to one sentence.`;

export function normalizeGeminiAnswer(raw, kb) {
  const data = parseJson(raw);
  if (!data) return null;
  const steps = strList(data.steps, 5);
  let confidence = Number(data.confidence);
  if (!Number.isFinite(confidence)) confidence = 0.5;
  if (confidence > 1) confidence = confidence / 100;
  confidence = Math.max(0, Math.min(0.95, confidence));
  if (typeof data.headline !== "string" || !data.headline.trim() || steps.length === 0) return null;
  const related = kb.conditions.some((c) => c.id === data.relatedConditionId) ? data.relatedConditionId : null;
  return {
    headline: data.headline.trim().slice(0, 120),
    whatsHappening: String(data.whatsHappening || "").trim().slice(0, 300),
    whyItMatters: String(data.whyItMatters || "").trim().slice(0, 300),
    steps,
    urgency: URGENCIES.includes(data.urgency) ? data.urgency : "watch",
    confidence,
    followUp: typeof data.followUp === "string" && data.followUp.trim() ? data.followUp.trim().slice(0, 160) : null,
    relatedConditionId: related,
  };
}

export async function answerWithGemini(kb, { question, cropId, lang }) {
  if (!geminiAvailable()) return null;
  const raw = await geminiGenerate(answerPrompt(kb, { question, cropId, lang }), [], {
    systemInstruction: ANSWER_SYSTEM,
    generationConfig: { temperature: 0.4, responseMimeType: "application/json" },
  });
  if (!raw) return null;
  return normalizeGeminiAnswer(raw, kb);
}
