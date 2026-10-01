import jwt from "jsonwebtoken";
import crypto from "crypto";
import bcrypt from "bcryptjs";
import { config } from "../../config/index.js";
import { User, Notification, PasswordResetOtp } from "../models/index.js";
import {
  sendSuccess,
  sendError,
  isValidEmail,
  isValidPhone,
} from "../utils/response.js";
import { sendOtpEmail } from "../services/emailService.js";

const signToken = (id) =>
  jwt.sign({ id }, config.jwt.secret, { expiresIn: config.jwt.expiresIn });

export const register = async (req, res, next) => {
  try {
    const { fullName, email, phone, password, confirmPassword } = req.body;

    if (!fullName?.trim()) return sendError(res, "Full name is required.");
    if (!email || !isValidEmail(email))
      return sendError(res, "Enter a valid email address.");
    if (!phone || !isValidPhone(phone))
      return sendError(res, "Enter a valid phone number.");
    if (!password || password.length < 8)
      return sendError(res, "Password must be at least 8 characters.");
    if (password !== confirmPassword)
      return sendError(res, "Passwords do not match.");

    const existing = await User.findOne({ email: email.toLowerCase() });
    if (existing)
      return sendError(res, "An account with this email already exists.", 409);

    const user = await User.create({
      name: fullName.trim(),
      email: email.toLowerCase(),
      phone: phone.trim(),
      password,
    });

    await Notification.create({
      userId: user._id,
      type: "system",
      title: "Welcome to AgriAI!",
      message:
        "Your account is ready. Start by asking a farming question in the AI Advisory Chat.",
    });

    const token = signToken(user._id);
    sendSuccess(
      res,
      { token, user: user.toSafeObject() },
      201,
      "Account created successfully.",
    );
  } catch (err) {
    next(err);
  }
};

export const login = async (req, res, next) => {
  try {
    const { email, password } = req.body;
    if (!email || !password)
      return sendError(res, "Email and password are required.");

    const user = await User.findOne({ email: email.toLowerCase() }).select(
      "+password",
    );
    if (!user) return sendError(res, "Invalid email or password.", 401);
    if (user.status === "suspended")
      return sendError(
        res,
        "Your account has been suspended. Contact support.",
        403,
      );

    const isMatch = await user.comparePassword(password);
    if (!isMatch) return sendError(res, "Invalid email or password.", 401);

    const token = signToken(user._id);
    sendSuccess(
      res,
      { token, user: user.toSafeObject() },
      200,
      "Logged in successfully.",
    );
  } catch (err) {
    next(err);
  }
};

export const logout = async (_req, res) => {
  sendSuccess(res, null, 200, "Logged out successfully.");
};

export const googleCallback = async (req, res) => {
  const token = signToken(req.user._id);
  const redirectUrl = new URL("/oauth-callback", config.cors.origin);
  redirectUrl.searchParams.set("token", token);
  res.redirect(redirectUrl.toString());
};

export const getMe = async (req, res, next) => {
  try {
    const user = await User.findById(req.user._id);
    if (!user) return sendError(res, "User not found.", 404);
    sendSuccess(res, { user: user.toSafeObject() });
  } catch (err) {
    next(err);
  }
};

export const updateProfile = async (req, res, next) => {
  try {
    const {
      name,
      location,
      preferredLanguage,
      farmSize,
      primaryCrops,
      phone,
      notificationPreferences,
    } = req.body;
    const update = {};

    if (name?.trim()) update.name = name.trim();
    if (location !== undefined) update.location = location;
    if (preferredLanguage) update.preferredLanguage = preferredLanguage;
    if (farmSize !== undefined) update.farmSize = farmSize;
    if (Array.isArray(primaryCrops)) update.primaryCrops = primaryCrops;
    if (phone && isValidPhone(phone)) update.phone = phone.trim();
    if (
      notificationPreferences &&
      typeof notificationPreferences === "object"
    ) {
      const allowed = ["rain", "pest", "schemes", "weekly"];
      update.notificationPreferences = {};
      for (const key of allowed) {
        if (typeof notificationPreferences[key] === "boolean") {
          update.notificationPreferences[key] = notificationPreferences[key];
        }
      }
    }

    const user = await User.findByIdAndUpdate(req.user._id, update, {
      new: true,
      runValidators: true,
    });
    sendSuccess(
      res,
      { user: user.toSafeObject() },
      200,
      "Profile updated successfully.",
    );
  } catch (err) {
    next(err);
  }
};

