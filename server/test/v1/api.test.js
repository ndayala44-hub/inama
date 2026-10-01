// End-to-end HTTP tests for the Inama v1 API on the built-in Node adapter.
// Exercises the MVP journey: sign in with OTP → profile → photo diagnosis → feedback →
// expert escalation → ask a question → weather. Run: npm test.
process.env.INAMA_DEMO_EXPERT_REPLY_SECONDS = "1";
process.env.INAMA_EXPOSE_OTP = "true";
delete process.env.GEMINI_API_KEY;
delete process.env.WEATHER_API_KEY;

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const { buildInamaRouter } = await import("../../src/v1/routes.js");
const { createNodeServer } = await import("../../src/v1/http/nodeAdapter.js");
const { resetStore } = await import("../../src/v1/store/memoryStore.js");

const here = path.dirname(fileURLToPath(import.meta.url));
const fixture = (name) => fs.readFileSync(path.join(here, "fixtures", name));

let server;
let base;
let token;

before(async () => {
  server = createNodeServer(buildInamaRouter(), { store: resetStore() });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  base = `http://127.0.0.1:${server.address().port}/api/v1`;
});
after(() => server.close());

const call = async (method, p, { body, headers = {}, auth = true } = {}) => {
  const isForm = body instanceof FormData;
  const res = await fetch(base + p, {
    method,
    headers: {
      ...(auth && token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body && !isForm ? { "Content-Type": "application/json" } : {}),
      ...headers,
    },
    body: body ? (isForm ? body : JSON.stringify(body)) : undefined,
  });
  return { status: res.status, json: await res.json() };
};

test("health reports demo engines", async () => {
  const { status, json } = await call("GET", "/health", { auth: false });
  assert.equal(status, 200);
  assert.equal(json.data.status, "ok");
  assert.equal(json.data.ai.engine, "inama-kb");
  assert.deepEqual(json.data.languages, ["en"]);
});

test("catalog lists crops, symptoms and crop-specific symptoms", async () => {
  const { json } = await call("GET", "/catalog", { auth: false, headers: { "Accept-Language": "rw-RW, en;q=0.8" } });
  assert.equal(json.data.language, "en", "unsupported languages fall back to English");
  assert.ok(json.data.crops.find((c) => c.id === "cassava"));
  assert.ok(json.data.cropSymptoms.maize.includes("holes"));
});

test("protected routes need a token", async () => {
  const { status, json } = await call("GET", "/diagnoses", { auth: false });
  assert.equal(status, 401);
  assert.equal(json.error.code, "unauthorized");
});

test("OTP sign-in: bad number, wrong code, then success", async () => {
  let r = await call("POST", "/auth/otp/request", { body: { phone: "12345" }, auth: false });
  assert.equal(r.status, 400);
  assert.equal(r.json.error.code, "invalid_phone");

  r = await call("POST", "/auth/otp/request", { body: { phone: "078 812 3456" }, auth: false });
  assert.equal(r.status, 200);
  const code = r.json.data.devCode;
  assert.match(code, /^\d{6}$/);

  r = await call("POST", "/auth/otp/verify", { body: { phone: "+250788123456", code: "000000" === code ? "111111" : "000000" }, auth: false });
  assert.equal(r.status, 400);

  r = await call("POST", "/auth/otp/verify", { body: { phone: "0788123456", code }, auth: false });
  assert.equal(r.status, 200);
  assert.equal(r.json.data.isNew, true);
  assert.equal(r.json.data.farmer.phone, "+250788123456");
  token = r.json.data.token;
});

test("profile update keeps only known crops", async () => {
  const { status, json } = await call("PUT", "/farmers/me", { body: { name: "Claudine Uwase", district: "Bugesera", sector: "Ruhuha", crops: ["maize", "beans", "unicorn"] } });
  assert.equal(status, 200);
  assert.deepEqual(json.data.crops, ["maize", "beans"]);
});

