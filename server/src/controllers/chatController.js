import { Chat } from "../models/index.js";
import { sendAdvisoryMessage } from "../services/geminiService.js";
import { sendSuccess, sendError } from "../utils/response.js";

export const sendMessage = async (req, res, next) => {
  try {
    const { message, chatId } = req.body;
    if (!message?.trim()) return sendError(res, "Message is required.");

    let chat = null;
    if (chatId) {
      chat = await Chat.findOne({ _id: chatId, userId: req.user._id });
      if (!chat) return sendError(res, "Chat not found.", 404);
    }

    // Build history for multi-turn context (last 10 messages max)
    const history = chat
      ? chat.messages
          .slice(-10)
          .map((m) => ({ role: m.role, content: m.content }))
      : [];

    const aiResponse = await sendAdvisoryMessage(message.trim(), history);

    const userMsg = { role: "user", content: message.trim() };
    const assistantMsg = {
      role: "assistant",
      content: aiResponse.reply,
      reply: aiResponse.reply,
      causes: aiResponse.causes || [],
      treatment: aiResponse.treatment || [],
      prevention: aiResponse.prevention || [],
    };

    if (!chat) {
      const title =
        message.trim().length > 60
          ? message.trim().slice(0, 60) + "..."
          : message.trim();
      chat = await Chat.create({
        userId: req.user._id,
        title,
        messages: [userMsg, assistantMsg],
      });
    } else {
      chat.messages.push(userMsg, assistantMsg);
      await chat.save();
    }

    const savedMsg = chat.messages[chat.messages.length - 1];
    sendSuccess(
      res,
      { chatId: chat._id, message: savedMsg },
      200,
      "Response generated.",
    );
  } catch (err) {
    next(err);
  }
};

export const getChatHistory = async (req, res, next) => {
  try {
    const { page = 1, limit = 20 } = req.query;
    const skip = (parseInt(page) - 1) * parseInt(limit);

    const [chats, total] = await Promise.all([
      Chat.find({ userId: req.user._id })
        .sort({ updatedAt: -1 })
        .skip(skip)
        .limit(parseInt(limit))
        .select("title messages createdAt updatedAt"),
      Chat.countDocuments({ userId: req.user._id }),
    ]);

    const items = chats.map((c) => ({
      id: c._id,
      title: c.title,
      preview:
        c.messages
          .find((m) => m.role === "assistant")
          ?.content?.slice(0, 100) || "",
      messageCount: c.messages.length,
      messages: c.messages,
      date: c.updatedAt,
      createdAt: c.createdAt,
    }));

    sendSuccess(res, {
      items,
      total,
      page: parseInt(page),
      totalPages: Math.ceil(total / parseInt(limit)),
    });
  } catch (err) {
    next(err);
  }
};

export const getChat = async (req, res, next) => {
  try {
    const chat = await Chat.findOne({
      _id: req.params.id,
      userId: req.user._id,
    });
    if (!chat) return sendError(res, "Chat not found.", 404);
    sendSuccess(res, { chat });
  } catch (err) {
    next(err);
  }
};

export const deleteChat = async (req, res, next) => {
  try {
    const chat = await Chat.findOneAndDelete({
      _id: req.params.id,
      userId: req.user._id,
    });
    if (!chat) return sendError(res, "Chat not found.", 404);
    sendSuccess(res, null, 200, "Conversation deleted.");
  } catch (err) {
    next(err);
  }
};

export const updateChatTitle = async (req, res, next) => {
  try {
    const { title } = req.body;
    if (!title?.trim()) return sendError(res, "Title is required.");
    const chat = await Chat.findOneAndUpdate(
      { _id: req.params.id, userId: req.user._id },
      { title: title.trim() },
      { new: true },
    );
    if (!chat) return sendError(res, "Chat not found.", 404);
    sendSuccess(res, { chat }, 200, "Title updated.");
  } catch (err) {
    next(err);
  }
};
