import { config } from "../../config/index.js";

export const errorHandler = (err, req, res, _next) => {
  console.error(`[${new Date().toISOString()}] ${req.method} ${req.path} — ${err.message}`);

  // Mongoose validation error
  if (err.name === "ValidationError") {
    const messages = Object.values(err.errors).map((e) => e.message);
    return res.status(400).json({ success: false, message: messages[0], errors: messages });
  }

  // Mongoose duplicate key
  if (err.code === 11000) {
    const field = Object.keys(err.keyPattern || {})[0] || "field";
    return res.status(409).json({ success: false, message: `An account with this ${field} already exists.` });
  }

  // Mongoose cast error (bad ObjectId)
  if (err.name === "CastError") {
    return res.status(400).json({ success: false, message: "Invalid ID format." });
  }

  const status = err.status || err.statusCode || 500;
  const message = status === 500 && !config.isDev ? "Something went wrong. Please try again." : err.message;

  res.status(status).json({
    success: false,
    message,
    ...(config.isDev && status === 500 && { stack: err.stack }),
  });
};

export const notFound = (req, res) => {
  res.status(404).json({ success: false, message: `Route ${req.method} ${req.path} not found.` });
};
