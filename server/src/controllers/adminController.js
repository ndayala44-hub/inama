import { User, Chat, DiseaseResult } from "../models/index.js";
import { sendSuccess, sendError } from "../utils/response.js";

export const getReports = async (req, res, next) => {
  try {
    const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    const sixMonthsAgo = new Date();
    sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 5);
    sixMonthsAgo.setDate(1);
    sixMonthsAgo.setHours(0, 0, 0, 0);

    const [queryTrendRaw, userGrowthRaw, diseaseCropRaw] = await Promise.all([
      Chat.aggregate([
        { $unwind: "$messages" },
        {
          $match: {
            "messages.role": "user",
            "messages.createdAt": { $gte: sevenDaysAgo },
          },
        },
        {
          $group: {
            _id: {
              $dateToString: {
                format: "%Y-%m-%d",
                date: "$messages.createdAt",
              },
            },
            count: { $sum: 1 },
          },
        },
        { $sort: { _id: 1 } },
      ]),
      User.aggregate([
        { $match: { createdAt: { $gte: sixMonthsAgo } } },
        {
          $group: {
            _id: { $dateToString: { format: "%Y-%m", date: "$createdAt" } },
            count: { $sum: 1 },
          },
        },
        { $sort: { _id: 1 } },
      ]),
      DiseaseResult.aggregate([
        { $group: { _id: "$crop", count: { $sum: 1 } } },
        { $sort: { count: -1 } },
        { $limit: 6 },
      ]),
    ]);

    const queryTrend = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date(Date.now() - i * 24 * 60 * 60 * 1000);
      const key = d.toISOString().slice(0, 10);
      const label = d.toLocaleDateString("en-US", { weekday: "short" });
      const found = queryTrendRaw.find((r) => r._id === key);
      queryTrend.push({ day: label, queries: found?.count || 0 });
    }

    const userGrowth = [];
    for (let i = 5; i >= 0; i--) {
      const d = new Date();
      d.setDate(1);
      d.setMonth(d.getMonth() - i);
      const key = d.toISOString().slice(0, 7);
      const label = d.toLocaleDateString("en-US", { month: "short" });
      const found = userGrowthRaw.find((r) => r._id === key);
      userGrowth.push({ month: label, users: found?.count || 0 });
    }

    const diseaseByCrop = diseaseCropRaw.map((r) => ({
      name: r._id || "Unknown",
      value: r.count,
    }));

    sendSuccess(res, { queryTrend, userGrowth, diseaseByCrop });
  } catch (err) {
    next(err);
  }
};

export const getAdminStats = async (req, res, next) => {
  try {
    const [totalUsers, totalChats, totalDiseaseReports, activeUsers] =
      await Promise.all([
        User.countDocuments(),
        Chat.countDocuments(),
        DiseaseResult.countDocuments(),
        User.countDocuments({ status: "active" }),
      ]);

    const allChats = await Chat.find().select("messages");
    const totalMessages = allChats.reduce(
      (sum, c) => sum + c.messages.length,
      0,
    );

    const recentChats = await Chat.find()
      .sort({ updatedAt: -1 })
      .limit(5)
      .populate("userId", "name");

    const formattedRecent = recentChats.map((c) => {
      const lastUserMsg = c.messages.filter((m) => m.role === "user").at(-1);
      return {
        id: c._id,
        user: c.userId?.name || "Unknown",
        query: lastUserMsg?.content || c.title,
        time: c.updatedAt,
      };
    });

    sendSuccess(res, {
      stats: {
        totalUsers,
        totalChats,
        totalMessages,
        totalDiseaseReports,
        activeUsers,
      },
      recentChats: formattedRecent,
    });
  } catch (err) {
    next(err);
  }
};

export const listUsers = async (req, res, next) => {
  try {
    const { search, status, role, page = 1, limit = 20 } = req.query;
    const filter = {};
    if (status) filter.status = status;
    if (role) filter.role = role;
    if (search) {
      const q = new RegExp(search, "i");
      filter.$or = [{ name: q }, { email: q }];
    }

    const skip = (parseInt(page) - 1) * parseInt(limit);
    const [usersRaw, total] = await Promise.all([
      User.find(filter)
        .sort({ createdAt: -1 })
        .skip(skip)
        .limit(parseInt(limit)),
      User.countDocuments(filter),
    ]);

    const userIds = usersRaw.map((u) => u._id);
    const chatCounts = await Chat.aggregate([
      { $match: { userId: { $in: userIds } } },
      { $group: { _id: "$userId", count: { $sum: 1 } } },
    ]);
    const countMap = Object.fromEntries(
      chatCounts.map((c) => [c._id.toString(), c.count]),
    );

    const users = usersRaw.map((u) => ({
      ...u.toSafeObject(),
      queries: countMap[u._id.toString()] || 0,
    }));

    sendSuccess(res, {
      users,
      total,
      page: parseInt(page),
      totalPages: Math.ceil(total / parseInt(limit)),
    });
  } catch (err) {
    next(err);
  }
};

export const getUser = async (req, res, next) => {
  try {
    const user = await User.findById(req.params.id);
    if (!user) return sendError(res, "User not found.", 404);
    const queries = await Chat.countDocuments({ userId: user._id });
    sendSuccess(res, { user: { ...user.toSafeObject(), queries } });
  } catch (err) {
    next(err);
  }
};

