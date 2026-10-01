import mongoose from "mongoose";
import { config } from "../../config/index.js";

let isConnected = false;

export async function connectDB() {
  if (isConnected) return;

  mongoose.set("strictQuery", true);

  mongoose.connection.on("connected", () => {
    console.log("✅  MongoDB connected:", mongoose.connection.host);
    isConnected = true;
  });

  mongoose.connection.on("error", (err) => {
    console.error("❌  MongoDB error:", err.message);
  });

  mongoose.connection.on("disconnected", () => {
    console.warn("⚠️   MongoDB disconnected — attempting reconnect...");
    isConnected = false;
  });

  await mongoose.connect(config.mongo.uri, {
    serverSelectionTimeoutMS: 5000,
    socketTimeoutMS: 45000,
  });
}

export async function disconnectDB() {
  if (!isConnected) return;
  await mongoose.disconnect();
  isConnected = false;
}

export { mongoose };
