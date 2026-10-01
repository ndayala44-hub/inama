import { z } from "zod";
import { sendError } from "../utils/response.js";

export const registerSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(2, "Full name must be at least 2 characters"),
    email: z.string().trim().email("Enter a valid email address"),
    phone: z
      .string()
      .trim()
      .regex(/^\+?\d{10,13}$/, "Enter a valid phone number"),
    password: z
      .string()
      .min(8, "Password must be at least 8 characters")
      .max(72, "Password must be under 72 characters"),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

export const loginSchema = z.object({
  email: z.string().trim().email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

export const forgotPasswordSchema = z.object({
  email: z.string().trim().email("Enter a valid email address"),
});

export const verifyOtpSchema = z.object({
  email: z.string().trim().email("Enter a valid email address"),
  otp: z
    .string()
    .trim()
    .regex(/^\d{6}$/, "Enter the 6-digit code"),
});

export const resetPasswordSchema = z.object({
  email: z.string().trim().email("Enter a valid email address"),
  resetToken: z.string().min(1, "Missing reset token"),
  newPassword: z
    .string()
    .min(8, "Password must be at least 8 characters")
    .max(72),
});

export const adminCreateUserSchema = z.object({
  name: z.string().trim().min(2, "Name must be at least 2 characters"),
  email: z.string().trim().email("Enter a valid email address"),
  phone: z
    .string()
    .trim()
    .regex(/^\+?\d{10,13}$/, "Enter a valid phone number")
    .optional()
    .or(z.literal("")),
  password: z.string().min(8, "Password must be at least 8 characters").max(72),
  role: z.enum(["farmer", "admin"]).optional(),
});

export const adminUpdateUserSchema = z.object({
  name: z.string().trim().min(2).optional(),
  email: z.string().trim().email("Enter a valid email address").optional(),
  phone: z
    .string()
    .trim()
    .regex(/^\+?\d{10,13}$/, "Enter a valid phone number")
    .optional()
    .or(z.literal("")),
  role: z.enum(["farmer", "admin"]).optional(),
  status: z.enum(["active", "inactive", "suspended"]).optional(),
});

export const validateBody = (schema) => (req, res, next) => {
  const result = schema.safeParse(req.body);
  if (!result.success) {
    const errors = result.error.issues.map((issue) => ({
      field: issue.path.join(".") || "body",
      message: issue.message,
    }));
    return sendError(res, errors[0]?.message || "Invalid input.", 400, errors);
  }
  req.body = result.data;
  next();
};
