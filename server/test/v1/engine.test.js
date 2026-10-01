// Unit tests for the Inama advisory core: knowledge-base engine, Gemini normalisation and
// weather decisions. Run: npm test (or node --test test/).
import { test } from "node:test";
import assert from "node:assert/strict";
import { loadKnowledgeBase, loadTestVectors } from "../../src/v1/services/knowledgeBase.js";
import { assessDiagnosis, matchQuestion } from "../../src/v1/services/kbEngine.js";
import { buildDiagnosis } from "../../src/v1/services/advice.js";
import { normalizeGeminiDiagnosis, normalizeGeminiAnswer } from "../../src/v1/services/geminiAdvisor.js";
import { decide, demoWeather } from "../../src/v1/services/weatherAdvisor.js";

const kb = loadKnowledgeBase("en");

test("knowledge base is internally consistent", () => {
  const cropIds = new Set(kb.crops.map((c) => c.id));
  const symptomIds = new Set(kb.symptoms.map((s) => s.id));
  for (const c of kb.conditions) {
    assert.ok(cropIds.has(c.cropId), `${c.id} crop`);
    for (const s of Object.keys(c.cues)) assert.ok(symptomIds.has(s), `${c.id} cue ${s}`);
    assert.ok(c.steps.length >= 2, `${c.id} steps`);
    assert.ok(c.headline && c.whatsHappening && c.whyItMatters, `${c.id} text`);
  }
});

// Shared with the Android app's Kotlin engine (KnowledgeBaseEngineTest).
for (const v of loadTestVectors().vectors) {
  test(`engine vector: ${v.name}`, () => {
    const a = assessDiagnosis(kb, { cropId: v.cropId, symptoms: v.symptoms, features: v.features });
    if (v.expect.level) assert.equal(a.level, v.expect.level);
    if (v.expect.levelNot) assert.notEqual(a.level, v.expect.levelNot);
    if (v.expect.conditionId) assert.equal(a.conditionId, v.expect.conditionId);
    if (v.expect.level === "low") assert.equal(a.outcome, "uncertain");
    if (v.expect.quality) assert.equal(a.quality, v.expect.quality);
  });
}

test("low confidence never returns treatment steps and always escalates", () => {
  const a = assessDiagnosis(kb, { cropId: "beans", symptoms: ["brown_spots"], features: { sharpness: 0.01, brightness: 0.5 } });
  const d = buildDiagnosis({ kb, lang: "en", assessment: a, cropId: "beans", engine: { id: "test" } });
  assert.equal(d.outcome, "uncertain");
  assert.deepEqual(d.steps, kb.lowConfidence.steps);
  assert.equal(d.escalation.recommended, true);
  assert.equal(d.photoProblem.code, "blurry");
  assert.ok(d.candidates.length > 0, "shows what it could be");
});

test("high-confidence diagnosis uses reviewed KB advice", () => {
  const a = assessDiagnosis(kb, { cropId: "maize", symptoms: ["holes", "frass"] });
  const d = buildDiagnosis({ kb, lang: "en", assessment: a, cropId: "maize", engine: { id: "test" } });
  assert.equal(d.condition.id, "maize_fall_armyworm");
  assert.equal(d.headline, "Your maize may have fall armyworm");
  assert.equal(d.confidence.level, "high");
  assert.match(d.confidence.reason, /2 of the signs/);
  assert.equal(d.urgency, "today");
  assert.deepEqual(d.evidence.matched.map((e) => e.id), ["holes", "frass"]);
});

test("question matcher ports AgriAI keyword fallback", () => {
  assert.equal(matchQuestion(kb, "My maize has holes in the leaves", "maize").id, "q_armyworm");
  assert.equal(matchQuestion(kb, "When should I spray, is rain coming?").id, "q_rain_spray");
  assert.equal(matchQuestion(kb, "hello"), null);
});

