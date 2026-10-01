import { Router } from "express";
import { protect } from "../middleware/auth.js";
import {
  recommendCrops, getSoilHealth, getWeather,
  getNotifications, markRead, markAllRead,
  getBookmarks, addBookmark, removeBookmark, removeBookmarkBySource,
  getSchemes, getScheme,
} from "../controllers/featureControllers.js";

export const cropRouter = Router();
cropRouter.use(protect);
cropRouter.post("/", recommendCrops);

export const soilRouter = Router();
soilRouter.use(protect);
soilRouter.post("/", getSoilHealth);

export const weatherRouter = Router();
weatherRouter.use(protect);
weatherRouter.get("/", getWeather);

export const notificationRouter = Router();
notificationRouter.use(protect);
notificationRouter.get("/", getNotifications);
notificationRouter.patch("/mark-all-read", markAllRead);
notificationRouter.patch("/:id/read", markRead);

export const bookmarkRouter = Router();
bookmarkRouter.use(protect);
bookmarkRouter.get("/", getBookmarks);
bookmarkRouter.post("/", addBookmark);
bookmarkRouter.delete("/source/:sourceId", removeBookmarkBySource);
bookmarkRouter.delete("/:id", removeBookmark);

export const schemeRouter = Router();
schemeRouter.use(protect);
schemeRouter.get("/", getSchemes);
schemeRouter.get("/:id", getScheme);
