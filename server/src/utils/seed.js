import dotenv from "dotenv";
dotenv.config();

import mongoose from "mongoose";
import { User, Chat, Notification } from "../models/index.js";

const MONGO_URI = process.env.MONGODB_URI || "mongodb://localhost:27017/agriai";

async function seed() {
  console.log("🌱  Seeding AgriAI database...\n");
  await mongoose.connect(MONGO_URI);

  // Clear existing data
  await Promise.all([User.deleteMany(), Chat.deleteMany(), Notification.deleteMany()]);
  console.log("✓  Cleared existing documents");

  const ramesh = await User.create({
    name: "Ramesh Patil",
    email: "ramesh@example.com",
    phone: "+91 98765 43210",
    password: "Demo@1234",
    location: "Nashik, Maharashtra",
    preferredLanguage: "Hindi",
    primaryCrops: ["Wheat", "Onion", "Sugarcane"],
    farmSize: "4.5 acres",
    role: "farmer",
    joinedOn: "2025-11-03",
  });

  const sita = await User.create({
    name: "Sita Devi",
    email: "sita@example.com",
    phone: "+91 87654 32109",
    password: "Demo@1234",
    location: "Patna, Bihar",
    preferredLanguage: "Hindi",
    primaryCrops: ["Rice", "Pulses"],
    farmSize: "2.5 acres",
    role: "farmer",
    joinedOn: "2025-12-12",
  });

  const admin = await User.create({
    name: "Admin User",
    email: "admin@agriai.in",
    phone: "+91 99999 00000",
    password: "Admin@1234",
    location: "Pune, Maharashtra",
    preferredLanguage: "English",
    role: "admin",
    joinedOn: "2025-10-01",
  });

  console.log("✓  Created 3 users (Ramesh, Sita, Admin)");

  await Chat.create({
    userId: ramesh._id,
    title: "Wheat leaf yellowing issue",
    messages: [
      {
        role: "user",
        content: "Wheat leaves turning yellow",
      },
      {
        role: "assistant",
        content:
          "Yellowing wheat leaves usually point to nitrogen deficiency or early-stage rust.",
        causes: [
          "Nitrogen deficiency",
          "Yellow rust (Puccinia striiformis)",
          "Waterlogging at the root zone",
        ],
        treatment: [
          "Apply urea (40–50 kg/acre) if nitrogen deficiency is confirmed",
          "Spray Propiconazole 25% EC if rust pustules are visible",
        ],
        prevention: [
          "Use rust-resistant wheat varieties",
          "Avoid excess irrigation",
        ],
      },
    ],
  });

  await Chat.create({
    userId: ramesh._id,
    title: "Best crops for monsoon season",
    messages: [
      { role: "user", content: "What is the best crop for monsoon season?" },
      {
        role: "assistant",
        content:
          "For the Kharif (monsoon) season, these crops perform reliably across most Indian regions.",
        causes: [],
        treatment: ["Rice", "Maize", "Soybean", "Cotton", "Pigeon pea (Arhar)"],
        prevention: [
          "Ensure field bunding to manage excess water",
          "Choose short-duration varieties in heavy-rainfall zones",
        ],
      },
    ],
  });

  await Chat.create({
    userId: sita._id,
    title: "Rice disease identification",
    messages: [
      { role: "user", content: "My rice leaves have brown spots" },
      {
        role: "assistant",
        content:
          "Brown spots on rice leaves typically indicate Brown Spot disease caused by Helminthosporium oryzae.",
        causes: [
          "Helminthosporium oryzae fungus",
          "Nutrient-deficient soils (especially potassium)",
          "High humidity",
        ],
        treatment: [
          "Spray Mancozeb 75% WP at 2g/litre",
          "Apply potassium fertilizer",
        ],
        prevention: [
          "Use certified disease-free seeds",
          "Maintain proper spacing",
        ],
      },
    ],
  });

  console.log("✓  Created 3 sample chat conversations");

  await Notification.insertMany([
    {
      userId: ramesh._id,
      type: "weather",
      title: "Heavy rainfall expected Wednesday",
      message: "Delay pesticide spraying until conditions clear.",
      read: false,
    },
    {
      userId: ramesh._id,
      type: "pest",
      title: "Pest outbreak alert — Nashik region",
      message: "Increased bollworm activity reported nearby. Inspect cotton fields.",
      read: false,
    },
    {
      userId: ramesh._id,
      type: "scheme",
      title: "PM-KISAN installment update",
      message: "Next installment window opens 1 Aug 2026.",
      read: true,
    },
    {
      userId: sita._id,
      type: "weather",
      title: "Flood warning — Bihar",
      message: "Heavy rains expected this week. Protect low-lying fields.",
      read: false,
    },
  ]);

  console.log("✓  Created 4 notifications");

  console.log("\n🎉  Seed complete!\n");
  console.log("Demo credentials:");
  console.log("  Farmer : ramesh@example.com / Demo@1234");
  console.log("  Farmer : sita@example.com   / Demo@1234");
  console.log("  Admin  : admin@agriai.in    / Admin@1234\n");

  await mongoose.disconnect();
}

seed().catch((err) => {
  console.error("Seed failed:", err);
  process.exit(1);
});
