// Minimal HS256 JWT + phone OTP helpers for the Inama v1 API, built on node:crypto so the v1
// layer runs without extra packages. Tokens carry the farmer id and phone only.
import crypto from "node:crypto";
import { config } from "../../../config/index.js";

const b64url = (buf) => Buffer.from(buf).toString("base64").replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
const fromB64url = (s) => Buffer.from(s.replace(/-/g, "+").replace(/_/g, "/"), "base64");

export function signToken(payload, ttlSeconds = config.inama.tokenTtlSeconds) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const body = b64url(JSON.stringify({ ...payload, iss: "inama-v1", iat: now, exp: now + ttlSeconds }));
  const sig = b64url(crypto.createHmac("sha256", config.jwt.secret).update(`${header}.${body}`).digest());
  return `${header}.${body}.${sig}`;
}

export function verifyToken(token) {
  if (typeof token !== "string") return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [header, body, sig] = parts;
  const expected = b64url(crypto.createHmac("sha256", config.jwt.secret).update(`${header}.${body}`).digest());
  const a = Buffer.from(sig);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  try {
    const payload = JSON.parse(fromB64url(body).toString("utf8"));
    if (payload.iss !== "inama-v1") return null;
    if (typeof payload.exp === "number" && payload.exp < Math.floor(Date.now() / 1000)) return null;
    return payload;
  } catch {
    return null;
  }
}

/** Normalises Rwandan mobile numbers to +2507XXXXXXXX, or returns null. */
export function normalizeRwandaPhone(input) {
  const digits = String(input || "").replace(/[^\d+]/g, "");
  let local = digits;
  if (local.startsWith("+250")) local = local.slice(4);
  else if (local.startsWith("250")) local = local.slice(3);
  if (local.startsWith("0")) local = local.slice(1);
  return /^7[2-9]\d{7}$/.test(local) ? `+250${local}` : null;
}

export function generateOtp(length = config.inama.otp.length) {
  let code = "";
  for (let i = 0; i < length; i++) code += crypto.randomInt(0, 10).toString();
  return code;
}

export function hashOtp(phone, code) {
  return crypto.createHmac("sha256", config.jwt.secret).update(`${phone}:${code}`).digest("hex");
}
