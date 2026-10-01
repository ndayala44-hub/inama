import { Router } from "express";
import multer from "multer";
import { detectDisease, getDiseaseHistory } from "../controllers/diseaseController.js";
import { protect } from "../middleware/auth.js";

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 10 * 1024 * 1024 },
  fileFilter: (_req, file, cb) => {
    const allowed = ["image/jpeg", "image/jpg", "image/png", "image/webp"];
    allowed.includes(file.mimetype)
      ? cb(null, true)
      : cb(new Error("Only JPEG, PNG, and WebP images are supported."), false);
  },
});

const router = Router();
router.use(protect);
router.post("/", upload.single("image"), detectDisease);
router.get("/", getDiseaseHistory);

export default router;