export const createUser = async (req, res, next) => {
  try {
    const { name, email, phone, password, role = "farmer" } = req.body;
    if (!name || !email || !password) {
      return sendError(res, "name, email, and password are required.");
    }
    const validRoles = ["farmer", "admin"];
    if (!validRoles.includes(role)) {
      return sendError(res, `role must be one of: ${validRoles.join(", ")}.`);
    }

    const existing = await User.findOne({ email: email.toLowerCase() });
    if (existing)
      return sendError(res, "A user with this email already exists.");

    const user = await User.create({
      name,
      email: email.toLowerCase(),
      phone,
      password,
      role,
    });
    sendSuccess(res, { user: user.toSafeObject() }, 201, "User created.");
  } catch (err) {
    next(err);
  }
};

export const updateUser = async (req, res, next) => {
  try {
    const { name, email, phone, role, status } = req.body;
    const updates = {};
    if (name !== undefined) updates.name = name;
    if (email !== undefined) updates.email = email.toLowerCase();
    if (phone !== undefined) updates.phone = phone;
    if (role !== undefined) {
      const validRoles = ["farmer", "admin"];
      if (!validRoles.includes(role))
        return sendError(res, `role must be one of: ${validRoles.join(", ")}.`);
      if (req.params.id === req.user._id.toString() && role !== "admin") {
        return sendError(res, "You cannot remove your own admin role.");
      }
      updates.role = role;
    }
    if (status !== undefined) {
      const validStatuses = ["active", "inactive", "suspended"];
      if (!validStatuses.includes(status))
        return sendError(
          res,
          `status must be one of: ${validStatuses.join(", ")}.`,
        );
      if (req.params.id === req.user._id.toString())
        return sendError(res, "You cannot change your own status.");
      updates.status = status;
    }

    if (email !== undefined) {
      const clash = await User.findOne({
        email: updates.email,
        _id: { $ne: req.params.id },
      });
      if (clash) return sendError(res, "Another user already uses this email.");
    }

    const user = await User.findByIdAndUpdate(req.params.id, updates, {
      new: true,
      runValidators: true,
    });
    if (!user) return sendError(res, "User not found.", 404);
    sendSuccess(res, { user: user.toSafeObject() }, 200, "User updated.");
  } catch (err) {
    next(err);
  }
};

export const updateUserStatus = async (req, res, next) => {
  try {
    const { status } = req.body;
    const valid = ["active", "inactive", "suspended"];
    if (!valid.includes(status))
      return sendError(res, `status must be one of: ${valid.join(", ")}.`);

    if (req.params.id === req.user._id.toString()) {
      return sendError(res, "You cannot change your own status.");
    }

    const user = await User.findByIdAndUpdate(
      req.params.id,
      { status },
      { new: true },
    );
    if (!user) return sendError(res, "User not found.", 404);
    sendSuccess(
      res,
      { user: user.toSafeObject() },
      200,
      `User status updated to ${status}.`,
    );
  } catch (err) {
    next(err);
  }
};

export const deleteUser = async (req, res, next) => {
  try {
    if (req.params.id === req.user._id.toString()) {
      return sendError(res, "You cannot delete your own account via admin.");
    }
    const user = await User.findByIdAndDelete(req.params.id);
    if (!user) return sendError(res, "User not found.", 404);
    await Promise.all([
      Chat.deleteMany({ userId: req.params.id }),
      DiseaseResult.deleteMany({ userId: req.params.id }),
    ]);
    sendSuccess(res, null, 200, "User and associated data removed.");
  } catch (err) {
    next(err);
  }
};

export const getChatById = async (req, res, next) => {
  try {
    const chat = await Chat.findById(req.params.id).populate(
      "userId",
      "name email",
    );
    if (!chat) return sendError(res, "Chat not found.", 404);
    sendSuccess(res, {
      chat: {
        id: chat._id,
        userId: chat.userId?._id,
        userName: chat.userId?.name || "Unknown",
        userEmail: chat.userId?.email || "",
        title: chat.title,
        messages: chat.messages,
        createdAt: chat.createdAt,
        updatedAt: chat.updatedAt,
      },
    });
  } catch (err) {
    next(err);
  }
};

export const getChatLogs = async (req, res, next) => {
  try {
    const { userId, page = 1, limit = 20 } = req.query;
    const filter = userId ? { userId } : {};
    const skip = (parseInt(page) - 1) * parseInt(limit);

    const [chats, total] = await Promise.all([
      Chat.find(filter)
        .sort({ updatedAt: -1 })
        .skip(skip)
        .limit(parseInt(limit))
        .populate("userId", "name email"),
      Chat.countDocuments(filter),
    ]);

    const enriched = chats.map((c) => ({
      id: c._id,
      userId: c.userId?._id,
      userName: c.userId?.name || "Unknown",
      userEmail: c.userId?.email || "",
      title: c.title,
      messageCount: c.messages.length,
      lastMessage: c.messages.at(-1),
      updatedAt: c.updatedAt,
    }));

    sendSuccess(res, {
      chats: enriched,
      total,
      page: parseInt(page),
      totalPages: Math.ceil(total / parseInt(limit)),
    });
  } catch (err) {
    next(err);
  }
};
