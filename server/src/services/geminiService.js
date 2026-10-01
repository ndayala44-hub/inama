import { config } from "../../config/index.js";

// Inama change: base URL comes from config, and the low-level helpers are exported so the
// Inama v1 advisory layer (src/v1/services/geminiAdvisor.js) can reuse them.
const BASE_URL = config.gemini.baseUrl;
export const isConfigured = () =>
  Boolean(config.gemini.apiKey) &&
  !["your_gemini_api_key_here", "your_gemini_api_key"].includes(config.gemini.apiKey);

const AGRI_SYSTEM = `You are AgriAI, an expert agricultural advisor for Indian farmers.
Rules:
- Give practical, specific advice for Indian farming conditions
- Structure responses with: analysis, possible causes, recommended treatment, prevention
- Keep language simple — your users are working farmers
- Include both organic and chemical control options
- Always mention safety precautions for chemicals
- If unsure, say so and recommend a local agricultural extension officer
- Always respond in English, regardless of what language the farmer's question is written in`;

async function logGeminiFailure(res) {
  if (!config.isDev) return;
  const body = await res.text().catch(() => "");
  console.warn(
    `⚠️  Gemini API returned ${res.status} for model "${config.gemini.model}":`,
    body.slice(0, 300),
  );
}
function logGeminiError(err) {
  if (config.isDev) console.warn("⚠️  Gemini API request failed:", err.message);
}

export async function geminiGenerate(prompt, history = [], options = {}) {
  if (!isConfigured()) return null;
  const contents = [
    ...history.map((m) => ({
      role: m.role === "assistant" ? "model" : "user",
      parts: [{ text: m.content }],
    })),
    { role: "user", parts: [{ text: prompt }] },
  ];
  try {
    const res = await fetch(
      `${BASE_URL}/models/${config.gemini.model}:generateContent?key=${config.gemini.apiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents,
          system_instruction: { parts: [{ text: options.systemInstruction || AGRI_SYSTEM }] },
          generationConfig: {
            temperature: 0.7,
            maxOutputTokens: 1024,
            ...(options.generationConfig || {}),
          },
        }),
      },
    );
    if (!res.ok) {
      await logGeminiFailure(res);
      return null;
    }
    const data = await res.json();
    return data.candidates?.[0]?.content?.parts?.[0]?.text || null;
  } catch (err) {
    logGeminiError(err);
    return null;
  }
}

export async function geminiVision(base64Image, mimeType, prompt, options = {}) {
  if (!isConfigured()) return null;
  try {
    const res = await fetch(
      `${BASE_URL}/models/${config.gemini.model}:generateContent?key=${config.gemini.apiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [
            {
              parts: [
                { inline_data: { mime_type: mimeType, data: base64Image } },
                { text: prompt },
              ],
            },
          ],
          ...(options.systemInstruction
            ? { system_instruction: { parts: [{ text: options.systemInstruction }] } }
            : {}),
          generationConfig: {
            temperature: 0.4,
            maxOutputTokens: 1024,
            ...(options.generationConfig || {}),
          },
        }),
      },
    );
    if (!res.ok) {
      await logGeminiFailure(res);
      return null;
    }
    const data = await res.json();
    return data.candidates?.[0]?.content?.parts?.[0]?.text || null;
  } catch (err) {
    logGeminiError(err);
    return null;
  }
}

function parseAdvisory(rawText) {
  const lines = rawText.split("\n").filter(Boolean);
  const causes = [],
    treatment = [],
    prevention = [],
    replyLines = [];
  let section = null;
  for (const line of lines) {
    const lower = line.toLowerCase().trim();
    if (lower.startsWith("possible cause") || lower.startsWith("cause")) {
      section = "causes";
      continue;
    }
    if (
      lower.startsWith("recommended treatment") ||
      lower.startsWith("treatment") ||
      lower.startsWith("remedy")
    ) {
      section = "treatment";
      continue;
    }
    if (lower.startsWith("prevention")) {
      section = "prevention";
      continue;
    }
    const clean = line.replace(/^[-•*\d.]+\s*/, "").trim();
    if (!clean) continue;
    if (section === "causes") causes.push(clean);
    else if (section === "treatment") treatment.push(clean);
    else if (section === "prevention") prevention.push(clean);
    else replyLines.push(clean);
  }
  return {
    reply: replyLines.join(" ") || rawText.slice(0, 300),
    causes: causes.slice(0, 4),
    treatment: treatment.slice(0, 4),
    prevention: prevention.slice(0, 3),
  };
}

