import { Router } from "express";
import { sendMessage, getChatHistory, getChat, deleteChat, updateChatTitle } from "../controllers/chatController.js";
import { protect } from "../middleware/auth.js";

const router = Router();
router.use(protect);
router.post("/", sendMessage);
router.get("/", getChatHistory);
router.get("/:id", getChat);
router.delete("/:id", deleteChat);
router.patch("/:id/title", updateChatTitle);

export default router;
