// Turns an engine assessment (from the KB engine or from Gemini) into the Inama advice shape
// used by the app: headline → confidence in words → what's happening / why it matters / what to do.
// Advice text comes from the reviewed knowledge base whenever the condition is known, so a model
// classifies the problem but does not write treatment instructions on its own.
import crypto from "node:crypto";
import { conditionById, cropById, symptomById } from "./knowledgeBase.js";
import { t } from "../i18n.js";

export const newId = (prefix) => `${prefix}_${crypto.randomBytes(8).toString("hex")}`;
const round2 = (x) => Math.round(x * 100) / 100;

function confidenceBlock(assessment, lang) {
  const text =
    assessment.reasonCode === "model"
      ? assessment.reasonText || ""
      : t(lang, `reason.${assessment.reasonCode}`, assessment.reasonArgs || {});
  return {
    level: assessment.level,
    score: round2(assessment.score || 0),
    reasonCode: assessment.reasonCode,
    count: assessment.reasonArgs?.count ?? 0,
    reason: text,
  };
}

function photoProblem(kb, quality) {
  if (!["blurry", "dark", "bright", "not_a_plant"].includes(quality)) return null;
  const p = kb.photoProblems[quality] || kb.photoProblems.blurry;
  return { code: quality, title: p.title, body: p.body, tips: kb.photoProblems.tips };
}

export function buildDiagnosis({ kb, lang, assessment, cropId, fieldId = null, engine, model = null }) {
  const crop = cropById(kb, cropId);
  const cropName = crop ? crop.name : "plant";
  const evidence = [
    ...(assessment.matchedSymptoms || []).map((id) => ({
      kind: "symptom",
      id,
      label: symptomById(kb, id)?.label || id,
    })),
    ...(assessment.visualHints || []).map((id) => ({ kind: "photo", id, label: t(lang, `photoHint.${id}`) })),
    ...(assessment.modelSignsSeen || []).map((label) => ({ kind: "model", id: null, label })),
  ];

  const base = {
    id: newId("dg"),
    createdAt: new Date().toISOString(),
    language: lang,
    crop: { id: cropId || null, name: cropName },
    fieldId,
    confidence: confidenceBlock(assessment, lang),
    photoProblem: photoProblem(kb, assessment.quality),
    engine,
  };

  const candidateList = (assessment.candidates || [])
    .map((c) => conditionById(kb, c.conditionId))
    .filter(Boolean)
    .map((c) => ({ id: c.id, name: c.name }));

  if (assessment.outcome === "condition" && assessment.conditionId === "other" && model?.otherName) {
    return {
      ...base,
      outcome: "condition",
      condition: { id: null, name: model.otherName, type: "unknown" },
      headline: `Your ${cropName.toLowerCase()} may have ${model.otherName.toLowerCase()}`,
      urgency: "this_week",
      whatsHappening: model.summary || kb.lowConfidence.whatsHappening,
      whyItMatters: t(lang, "otherWhy"),
      steps: t(lang, "otherSteps"),
      evidence: { matched: evidence, lookFor: null },
      candidates: [],
      lessLikely: [],
      sources: [],
      escalation: { recommended: true, reasonCode: "unvetted", reason: t(lang, "escalation.unvetted") },
    };
  }

  if (assessment.outcome === "condition") {
    const cond = conditionById(kb, assessment.conditionId);
    const lessLikely = candidateList.filter((c) => c.id !== cond.id).slice(0, 1);
    const urgentButUnsure = cond.urgency === "today" && assessment.level !== "high";
    return {
      ...base,
      outcome: "condition",
      condition: { id: cond.id, name: cond.name, type: cond.type },
      headline: cond.headline,
      urgency: cond.urgency,
      whatsHappening: cond.whatsHappening,
      whyItMatters: cond.whyItMatters,
      steps: cond.steps,
      evidence: { matched: evidence, lookFor: cond.notSeenHint || null, signs: cond.signs },
      candidates: [],
      lessLikely,
      sources: cond.sources,
      escalation: urgentButUnsure
        ? { recommended: true, reasonCode: "urgent", reason: t(lang, "escalation.urgent") }
        : { recommended: false, reasonCode: null, reason: null },
    };
  }

  if (assessment.outcome === "healthy") {
    return {
      ...base,
      outcome: "healthy",
      condition: null,
      headline: kb.healthy.headline.replace("{crop}", cropName.toLowerCase()),
      urgency: "watch",
      whatsHappening: kb.healthy.whatsHappening,
      whyItMatters: kb.healthy.whyItMatters,
      steps: kb.healthy.steps,
      evidence: { matched: evidence, lookFor: null },
      candidates: [],
      lessLikely: [],
      sources: [],
      escalation: { recommended: false, reasonCode: null, reason: null },
    };
  }

  // Uncertain: never give treatment steps — safe interim actions and a person to ask.
  return {
    ...base,
    outcome: "uncertain",
    condition: null,
    headline: kb.lowConfidence.headline,
    urgency: "watch",
    whatsHappening: base.photoProblem ? base.photoProblem.body : kb.lowConfidence.whatsHappening,
    whyItMatters: kb.lowConfidence.whyItMatters,
    steps: kb.lowConfidence.steps,
    evidence: { matched: evidence, lookFor: null },
    candidates: candidateList.slice(0, 2),
    lessLikely: [],
    sources: [],
    escalation: { recommended: true, reasonCode: "low_confidence", reason: t(lang, "escalation.low_confidence") },
  };
}

export function buildAnswer({ kb, lang, question, match, engine, model = null }) {
  const thresholds = kb.engine.thresholds;
  const levelOf = (score) => (score >= thresholds.high ? "high" : score >= thresholds.medium ? "medium" : "low");
  const common = { id: newId("an"), createdAt: new Date().toISOString(), language: lang, question, engine };

  if (model) {
    return {
      ...common,
      headline: model.headline,
      confidence: { level: levelOf(model.confidence), score: round2(model.confidence), reasonCode: "model", reason: model.reason || "" },
      urgency: model.urgency,
      whatsHappening: model.whatsHappening,
      whyItMatters: model.whyItMatters,
      steps: model.steps,
      followUp: model.followUp || null,
      relatedConditionId: model.relatedConditionId || null,
      sources: model.relatedConditionId ? conditionById(kb, model.relatedConditionId)?.sources || [] : [],
    };
  }
  const src = match || kb.defaultAnswer;
  return {
    ...common,
    headline: src.headline,
    confidence: {
      level: levelOf(src.confidence),
      score: round2(src.confidence),
      reasonCode: match ? "kb_match" : "general",
      reason: t(lang, match ? "reason.kb_match" : "reason.general"),
    },
    urgency: src.urgency,
    whatsHappening: src.whatsHappening,
    whyItMatters: src.whyItMatters,
    steps: src.steps,
    followUp: src.followUp || null,
    relatedConditionId: match?.conditionId || null,
    sources: src.sources || [],
  };
}