test("Gemini diagnosis JSON is validated and mapped to KB ids", () => {
  const raw = '```json\n{"photoQuality":"ok","conditionId":"cassava_bacterial_blight","confidence":0.86,"signsSeen":["angular brown spots"],"alternativeIds":["cassava_brown_streak"],"summary":"Brown angular spots and drying tips."}\n```';
  const a = normalizeGeminiDiagnosis(raw, kb, { cropId: "cassava" });
  assert.equal(a.outcome, "condition");
  assert.equal(a.conditionId, "cassava_bacterial_blight");
  assert.equal(a.level, "high");
  assert.deepEqual(a.modelSignsSeen, ["angular brown spots"]);
});

test("Gemini: invented ids fall back, unsure answers become low, 0–100 scale tolerated", () => {
  assert.equal(normalizeGeminiDiagnosis('{"photoQuality":"ok","conditionId":"made_up","confidence":0.9}', kb, { cropId: "maize" }), null);
  const unsure = normalizeGeminiDiagnosis('{"photoQuality":"ok","conditionId":"beans_rust","confidence":0.3}', kb, { cropId: "beans" });
  assert.equal(unsure.level, "low");
  assert.equal(unsure.outcome, "uncertain");
  const pct = normalizeGeminiDiagnosis('{"photoQuality":"ok","conditionId":"beans_rust","confidence":80}', kb, { cropId: "beans" });
  assert.equal(pct.level, "high");
  const blurry = normalizeGeminiDiagnosis('{"photoQuality":"blurry","conditionId":"beans_rust","confidence":0.9}', kb, { cropId: "beans" });
  assert.equal(blurry.reasonCode, "photo_blurry");
  const other = normalizeGeminiDiagnosis('{"photoQuality":"ok","conditionId":"other","otherName":"Leaf scorch","confidence":0.95}', kb, { cropId: "beans" });
  assert.equal(other.level, "medium", "unreviewed problems are capped at medium");
  assert.equal(normalizeGeminiDiagnosis("not json", kb, { cropId: "beans" }), null);
});

test("Gemini answer JSON is validated", () => {
  const ok = normalizeGeminiAnswer('{"headline":"Weed before the rain","whatsHappening":"x","whyItMatters":"y","steps":["a","b"],"urgency":"this_week","confidence":0.8}', kb);
  assert.equal(ok.urgency, "this_week");
  assert.equal(normalizeGeminiAnswer('{"headline":"","steps":[]}', kb), null);
  assert.equal(normalizeGeminiAnswer('{"headline":"x","steps":["a"],"urgency":"soon"}', kb).urgency, "watch");
});

test("weather decisions: rain stops spraying, dry spell raises an alert", () => {
  const rainy = { current: { windKph: 5, tempC: 24 }, days: [{ label: "Today", date: "2026-10-01", rainChance: 80 }, { label: "Fri", rainChance: 60 }, { label: "Sat", rainChance: 30 }] };
  const r = decide(rainy);
  assert.equal(r.decisions.find((d) => d.activity === "spraying").verdict, "wait");
  assert.equal(r.decisions.find((d) => d.activity === "weeding").verdict, "wait");
  assert.ok(r.alerts.some((a) => a.code === "heavy_rain"));
  const dry = { current: { windKph: 20, tempC: 33 }, days: Array.from({ length: 5 }, (_, i) => ({ label: `D${i}`, rainChance: 5 })) };
  const d = decide(dry);
  assert.equal(d.decisions.find((x) => x.activity === "spraying").verdict, "careful");
  assert.equal(d.decisions.find((x) => x.activity === "planting").verdict, "wait");
  assert.ok(d.alerts.some((a) => a.code === "dry_spell"));
  assert.ok(d.alerts.some((a) => a.code === "heat"));
});

test("demo weather is deterministic and has 7 days", () => {
  const now = new Date("2026-09-30T08:00:00Z");
  const a = demoWeather("Bugesera", now);
  const b = demoWeather("Bugesera", now);
  assert.deepEqual(a.days, b.days);
  assert.equal(a.days.length, 7);
  assert.equal(a.days[0].label, "Today");
});