const OTP_EXPIRY_MS = 10 * 60 * 1000; // 10 minutes
const MAX_OTP_ATTEMPTS = 5;

// Step 1 — request an OTP.
export const forgotPassword = async (req, res, next) => {
  try {
    const { email } = req.body;
    if (!email || !isValidEmail(email))
      return sendError(res, "Enter a valid email address.");

    const normalizedEmail = email.toLowerCase();
    const user = await User.findOne({ email: normalizedEmail });

    if (user) {
      const otp = String(crypto.randomInt(100000, 999999));
      const otpHash = await bcrypt.hash(otp, 10);

      await PasswordResetOtp.findOneAndUpdate(
        { email: normalizedEmail },
        {
          email: normalizedEmail,
          otpHash,
          resetToken: null,
          verified: false,
          attempts: 0,
          expiresAt: new Date(Date.now() + OTP_EXPIRY_MS),
        },
        { upsert: true, new: true },
      );

      try {
        await sendOtpEmail(normalizedEmail, otp);
      } catch (emailErr) {
        return next(emailErr);
      }
    }

    sendSuccess(
      res,
      null,
      200,
      "If an account exists for that email, a verification code has been sent.",
    );
  } catch (err) {
    next(err);
  }
};

// Step 2 — verify the OTP. 
export const verifyOtp = async (req, res, next) => {
  try {
    const { email, otp } = req.body;
    if (!email || !otp) return sendError(res, "Email and code are required.");

    const normalizedEmail = email.toLowerCase();
    const record = await PasswordResetOtp.findOne({ email: normalizedEmail });

    if (!record)
      return sendError(
        res,
        "Code expired or not found. Please request a new one.",
        400,
      );
    if (record.attempts >= MAX_OTP_ATTEMPTS) {
      return sendError(
        res,
        "Too many incorrect attempts. Please request a new code.",
        429,
      );
    }

    const isMatch = await bcrypt.compare(String(otp), record.otpHash);
    if (!isMatch) {
      record.attempts += 1;
      await record.save();
      return sendError(res, "Incorrect code. Please try again.", 400);
    }

    const resetToken = crypto.randomBytes(32).toString("hex");
    record.verified = true;
    record.resetToken = resetToken;
    await record.save();

    sendSuccess(res, { resetToken }, 200, "Code verified.");
  } catch (err) {
    next(err);
  }
};

// Step 3 — set the new password.
export const resetPassword = async (req, res, next) => {
  try {
    const { email, resetToken, newPassword } = req.body;
    if (!email || !resetToken || !newPassword)
      return sendError(res, "Missing required fields.");
    if (newPassword.length < 8)
      return sendError(res, "Password must be at least 8 characters.");

    const normalizedEmail = email.toLowerCase();
    const record = await PasswordResetOtp.findOne({
      email: normalizedEmail,
      verified: true,
      resetToken,
    });
    if (!record)
      return sendError(
        res,
        "This reset session has expired or is invalid. Please start again.",
        400,
      );

    const user = await User.findOne({ email: normalizedEmail });
    if (!user) return sendError(res, "Account not found.", 404);

    user.password = newPassword;
    await user.save();
    await PasswordResetOtp.deleteOne({ _id: record._id });

    sendSuccess(res, null, 200, "Password reset successfully. Please log in.");
  } catch (err) {
    next(err);
  }
};

export const changePassword = async (req, res, next) => {
  try {
    const { currentPassword, newPassword } = req.body;
    if (!currentPassword || !newPassword)
      return sendError(res, "Both current and new password are required.");
    if (newPassword.length < 8)
      return sendError(res, "New password must be at least 8 characters.");

    const user = await User.findById(req.user._id).select("+password");
    const isMatch = await user.comparePassword(currentPassword);
    if (!isMatch) return sendError(res, "Current password is incorrect.", 401);

    user.password = newPassword;
    await user.save();

    sendSuccess(res, null, 200, "Password changed successfully.");
  } catch (err) {
    next(err);
  }
};
