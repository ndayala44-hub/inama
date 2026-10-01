import mongoose from "mongoose";

const bookmarkSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    type: {
      type: String,
      enum: ["chat", "disease", "crop", "soil"],
      required: true,
    },
    title: { type: String, required: true, trim: true },
    summary: { type: String, required: true },
    tags: { type: [String], default: [] },
    sourcePath: { type: String, default: null },
    sourceId: { type: String, default: null },
  },
  { timestamps: true }
);

bookmarkSchema.index({ userId: 1, sourceId: 1 }, { unique: true, sparse: true });

const Bookmark = mongoose.model("Bookmark", bookmarkSchema);
export default Bookmark;