let diagnosisId;
test("photo diagnosis: clear cassava photo with symptoms → bacterial blight", async () => {
  const form = new FormData();
  form.append("image", new Blob([fixture("leaf.jpg")], { type: "image/jpeg" }), "leaf.jpg");
  form.append("cropId", "cassava");
  form.append("symptoms", JSON.stringify(["brown_spots", "drying_tips", "wilting"]));
  form.append("features", JSON.stringify({ brownRatio: 0.11, yellowRatio: 0.05, whiteRatio: 0.01, greenRatio: 0.55, brightness: 0.52, sharpness: 0.6 }));
  form.append("fieldId", "field_1");
  const { status, json } = await call("POST", "/diagnoses", { body: form });
  assert.equal(status, 201);
  const d = json.data;
  assert.equal(d.outcome, "condition");
  assert.equal(d.condition.id, "cassava_bacterial_blight");
  assert.equal(d.headline, "Your cassava may have bacterial blight");
  assert.equal(d.confidence.level, "high");
  assert.equal(d.fieldId, "field_1");
  assert.ok(d.steps.length >= 3);
  assert.ok(d.sources.length >= 1);
  assert.equal(d.engine.id, "inama-kb");
  diagnosisId = d.id;
});

test("photo diagnosis: blurry photo → not sure, escalate, no treatment", async () => {
  const form = new FormData();
  form.append("image", new Blob([fixture("leaf_blurry.jpg")], { type: "image/jpeg" }), "leaf.jpg");
  form.append("cropId", "beans");
  form.append("symptoms", "brown_spots");
  form.append("features", JSON.stringify({ brownRatio: 0.06, greenRatio: 0.5, brightness: 0.5, sharpness: 0.05 }));
  const { json } = await call("POST", "/diagnoses", { body: form });
  assert.equal(json.data.outcome, "uncertain");
  assert.equal(json.data.photoProblem.code, "blurry");
  assert.equal(json.data.escalation.recommended, true);
});

test("photo diagnosis rejects non-images", async () => {
  const form = new FormData();
  form.append("image", new Blob([fixture("not_an_image.txt")], { type: "text/plain" }), "x.txt");
  const { status, json } = await call("POST", "/diagnoses", { body: form });
  assert.equal(status, 400);
  assert.equal(json.error.code, "image_type");
});

test("history, feedback and expert escalation", async () => {
  let r = await call("GET", "/diagnoses");
  assert.equal(r.json.data.total, 2);
  assert.equal(r.json.data.items[0].farmerId, undefined, "internal ids are not leaked");

  r = await call("POST", `/diagnoses/${diagnosisId}/feedback`, { body: { verdict: "correct" } });
  assert.equal(r.json.data.feedback.verdict, "correct");

  r = await call("POST", `/diagnoses/${diagnosisId}/escalate`, { body: { note: "Please check" } });
  assert.equal(r.status, 201);
  assert.equal(r.json.data.status, "sent");
  const caseId = r.json.data.id;

  await new Promise((res) => setTimeout(res, 1100));
  r = await call("GET", `/cases/${caseId}`);
  assert.equal(r.json.data.status, "answered");
  assert.equal(r.json.data.reply.byPerson, true);
  assert.match(r.json.data.reply.text, /bacterial blight/);
});

test("ask a question → structured answer from the knowledge base", async () => {
  const { status, json } = await call("POST", "/advice/ask", { body: { question: "My maize has small holes in the leaves", cropId: "maize" } });
  assert.equal(status, 200);
  assert.equal(json.data.headline, "This sounds like fall armyworm");
  assert.equal(json.data.confidence.level, "medium");
  assert.ok(json.data.steps.length >= 2);
  const list = await call("GET", "/advice/answers");
  assert.equal(list.json.data.total, 1);
});

test("weather with farm decisions (demo forecast without a key)", async () => {
  const { status, json } = await call("GET", "/weather?lat=-2.155&lon=30.097&place=Bugesera", { auth: false });
  assert.equal(status, 200);
  assert.equal(json.data.source, "demo");
  assert.equal(json.data.days.length, 7);
  assert.deepEqual(json.data.decisions.map((d) => d.activity), ["spraying", "weeding", "planting", "drying"]);
});

test("unknown routes return JSON 404", async () => {
  const { status, json } = await call("GET", "/nope", { auth: false });
  assert.equal(status, 404);
  assert.equal(json.success, false);
});
