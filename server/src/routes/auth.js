import { Router as AuthRouter } from "express";
import rateLimit from "express-rate-limit";
export const authRouter = AuthRouter();
import passport, { isGoogleConfigured } from "../config/passport.js";
import {
  register,
  login,
  logout,
  googleCallback,
  getMe,
  updateProfile,
  changePassword,
  forgotPassword,
  verifyOtp,
  resetPassword,
} from "../controllers/authController.js";
import { protect } from "../middleware/auth.js";
import {
  validateBody,
  registerSchema,
  loginSchema,
  forgotPasswordSchema,
  verifyOtpSchema,
  resetPasswordSchema,
} from "../validators/authValidators.js";
import { sendError } from "../utils/response.js";

authRouter.post("/register", validateBody(registerSchema), register);
authRouter.post("/login", validateBody(loginSchema), login);
authRouter.post("/logout", protect, logout);
authRouter.get("/me", protect, getMe);
authRouter.patch("/me", protect, updateProfile);
authRouter.patch("/change-password", protect, changePassword);

const otpRateLimit = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 8,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    success: false,
    message: "Too many attempts. Please try again in 15 minutes.",
  },
});
authRouter.post(
  "/forgot-password",
  otpRateLimit,
  validateBody(forgotPasswordSchema),
  forgotPassword,
);
authRouter.post(
  "/verify-otp",
  otpRateLimit,
  validateBody(verifyOtpSchema),
  verifyOtp,
);
authRouter.post(
  "/reset-password",
  otpRateLimit,
  validateBody(resetPasswordSchema),
  resetPassword,
);

authRouter.get("/google", (req, res, next) => {
  if (!isGoogleConfigured) {
    return sendError(
      res,
      "Google sign-in is not configured on this server.",
      503,
    );
  }
  passport.authenticate("google", {
    scope: ["profile", "email"],
    session: false,
  })(req, res, next);
});

authRouter.get(
  "/google/callback",
  (req, res, next) => {
    if (!isGoogleConfigured) {
      return sendError(
        res,
        "Google sign-in is not configured on this server.",
        503,
      );
    }
    passport.authenticate("google", {
      session: false,
      failureRedirect: "/login",
    })(req, res, next);
  },
  googleCallback,
);
