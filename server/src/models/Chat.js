import mongoose from "mongoose";

const messageSchema = new mongoose.Schema(
  {
    role: { type: String, enum: ["user", "assistant"], required: true },
    content: { type: String, required: true },
    reply: { type: String },
    causes: { type: [String], default: [] },
    treatment: { type: [String], default: [] },
    prevention: { type: [String], default: [] },
  },
  { timestamps: true }
);

const chatSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    title: {
      type: String,
      required: true,
      trim: true,
      maxlength: 120,
    },
    messages: { type: [messageSchema], default: [] },
  },
  { timestamps: true }
);

// Index for fast user-specific queries sorted by latest
chatSchema.index({ userId: 1, updatedAt: -1 });

const Chat = mongoose.model("Chat", chatSchema);
export default Chat;