const FALLBACK_CHATS = [
  {
    keywords: ["yellow", "wheat"],
    reply:
      "Yellowing wheat leaves usually point to nitrogen deficiency or early-stage rust. Check the lower leaves — uniform yellowing from the base suggests nitrogen shortage, while orange-yellow pustules indicate rust.",
    causes: [
      "Nitrogen deficiency",
      "Yellow rust (Puccinia striiformis)",
      "Waterlogging at the root zone",
    ],
    treatment: [
      "Apply urea 40–50 kg/acre if nitrogen deficiency confirmed",
      "Spray Propiconazole 25% EC at 0.1% if rust pustules visible",
      "Improve field drainage",
    ],
    prevention: [
      "Use rust-resistant wheat varieties",
      "Avoid excess irrigation",
      "Apply balanced NPK at sowing",
    ],
  },
  {
    keywords: ["white", "spot", "tomato"],
    reply:
      "White spots on tomato leaves are commonly caused by powdery mildew or early blight.",
    causes: [
      "Powdery mildew (fungal)",
      "Septoria leaf spot",
      "Magnesium deficiency",
    ],
    treatment: [
      "Spray wettable sulfur 2g/litre for powdery mildew",
      "Remove and destroy severely affected leaves",
      "Apply Mancozeb 75% WP at 2g/litre",
    ],
    prevention: [
      "Maintain spacing for airflow",
      "Avoid overhead irrigation in evening",
      "Rotate crops each season",
    ],
  },
  {
    keywords: ["fungal", "prevent"],
    reply:
      "Preventive fungicide spraying before the rainy season is the most effective approach, combined with good field hygiene year-round.",
    causes: [],
    treatment: [
      "Apply copper-based fungicide as preventive spray before monsoon",
      "Remove crop debris and weeds harboring fungal spores",
      "Avoid dense planting",
    ],
    prevention: [
      "Rotate crops each season",
      "Use certified disease-free seeds",
      "Ensure proper field drainage",
    ],
  },
  {
    keywords: ["monsoon", "crop", "season", "kharif"],
    reply:
      "For the Kharif (monsoon) season, these crops perform reliably across most Indian regions based on your soil and rainfall.",
    causes: [],
    treatment: ["Rice", "Maize", "Soybean", "Cotton", "Pigeon pea (Arhar)"],
    prevention: [
      "Ensure field bunding to manage excess water",
      "Choose short-duration varieties in heavy-rainfall zones",
    ],
  },
];

const DEFAULT_FALLBACK = {
  reply:
    "Thank you for your question. I recommend carefully observing the affected plants and looking for patterns. For a precise diagnosis, use the Disease Detection feature to upload a photo.",
  causes: [
    "Possible fungal or bacterial infection",
    "Nutrient imbalance",
    "Pest activity",
  ],
  treatment: [
    "Isolate affected plants if possible",
    "Apply broad-spectrum fungicide as precaution",
    "Monitor for 3–4 days and re-check",
  ],
  prevention: [
    "Maintain field hygiene",
    "Rotate crops seasonally",
    "Test soil every season",
  ],
};

export async function sendAdvisoryMessage(message, history = []) {
  const raw = await geminiGenerate(message, history);
  if (raw) return parseAdvisory(raw);
  const lower = message.toLowerCase();
  const match = FALLBACK_CHATS.find((r) =>
    r.keywords.some((k) => lower.includes(k)),
  );
  return match || DEFAULT_FALLBACK;
}

