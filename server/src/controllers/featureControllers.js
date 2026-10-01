import { Notification, Bookmark } from "../models/index.js";
import {
  getCropRecommendation,
  getSoilAdvisory,
} from "../services/geminiService.js";
import { getWeatherData } from "../services/weatherService.js";
import { sendSuccess, sendError } from "../utils/response.js";

export const recommendCrops = async (req, res, next) => {
  try {
    const { soilType, season, location } = req.body;
    if (!soilType) return sendError(res, "Soil type is required.");
    if (!season) return sendError(res, "Season is required.");

    const valid = ["Loamy", "Sandy", "Clayey", "Black", "Red"];
    if (!valid.includes(soilType))
      return sendError(res, `Soil type must be one of: ${valid.join(", ")}.`);

    const result = await getCropRecommendation({
      soilType,
      season,
      location: location || "India",
    });
    sendSuccess(res, result, 200, "Crop recommendations generated.");
  } catch (err) {
    next(err);
  }
};

export const getSoilHealth = async (req, res, next) => {
  try {
    const { ph, nitrogen, phosphorus, potassium } = req.body;
    if ([ph, nitrogen, phosphorus, potassium].some((v) => v === undefined)) {
      return sendError(
        res,
        "All soil readings (ph, nitrogen, phosphorus, potassium) are required.",
      );
    }
    const phNum = parseFloat(ph);
    if (isNaN(phNum) || phNum < 0 || phNum > 14)
      return sendError(res, "pH must be a number between 0 and 14.");

    const result = await getSoilAdvisory({
      ph: phNum,
      nitrogen: parseFloat(nitrogen),
      phosphorus: parseFloat(phosphorus),
      potassium: parseFloat(potassium),
    });
    sendSuccess(res, result, 200, "Soil health advisory generated.");
  } catch (err) {
    next(err);
  }
};

export const getWeather = async (req, res, next) => {
  try {
    const { lat, lon } = req.query;
    const location = req.query.location || req.user?.location || "Nashik,IN";
    const data =
      lat != null && lon != null
        ? await getWeatherData({ lat: parseFloat(lat), lon: parseFloat(lon) })
        : await getWeatherData({ location });
    sendSuccess(
      res,
      data,
      200,
      data.isFallback
        ? "Showing demo weather (set WEATHER_API_KEY for real data)."
        : "Weather data retrieved.",
    );
  } catch (err) {
    next(err);
  }
};

export const getNotifications = async (req, res, next) => {
  try {
    const filter = { userId: req.user._id };
    if (req.query.type) filter.type = req.query.type;
    if (req.query.unread === "true") filter.read = false;

    const notifs = await Notification.find(filter).sort({ createdAt: -1 });
    const unreadCount = notifs.filter((n) => !n.read).length;
    sendSuccess(res, { notifications: notifs, unreadCount });
  } catch (err) {
    next(err);
  }
};

export const markRead = async (req, res, next) => {
  try {
    const notif = await Notification.findOneAndUpdate(
      { _id: req.params.id, userId: req.user._id },
      { read: true },
      { new: true },
    );
    if (!notif) return sendError(res, "Notification not found.", 404);
    sendSuccess(res, { notification: notif }, 200, "Marked as read.");
  } catch (err) {
    next(err);
  }
};

export const markAllRead = async (req, res, next) => {
  try {
    const result = await Notification.updateMany(
      { userId: req.user._id, read: false },
      { read: true },
    );
    sendSuccess(
      res,
      { updated: result.modifiedCount },
      200,
      `${result.modifiedCount} notification(s) marked as read.`,
    );
  } catch (err) {
    next(err);
  }
};

export const getBookmarks = async (req, res, next) => {
  try {
    const bookmarks = await Bookmark.find({ userId: req.user._id }).sort({
      createdAt: -1,
    });
    sendSuccess(res, { bookmarks, total: bookmarks.length });
  } catch (err) {
    next(err);
  }
};

