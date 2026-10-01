import { DiseaseResult } from "../models/index.js";
import { analyzeDisease } from "../services/geminiService.js";
import { sendSuccess, sendError } from "../utils/response.js";

export const detectDisease = async (req, res, next) => {
  try {
    if (!req.file) return sendError(res, "Please upload a crop image (JPEG or PNG).");

    const allowed = ["image/jpeg", "image/jpg", "image/png", "image/webp"];
    if (!allowed.includes(req.file.mimetype)) {
      return sendError(res, "Only JPEG, PNG, or WebP images are supported.");
    }

    const base64 = req.file.buffer.toString("base64");
    const result = await analyzeDisease(base64, req.file.mimetype);

    const record = await DiseaseResult.create({
      userId: req.user._id,
      disease: result.disease,
      confidence: result.confidence,
      crop: result.crop,
      severity: result.severity,
      causes: result.causes,
      remedies: result.remedies,
      analysedByAI: result.analysedByAI,
      imageSize: req.file.size,
      imageMimeType: req.file.mimetype,
    });

    sendSuccess(
      res,
      { id: record._id, ...result },
      200,
      result.analysedByAI ? "Image analysed by AI." : "Image analysed (fallback model — set GEMINI_API_KEY for real AI)."
    );
  } catch (err) {
    next(err);
  }
};

export const getDiseaseHistory = async (req, res, next) => {
  try {
    const results = await DiseaseResult.find({ userId: req.user._id }).sort({ createdAt: -1 });
    sendSuccess(res, { results, total: results.length });
  } catch (err) {
    next(err);
  }
};
