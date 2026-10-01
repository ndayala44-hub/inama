// Language handling for the Inama v1 API.
//
// The MVP answers in English only, but every response is built from per-language
// resources: advisory_kb.<lang>.json for advice content, and the MESSAGES table below
// for short generated phrases. Adding Kinyarwanda later means adding "rw" to
// INAMA_LANGUAGES, shipping advisory_kb.rw.json and a MESSAGES.rw entry — no handler changes.
import { config } from "../../config/index.js";

export const MESSAGES = {
  en: {
    reason: {
      signs_matched: "matches {count} of the signs you reported",
      photo_and_signs: "your photo and {count} of your signs agree",
      photo_only: "based on the photo only",
      ambiguous: "the signs fit more than one problem",
      photo_blurry: "the photo is blurry",
      photo_dark: "the photo is too dark",
      photo_bright: "the photo is too bright",
      not_a_plant: "we couldn’t find a plant in the photo",
      no_signs: "there are no signs to go on yet",
      healthy_looking: "the photo looks healthy",
      kb_match: "based on common advice for this question",
      general: "general advice — tell Inama more to be surer",
      model: "{text}",
    },
    photoHint: {
      brown: "Brown areas in the photo",
      yellow: "Yellow areas in the photo",
      white: "Pale or white areas in the photo",
    },
    escalation: {
      low_confidence: "Inama is not sure — a person should check.",
      urgent: "This needs action today — ask your farmer promoter to confirm.",
      unvetted: "This problem is not in Inama’s reviewed guide yet.",
    },
    otherSteps: [
      "Remove the worst affected leaves or plants and keep them away from the field.",
      "Don’t spray until someone has confirmed the problem.",
      "Ask your farmer promoter or an agronomist to check.",
    ],
    otherWhy: "Inama recognised this problem but doesn’t have reviewed advice for it yet.",
    expertReply: "I looked at your photos. {text} Follow the steps in the app and send me a new photo in a week.",
    decisions: {
      spraying: "Spraying",
      weeding: "Weeding",
      planting: "Planting",
      drying: "Drying harvest",
      spray_rain: "Rain is coming — spray would wash off.",
      spray_wind: "Windy — spray only in the calm early morning.",
      spray_go: "Calm and dry — spray early in the morning.",
      weed_wet: "Heavy rain today — soil will be too wet.",
      weed_go: "Good day to weed.",
      plant_rain: "Rain is coming — good for planting.",
      plant_dry: "Dry days ahead — wait for steady rain.",
      plant_mixed: "Rain is patchy — plant only if the soil is moist.",
      dry_rain: "Rain likely — dry your harvest under cover.",
      dry_go: "Sunny enough to dry your harvest.",
    },
    alerts: {
      heavy_rain: "Heavy rain expected {day}.",
      heat: "Very hot — water seedlings early or late in the day.",
      dry_spell: "Little rain in the next days — mulch to keep water in the soil.",
    },
    errors: {
      invalid_phone: "Enter a Rwandan mobile number, for example 07XX XXX XXX.",
      invalid_code: "That code is not right. Check the SMS and try again.",
      expired_code: "That code has expired. Ask for a new one.",
      unauthorized: "Please sign in again.",
      image_required: "Add a photo of the plant.",
      image_type: "Only JPEG, PNG or WebP photos are supported.",
      image_too_big: "The photo is too big. Try again with a smaller photo.",
      question_required: "Ask a question first.",
      not_found: "Not found.",
      bad_request: "Something in the request is not right.",
    },
  },
};

export function t(lang, path, vars = {}) {
  const table = MESSAGES[lang] || MESSAGES.en;
  const fallback = MESSAGES.en;
  const pick = (obj) => path.split(".").reduce((o, k) => (o == null ? o : o[k]), obj);
  let value = pick(table);
  if (value == null) value = pick(fallback);
  if (typeof value !== "string") return value ?? path;
  return value.replace(/\{(\w+)\}/g, (_, k) => (vars[k] ?? `{${k}}`).toString());
}

// Resolves the response language from ?lang= or Accept-Language, limited to the
// languages this deployment supports.
export function resolveLanguage(acceptLanguage, queryLang) {
  const supported = config.inama.languages;
  const wanted = [];
  if (queryLang) wanted.push(String(queryLang));
  if (acceptLanguage) {
    for (const part of String(acceptLanguage).split(",")) {
      const tag = part.split(";")[0].trim();
      if (tag) wanted.push(tag);
    }
  }
  for (const tag of wanted) {
    const base = tag.toLowerCase().split("-")[0];
    if (supported.includes(base)) return base;
  }
  return supported.includes(config.inama.defaultLanguage) ? config.inama.defaultLanguage : supported[0] || "en";
}
