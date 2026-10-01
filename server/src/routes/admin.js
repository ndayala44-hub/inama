import { Router } from "express";
import { protect, restrictTo } from "../middleware/auth.js";
import {
  getAdminStats,
  getReports,
  listUsers,
  getUser,
  createUser,
  updateUser,
  updateUserStatus,
  deleteUser,
  getChatLogs,
  getChatById,
} from "../controllers/adminController.js";
import {
  listContactMessages,
  markContactMessageRead,
} from "../controllers/contactController.js";
import {
  validateBody,
  adminCreateUserSchema,
  adminUpdateUserSchema,
} from "../validators/authValidators.js";

const router = Router();
router.use(protect, restrictTo("admin"));

router.get("/stats", getAdminStats);
router.get("/reports", getReports);
router.get("/users", listUsers);
router.post("/users", validateBody(adminCreateUserSchema), createUser);
router.get("/users/:id", getUser);
router.patch("/users/:id", validateBody(adminUpdateUserSchema), updateUser);
router.patch("/users/:id/status", updateUserStatus);
router.delete("/users/:id", deleteUser);
router.get("/chats", getChatLogs);
router.get("/chats/:id", getChatById);
router.get("/contact-messages", listContactMessages);
router.patch("/contact-messages/:id", markContactMessageRead);

export default router;
