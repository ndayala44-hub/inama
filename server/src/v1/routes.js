// Inama v1 route table — the single place that maps URLs to handlers.
// Full request/response examples: docs/API.md.
import { Router } from "./http/core.js";
import * as h from "./handlers/index.js";

export function buildInamaRouter() {
  const r = new Router();
  r.get("/health", h.health);
  r.get("/catalog", h.catalog);
  r.get("/content/lessons", h.lessons);
  r.get("/content/districts", h.districts);

  r.post("/auth/otp/request", h.requestOtp);
  r.post("/auth/otp/verify", h.verifyOtp);
  r.get("/farmers/me", h.getMe, { auth: true });
  r.put("/farmers/me", h.updateMe, { auth: true });

  r.post("/diagnoses", h.createDiagnosis, { auth: true });
  r.get("/diagnoses", h.listDiagnoses, { auth: true });
  r.get("/diagnoses/:id", h.getDiagnosis, { auth: true });
  r.post("/diagnoses/:id/feedback", h.diagnosisFeedback, { auth: true });
  r.post("/diagnoses/:id/escalate", h.escalateDiagnosis, { auth: true });
  r.get("/cases/:id", h.getCase, { auth: true });

  r.post("/advice/ask", h.ask, { auth: true });
  r.get("/advice/answers", h.listAnswers, { auth: true });

  r.get("/weather", h.weather);
  return r;
}
