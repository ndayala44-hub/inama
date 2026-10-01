import jwt from "jsonwebtoken";
import { config } from "../../config/index.js";
import { User } from "../models/index.js";
import { sendError } from "../utils/response.js";

export const protect = async (req, res, next) => {
  const auth = req.headers.authorization;
  if (!auth?.startsWith("Bearer ")) {
    return sendError(res, "No token provided. Please log in.", 401);
  }

  const token = auth.split(" ")[1];
  try {
    const decoded = jwt.verify(token, config.jwt.secret);
    const user = await User.findById(decoded.id).select("-password");
    if (!user) return sendError(res, "User no longer exists.", 401);
    if (user.status === "suspended")
      return sendError(res, "Your account has been suspended.", 403);
    req.user = user;
    next();
  } catch (err) {
    if (err.name === "TokenExpiredError")
      return sendError(res, "Session expired. Please log in again.", 401);
    return sendError(res, "Invalid token. Please log in.", 401);
  }
};

export const restrictTo =
  (...roles) =>
  (req, res, next) => {
    if (!roles.includes(req.user?.role)) {
      return sendError(
        res,
        "You do not have permission to perform this action.",
        403,
      );
    }
    next();
  };
