// Loads the shared, reviewed advisory content (the same JSON files the Android app bundles).
// Content is per language: advisory_kb.<lang>.json. Missing languages fall back to English.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { config } from "../../../config/index.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoShared = path.resolve(here, "../../../../shared");

const knowledgeDir = () => config.inama.knowledgeDir || path.join(repoShared, "knowledge");
const contentDir = () => config.inama.contentDir || path.join(repoShared, "content");

const cache = new Map();

function readJson(file) {
  if (cache.has(file)) return cache.get(file);
  const data = JSON.parse(fs.readFileSync(file, "utf8"));
  cache.set(file, data);
  return data;
}

function readLocalized(dir, base, lang) {
  const localized = path.join(dir, `${base}.${lang}.json`);
  if (fs.existsSync(localized)) return readJson(localized);
  return readJson(path.join(dir, `${base}.en.json`));
}

export function loadKnowledgeBase(lang = "en") {
  const kb = readLocalized(knowledgeDir(), "advisory_kb", lang);
  if (!kb.__index) {
    Object.defineProperty(kb, "__index", {
      enumerable: false,
      value: {
        crops: new Map(kb.crops.map((c) => [c.id, c])),
        conditions: new Map(kb.conditions.map((c) => [c.id, c])),
        symptoms: new Map(kb.symptoms.map((s) => [s.id, s])),
      },
    });
  }
  return kb;
}

export const cropById = (kb, id) => kb.__index.crops.get(id) || null;
export const conditionById = (kb, id) => kb.__index.conditions.get(id) || null;
export const symptomById = (kb, id) => kb.__index.symptoms.get(id) || null;

export function loadLessons(lang = "en") {
  return readLocalized(contentDir(), "lessons", lang);
}

export function loadDistricts() {
  return readJson(path.join(contentDir(), "rwanda_districts.json"));
}

export function loadTestVectors() {
  return readJson(path.join(knowledgeDir(), "engine_test_vectors.json"));
}
