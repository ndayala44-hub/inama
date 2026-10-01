import mongoose from "mongoose";

const passwordResetOtpSchema = new mongoose.Schema(
  {
    email: {
      type: String,
      required: true,
      lowercase: true,
      trim: true,
      index: true,
    },
    otpHash: { type: String, required: true },
    resetToken: { type: String, default: null },
    verified: { type: Boolean, default: false },
    attempts: { type: Number, default: 0 },
    expiresAt: { type: Date, required: true, index: { expires: 0 } },
  },
  { timestamps: true },
);

const PasswordResetOtp = mongoose.model(
  "PasswordResetOtp",
  passwordResetOtpSchema,
);
export default PasswordResetOtp;