export async function analyzeDisease(base64Image, mimeType) {
  const prompt = `Analyse this crop image for diseases, pests, or nutrient deficiencies.
Respond ONLY with valid JSON (no markdown, no backticks):
{"disease":"name","confidence":85,"crop":"crop type","severity":"High|Moderate|Low","causes":["cause1","cause2"],"remedies":["remedy1","remedy2"]}`;

  const raw = await geminiVision(base64Image, mimeType, prompt);
  if (raw) {
    try {
      return {
        ...JSON.parse(raw.replace(/```json|```/g, "").trim()),
        analysedByAI: true,
      };
    } catch {
      /* fall through */
    }
  }

  const results = [
    {
      disease: "Late Blight (Phytophthora infestans)",
      confidence: 87,
      crop: "Potato / Tomato",
      severity: "High",
    },
    {
      disease: "Leaf Rust (Puccinia triticina)",
      confidence: 82,
      crop: "Wheat",
      severity: "Moderate",
    },
    {
      disease: "Bacterial Leaf Blight",
      confidence: 76,
      crop: "Rice",
      severity: "Moderate",
    },
  ];
  return {
    ...results[Math.floor(Math.random() * results.length)],
    causes: [
      "Prolonged leaf wetness",
      "High humidity with moderate temperatures",
      "Poor air circulation",
    ],
    remedies: [
      "Apply copper-based fungicide (Copper oxychloride 50% WP, 3g/litre)",
      "Remove and destroy infected plant debris",
      "Improve drainage and spacing",
    ],
    analysedByAI: false,
  };
}

export async function getCropRecommendation({ soilType, season, location }) {
  const prompt = `Farmer in ${location || "India"}. Soil: ${soilType}. Season: ${season}.
Recommend 3 crops. Respond ONLY with valid JSON:
{"recommendations":[{"name":"Wheat","suitability":95,"benefits":"short text","tips":"key tip"}]}`;

  const raw = await geminiGenerate(prompt);
  if (raw) {
    try {
      const parsed = JSON.parse(raw.replace(/```json|```/g, "").trim());
      return { soilType, season, location, ...parsed };
    } catch {
      /* fall through */
    }
  }

  const cropMap = {
    Loamy: ["Wheat", "Sugarcane", "Cotton"],
    Sandy: ["Groundnut", "Bajra", "Watermelon"],
    Clayey: ["Rice", "Jute", "Pulses"],
    Black: ["Cotton", "Soybean", "Sunflower"],
    Red: ["Maize", "Millets", "Pulses"],
  };
  const crops = cropMap[soilType] || cropMap.Loamy;
  return {
    soilType,
    season,
    location,
    recommendations: crops.map((name, i) => ({
      name,
      suitability: 95 - i * 8,
      benefits:
        "Good market demand and well-suited to local rainfall patterns.",
      tips: "Use certified seeds, maintain proper spacing, and ensure timely irrigation.",
    })),
  };
}

export async function getSoilAdvisory({ ph, nitrogen, phosphorus, potassium }) {
  const prompt = `Soil test — pH: ${ph}, N: ${nitrogen} kg/ha, P: ${phosphorus} kg/ha, K: ${potassium} kg/ha.
Respond ONLY with valid JSON:
{"condition":"Acidic|Alkaline|Balanced","summary":"text","fertilizers":["f1","f2"],"suitableCrops":["c1","c2","c3"]}`;

  const raw = await geminiGenerate(prompt);
  if (raw) {
    try {
      return JSON.parse(raw.replace(/```json|```/g, "").trim());
    } catch {
      /* fall through */
    }
  }

  const phNum = parseFloat(ph);
  const condition =
    phNum < 6 ? "Acidic" : phNum > 7.5 ? "Alkaline" : "Balanced";
  return {
    condition,
    summary: `Soil pH of ${ph} indicates ${condition.toLowerCase()} conditions. ${nitrogen < 50 ? "Nitrogen levels are low — a boost is needed." : "Nitrogen levels are adequate."}`,
    fertilizers:
      condition === "Acidic"
        ? [
            "Agricultural lime to raise pH",
            "Balanced NPK 12:32:16",
            "Organic compost",
          ]
        : condition === "Alkaline"
          ? [
              "Elemental sulfur to lower pH",
              "Ammonium sulfate",
              "Well-rotted farmyard manure",
            ]
          : [
              "Balanced NPK 10:26:26",
              "Vermicompost",
              "Micronutrient mix (Zinc, Boron)",
            ],
    suitableCrops:
      condition === "Acidic"
        ? ["Tea", "Potato", "Pineapple"]
        : condition === "Alkaline"
          ? ["Barley", "Cotton", "Sugar beet"]
          : ["Wheat", "Maize", "Vegetables"],
  };
}
