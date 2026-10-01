import { ContactMessage } from "../models/index.js";
import { sendSuccess, sendError } from "../utils/response.js";

export const submitContactMessage = async (req, res, next) => {
  try {
    const { name, email, subject, message } = req.body;
    if (!name?.trim() || !email?.trim() || !message?.trim()) {
      return sendError(res, "Name, email, and message are required.");
    }
    if (!/^\S+@\S+\.\S+$/.test(email))
      return sendError(res, "Enter a valid email address.");

    await ContactMessage.create({
      name: name.trim(),
      email: email.toLowerCase().trim(),
      subject: subject?.trim() || "",
      message: message.trim(),
    });

    sendSuccess(
      res,
      null,
      201,
      "Message sent! We'll get back to you within 24 hours.",
    );
  } catch (err) {
    next(err);
  }
};

export const listContactMessages = async (req, res, next) => {
  try {
    const { page = 1, limit = 20 } = req.query;
    const skip = (parseInt(page) - 1) * parseInt(limit);
    const [messages, total] = await Promise.all([
      ContactMessage.find()
        .sort({ createdAt: -1 })
        .skip(skip)
        .limit(parseInt(limit)),
      ContactMessage.countDocuments(),
    ]);
    sendSuccess(res, {
      messages,
      total,
      page: parseInt(page),
      totalPages: Math.ceil(total / parseInt(limit)),
    });
  } catch (err) {
    next(err);
  }
};

export const markContactMessageRead = async (req, res, next) => {
  try {
    const msg = await ContactMessage.findByIdAndUpdate(
      req.params.id,
      { status: "read" },
      { new: true },
    );
    if (!msg) return sendError(res, "Message not found.", 404);
    sendSuccess(res, { message: msg });
  } catch (err) {
    next(err);
  }
};
