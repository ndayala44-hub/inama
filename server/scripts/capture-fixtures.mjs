// Captures real Inama v1 API responses as JSON fixtures for the Android app's mapper tests
// (android/app/src/test/resources/api). Run: node scripts/capture-fixtures.mjs
process.env.INAMA_DEMO_EXPERT_REPLY_SECONDS = "1";
process.env.INAMA_EXPOSE_OTP = "true";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
const { buildInamaRouter } = await import("../src/v1/routes.js");
const { createNodeServer } = await import("../src/v1/http/nodeAdapter.js");
const { resetStore } = await import("../src/v1/store/memoryStore.js");

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "../../android/app/src/test/resources/api");
fs.mkdirSync(out, { recursive: true });
const server = createNodeServer(buildInamaRouter(), { store: resetStore() });
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const base = `http://127.0.0.1:${server.address().port}/api/v1`;
let token = null;
const call = async (method, p, body) => {
  const isForm = body instanceof FormData;
  const res = await fetch(base + p, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body && !isForm ? { "Content-Type": "application/json" } : {}) },
    body: body ? (isForm ? body : JSON.stringify(body)) : undefined,
  });
  return res.json();
};
const save = (name, json) => fs.writeFileSync(path.join(out, name), JSON.stringify(json, null, 1));

const otp = await call("POST", "/auth/otp/request", { phone: "0788123456" });
save("otp_request.json", otp);
const verify = await call("POST", "/auth/otp/verify", { phone: "0788123456", code: otp.data.devCode });
save("otp_verify.json", verify);
token = verify.data.token;
const leaf = fs.readFileSync(path.resolve(here, "../test/v1/fixtures/leaf.jpg"));
const form = new FormData();
form.append("image", new Blob([leaf], { type: "image/jpeg" }), "leaf.jpg");
form.append("cropId", "cassava");
form.append("fieldId", "field_1");
form.append("symptoms", JSON.stringify(["brown_spots", "drying_tips", "wilting"]));
form.append("features", JSON.stringify({ brownRatio: 0.11, yellowRatio: 0.05, whiteRatio: 0.01, greenRatio: 0.55, brightness: 0.52, sharpness: 0.6 }));
const diag = await call("POST", "/diagnoses", form);
save("diagnosis_condition.json", diag);
const form2 = new FormData();
form2.append("image", new Blob([leaf], { type: "image/jpeg" }), "leaf.jpg");
form2.append("cropId", "beans");
form2.append("symptoms", "brown_spots");
form2.append("features", JSON.stringify({ brownRatio: 0.06, greenRatio: 0.5, brightness: 0.5, sharpness: 0.05 }));
save("diagnosis_uncertain.json", await call("POST", "/diagnoses", form2));
const esc = await call("POST", `/diagnoses/${diag.data.id}/escalate`, { note: "Please check" });
save("case_sent.json", esc);
await new Promise((r) => setTimeout(r, 1100));
save("case_answered.json", await call("GET", `/cases/${esc.data.id}`));
save("answer.json", await call("POST", "/advice/ask", { question: "My maize has small holes in the leaves", cropId: "maize" }));
save("weather.json", await call("GET", "/weather?lat=-2.155&lon=30.097&place=Bugesera"));
save("error_invalid_phone.json", await call("POST", "/auth/otp/request", { phone: "123" }));
server.close();
console.log("fixtures written to", out, fs.readdirSync(out));
