// Inama knowledge-base engine — the transparent, rule-based advisor used when no AI model is
// configured or reachable. It extends AgriAI's keyword fallback (FALLBACK_CHATS) into a scored
// matcher over the shared, reviewed knowledge base.
//
// IMPORTANT: the Android app ships a Kotlin port of this exact algorithm
// (rw.inama.app.domain.ai.kb.KnowledgeBaseEngine). Both are checked against
// shared/knowledge/engine_test_vectors.json — change them together.

const clamp01 = (x) => Math.max(0, Math.min(1, x));
const num = (x) => (typeof x === "number" && Number.isFinite(x) ? x : null);

export function assessQuality(features, q) {
  if (!features) return "unknown";
  const sharpness = num(features.sharpness);
  const brightness = num(features.brightness);
  if (sharpness != null && sharpness < q.minSharpness) return "blurry";
  if (brightness != null && brightness < q.minBrightness) return "dark";
  if (brightness != null && brightness > q.maxBrightness) return "bright";
  return "ok";
}

export function visualLevels(features, scale) {
  if (!features) return null;
  const level = (ratio, s) => clamp01((num(ratio) ?? 0) / s);
  return {
    brown: level(features.brownRatio, scale.brown),
    yellow: level(features.yellowRatio, scale.yellow),
    white: level(features.whiteRatio, scale.white),
    green: level(features.greenRatio, scale.green),
  };
}

function visualMatch(profile, levels) {
  const keys = Object.keys(profile || {});
  if (!levels || keys.length === 0) return null;
  let diff = 0;
  for (const k of keys) diff += Math.abs(profile[k] - levels[k]);
  return 1 - diff / keys.length;
}

function specificity(symptomId, candidates) {
  let n = 0;
  for (const c of candidates) if ((c.cues[symptomId] || 0) >= 0.5) n += 1;
  return n <= 1 ? 1 : 1 / n;
}

function scoreCondition(cond, symptoms, levels, w, candidates, photoOnlyFactor) {
  const cues = cond.cues;
  const total = Object.values(cues).reduce((a, b) => a + b, 0) || 1;
  const vis = visualMatch(cond.visual, levels);
  if (symptoms.length === 0) {
    // Colour alone is weak evidence: capped below "medium" so the farmer is asked what they see.
    return { conditionId: cond.id, score: vis == null ? 0 : photoOnlyFactor * vis, matched: [], vis };
  }
  let support = 0;
  let covered = 0;
  const matched = [];
  for (const s of symptoms) {
    const weight = cues[s] || 0;
    support += weight;
    covered += weight;
    if (weight > 0) matched.push(s);
  }
  support /= symptoms.length;
  const coverage = covered / total;
  let score =
    vis == null
      ? (w.symptomMatch * support + w.coverage * coverage) / (w.symptomMatch + w.coverage)
      : w.symptomMatch * support + w.coverage * coverage + w.visual * vis;
  const spec = matched.length
    ? matched.reduce((a, s) => a + specificity(s, candidates), 0) / matched.length
    : 1;
  score *= 0.75 + 0.25 * spec;
  return { conditionId: cond.id, score, matched, vis };
}

/**
 * @param kb        parsed advisory_kb.<lang>.json
 * @param input     { cropId?, symptoms?: string[], features?: {brownRatio, yellowRatio, whiteRatio, greenRatio, brightness, sharpness} }
 * @returns assessment { quality, level, score, reasonCode, reasonArgs, outcome, conditionId, candidates, matchedSymptoms, visualHints }
 */
export function assessDiagnosis(kb, { cropId = null, symptoms = [], features = null } = {}) {
  const e = kb.engine;
  const known = new Set(kb.symptoms.map((s) => s.id));
  const S = [...new Set((symptoms || []).filter((s) => known.has(s)))];
  const candidates = kb.conditions.filter((c) => !cropId || c.cropId === cropId);
  const quality = assessQuality(features, e.quality);
  const levels = quality === "unknown" ? null : visualLevels(features, e.visualScale);

  const ranked = candidates
    .map((c) => scoreCondition(c, S, levels, e.weights, candidates, e.photoOnlyFactor ?? 0.45))
    .sort((a, b) => b.score - a.score);
  const shortlist = ranked.filter((r) => r.score > 0.2).slice(0, 3);
  const visualHints = levels
    ? ["brown", "yellow", "white"].filter((k) => levels[k] >= 0.5)
    : [];

  const base = { quality, matchedSymptoms: [], visualHints, candidates: shortlist };

  if (quality === "blurry" || quality === "dark" || quality === "bright") {
    return { ...base, level: "low", score: 0, reasonCode: `photo_${quality}`, reasonArgs: {}, outcome: "uncertain", conditionId: null };
  }
  if (S.length === 0 && !levels) {
    return { ...base, level: "low", score: 0, reasonCode: "no_signs", reasonArgs: {}, outcome: "uncertain", conditionId: null, candidates: [] };
  }

  // "Looks healthy" needs a clean leaf: any real blemish means we ask instead of reassuring.
  const blemish = levels ? Math.max(levels.brown, levels.yellow, levels.white) : 1;
  const healthy =
    S.length === 0 && levels && blemish < (e.healthyMaxBlemish ?? 0.35)
      ? Math.min(e.healthyCap ?? 0.7, levels.green * (1 - blemish))
      : 0;
  const top = ranked[0] || { score: 0, matched: [], vis: null, conditionId: null };
  const second = ranked[1] || { score: 0 };

  if (healthy > top.score) {
    let score = healthy;
    let reasonCode = "healthy_looking";
    if (healthy - top.score < e.ambiguityMargin) {
      score = Math.min(score, e.ambiguousCap);
      reasonCode = "ambiguous";
    }
    const level = levelFor(score, e.thresholds);
    return {
      ...base,
      level,
      score,
      reasonCode,
      reasonArgs: {},
      outcome: level === "low" ? "uncertain" : "healthy",
      conditionId: level === "low" ? null : "healthy",
    };
  }

  let score = top.score;
  let reasonCode = S.length ? (levels && top.vis != null ? "photo_and_signs" : "signs_matched") : "photo_only";
  if (top.score - second.score < e.ambiguityMargin) {
    score = Math.min(score, e.ambiguousCap);
    reasonCode = "ambiguous";
  }
  const level = levelFor(score, e.thresholds);
  return {
    ...base,
    level,
    score,
    reasonCode,
    reasonArgs: { count: top.matched.length },
    outcome: level === "low" ? "uncertain" : "condition",
    conditionId: level === "low" ? null : top.conditionId,
    matchedSymptoms: top.matched,
  };
}

export function levelFor(score, thresholds) {
  if (score >= thresholds.high) return "high";
  if (score >= thresholds.medium) return "medium";
  return "low";
}

/** Keyword matcher over kb.questions — a structured port of AgriAI's FALLBACK_CHATS. */
export function matchQuestion(kb, question, cropId = null) {
  const text = ` ${String(question || "").toLowerCase()} `;
  let best = null;
  let bestScore = 0;
  for (const q of kb.questions) {
    let score = 0;
    for (const k of q.keywords) if (text.includes(k.toLowerCase())) score += 1;
    if (score > 0 && q.cropId && cropId && q.cropId === cropId) score += 0.5;
    if (score > bestScore) {
      best = q;
      bestScore = score;
    }
  }
  return bestScore >= 1 ? best : null;
}