export const addBookmark = async (req, res, next) => {
  try {
    const { type, title, summary, tags, sourcePath, sourceId } = req.body;
    if (!type || !title || !summary)
      return sendError(res, "type, title, and summary are required.");

    const valid = ["chat", "disease", "crop", "soil"];
    if (!valid.includes(type))
      return sendError(res, `type must be one of: ${valid.join(", ")}.`);

    if (sourceId) {
      const existing = await Bookmark.findOne({
        userId: req.user._id,
        sourceId,
      });
      if (existing)
        return sendSuccess(
          res,
          { bookmark: existing },
          200,
          "Already bookmarked.",
        );
    }

    const bookmark = await Bookmark.create({
      userId: req.user._id,
      type,
      title,
      summary,
      tags: tags || [],
      sourcePath: sourcePath || null,
      sourceId: sourceId || null,
    });
    sendSuccess(res, { bookmark }, 201, "Bookmark saved.");
  } catch (err) {
    if (err.code === 11000) return sendError(res, "Already bookmarked.", 409);
    next(err);
  }
};

export const removeBookmark = async (req, res, next) => {
  try {
    const bookmark = await Bookmark.findOneAndDelete({
      _id: req.params.id,
      userId: req.user._id,
    });
    if (!bookmark) return sendError(res, "Bookmark not found.", 404);
    sendSuccess(res, null, 200, "Bookmark removed.");
  } catch (err) {
    next(err);
  }
};

export const removeBookmarkBySource = async (req, res, next) => {
  try {
    const bookmark = await Bookmark.findOneAndDelete({
      userId: req.user._id,
      sourceId: req.params.sourceId,
    });
    if (!bookmark) return sendError(res, "Bookmark not found.", 404);
    sendSuccess(res, null, 200, "Bookmark removed.");
  } catch (err) {
    next(err);
  }
};

const SCHEMES = [
  {
    id: "s1",
    name: "PM-KISAN (Pradhan Mantri Kisan Samman Nidhi)",
    category: "Income Support",
    summary:
      "Direct income support of ₹6,000 per year, paid in three installments.",
    eligibility:
      "All landholding farmer families, subject to exclusion criteria.",
    deadline: "Next installment: 1 Aug – 30 Sep 2026",
    benefit: "₹2,000 per installment, 3 times a year",
    status: "open",
  },
  {
    id: "s2",
    name: "Pradhan Mantri Fasal Bima Yojana (PMFBY)",
    category: "Crop Insurance",
    summary:
      "Subsidized crop insurance covering yield losses due to natural calamities, pests, and diseases.",
    eligibility:
      "All farmers growing notified crops, including sharecroppers and tenant farmers.",
    deadline: "Kharif enrollment closes 31 Jul 2026",
    benefit: "Premium as low as 1.5–5% of sum insured",
    status: "open",
  },
  {
    id: "s3",
    name: "Soil Health Card Scheme",
    category: "Soil & Advisory",
    summary:
      "Free soil testing every 2 years with crop-wise fertilizer and nutrient recommendations.",
    eligibility:
      "All farmers; samples collected through local agriculture extension offices.",
    deadline: "Rolling — no fixed deadline",
    benefit: "Free soil test + personalized fertilizer plan",
    status: "open",
  },
  {
    id: "s4",
    name: "Kisan Credit Card (KCC)",
    category: "Credit & Loans",
    summary:
      "Short-term credit for cultivation expenses at subsidized interest rates.",
    eligibility:
      "Farmers, sharecroppers, and tenant farmers with proof of land cultivation rights.",
    deadline: "Apply anytime through participating banks",
    benefit: "Interest subvention up to 3% on timely repayment",
    status: "open",
  },
  {
    id: "s5",
    name: "Sub-Mission on Agricultural Mechanization (SMAM)",
    category: "Equipment Subsidy",
    summary: "Subsidy on purchase of farm machinery and equipment.",
    eligibility:
      "Individual farmers, FPOs, and cooperatives; priority for small and marginal farmers.",
    deadline: "Closed for this cycle — reopens Oct 2026",
    benefit: "40–50% subsidy on eligible equipment",
    status: "closed",
  },
];

export const getSchemes = (req, res) => {
  const { search, category, status } = req.query;
  let results = [...SCHEMES];
  if (search) {
    const q = search.toLowerCase();
    results = results.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        s.category.toLowerCase().includes(q) ||
        s.summary.toLowerCase().includes(q),
    );
  }
  if (category)
    results = results.filter(
      (s) => s.category.toLowerCase() === category.toLowerCase(),
    );
  if (status) results = results.filter((s) => s.status === status);
  sendSuccess(res, { schemes: results, total: results.length });
};

export const getScheme = (req, res) => {
  const scheme = SCHEMES.find((s) => s.id === req.params.id);
  if (!scheme) return sendError(res, "Scheme not found.", 404);
  sendSuccess(res, { scheme });
};
