import mongoose from "mongoose";

const diseaseResultSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    disease: { type: String, required: true },
    confidence: { type: Number, required: true, min: 0, max: 100 },
    crop: { type: String, required: true },
    severity: { type: String, enum: ["High", "Moderate", "Low"], required: true },
    causes: { type: [String], default: [] },
    remedies: { type: [String], default: [] },
    analysedByAI: { type: Boolean, default: false },
    imageSize: { type: Number },
    imageMimeType: { type: String },
  },
  { timestamps: true }
);

const DiseaseResult = mongoose.model("DiseaseResult", diseaseResultSchema);
export default DiseaseResult;
