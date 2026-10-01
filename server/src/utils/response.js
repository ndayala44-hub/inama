export const sendSuccess = (res, data, status = 200, message = "Success") => {
  res.status(status).json({ success: true, message, data });
};

export const sendError = (res, message, status = 400, errors = null) => {
  const payload = { success: false, message };
  if (errors) payload.errors = errors;
  res.status(status).json(payload);
};

export const isValidEmail = (email) => /^\S+@\S+\.\S+$/.test(email);

export const isValidPhone = (phone) =>
  /^\+?\d{10,13}$/.test(phone.replace(/\s/g, ""));

export const paginate = (query, page = 1, limit = 20) => {
  const skip = (page - 1) * limit;
  return { skip, limit: parseInt(limit) };
};
