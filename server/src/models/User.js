import mongoose from "mongoose";
import bcrypt from "bcryptjs";

const userSchema = new mongoose.Schema(
  {
    name: { type: String, required: [true, "Name is required"], trim: true },
    email: {
      type: String,
      required: [true, "Email is required"],
      unique: true,
      lowercase: true,
      trim: true,
      match: [/^\S+@\S+\.\S+$/, "Enter a valid email address"],
    },
    phone: {
      type: String,
      required: [
        function () {
          return !this.googleId;
        },
        "Phone number is required",
      ],
      trim: true,
      default: "",
    },
    password: {
      type: String,
      required: [
        function () {
          return !this.googleId;
        },
        "Password is required",
      ],
      minlength: [8, "Password must be at least 8 characters"],
      select: false,
    },
    googleId: {
      type: String,
      default: null,
      index: true,
      sparse: true,
    },
    authProvider: {
      type: String,
      enum: ["local", "google"],
      default: "local",
    },
    role: {
      type: String,
      enum: ["farmer", "admin"],
      default: "farmer",
    },
    status: {
      type: String,
      enum: ["active", "inactive", "suspended"],
      default: "active",
    },
    location: { type: String, default: "" },
    preferredLanguage: { type: String, default: "English" },
    notificationPreferences: {
      rain: { type: Boolean, default: true },
      pest: { type: Boolean, default: true },
      schemes: { type: Boolean, default: false },
      weekly: { type: Boolean, default: true },
    },
    primaryCrops: { type: [String], default: [] },
    farmSize: { type: String, default: "" },
    joinedOn: {
      type: String,
      default: () => new Date().toISOString().split("T")[0],
    },
  },
  { timestamps: true },
);

userSchema.pre("save", async function (next) {
  if (!this.isModified("password") || !this.password) return next();
  this.password = await bcrypt.hash(this.password, 12);
  next();
});

userSchema.methods.comparePassword = async function (candidatePassword) {
  if (!this.password) return false;
  return bcrypt.compare(candidatePassword, this.password);
};

userSchema.methods.toSafeObject = function () {
  const obj = this.toObject();
  delete obj.password;
  return obj;
};

const User = mongoose.model("User", userSchema);
export default User;
