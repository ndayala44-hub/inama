// Framework-agnostic HTTP core for the Inama v1 API.
// Handlers receive a plain context object and return { status, body }. Two thin adapters run
// the same handlers: expressAdapter.js (inside the AgriAI Express app) and nodeAdapter.js
// (zero-dependency dev server + tests). Multipart parsing uses the web-standard
// Request.formData() built into Node 18+, so both adapters parse uploads identically.
import { resolveLanguage, t } from "../i18n.js";
import { verifyToken } from "../auth/tokens.js";
import { config } from "../../../config/index.js";

export class HttpError extends Error {
  constructor(status, code, message) {
    super(message || code);
    this.status = status;
    this.code = code;
  }
}

export class Router {
  constructor() {
    this.routes = [];
  }
  add(method, pattern, handler, options = {}) {
    const keys = [];
    const regex = new RegExp(
      "^" +
        pattern.replace(/\/:(\w+)/g, (_, k) => {
          keys.push(k);
          return "/([^/]+)";
        }) +
        "/?$",
    );
    this.routes.push({ method, regex, keys, handler, auth: Boolean(options.auth) });
    return this;
  }
  get(p, h, o) {
    return this.add("GET", p, h, o);
  }
  post(p, h, o) {
    return this.add("POST", p, h, o);
  }
  put(p, h, o) {
    return this.add("PUT", p, h, o);
  }
  match(method, path) {
    let pathMatched = false;
    for (const r of this.routes) {
      const m = r.regex.exec(path);
      if (!m) continue;
      pathMatched = true;
      if (r.method !== method) continue;
      const params = {};
      r.keys.forEach((k, i) => (params[k] = decodeURIComponent(m[i + 1])));
      return { route: r, params };
    }
    return pathMatched ? { methodNotAllowed: true } : null;
  }
}

async function parseBody(method, headers, readBody) {
  if (method === "GET" || method === "HEAD") return { body: null, form: null };
  const type = String(headers["content-type"] || "");
  const limit = config.inama.maxImageBytes + 1024 * 1024;
  const raw = await readBody(limit);
  if (!raw || raw.length === 0) return { body: null, form: null };
  if (type.includes("application/json")) {
    try {
      return { body: JSON.parse(raw.toString("utf8")), form: null };
    } catch {
      throw new HttpError(400, "bad_json", "Body is not valid JSON.");
    }
  }
  if (type.includes("multipart/form-data")) {
    const request = new Request("http://inama.local/upload", { method: "POST", headers: { "content-type": type }, body: raw });
    return { body: null, form: await request.formData() };
  }
  return { body: null, form: null };
}

/**
 * @param router   Router
 * @param req      { method, url (path + query, relative to the API prefix), headers (lower-case), readBody(limit) → Buffer }
 * @param services injected dependencies (store, …)
 */
export async function handleRequest(router, req, services) {
  const url = new URL(req.url, "http://inama.local");
  const query = Object.fromEntries(url.searchParams.entries());
  const lang = resolveLanguage(req.headers["accept-language"], query.lang);
  try {
    const found = router.match(req.method, url.pathname);
    if (!found) throw new HttpError(404, "not_found", t(lang, "errors.not_found"));
    if (found.methodNotAllowed) throw new HttpError(405, "method_not_allowed", "Method not allowed.");
    let auth = null;
    if (found.route.auth) {
      const header = String(req.headers.authorization || "");
      auth = header.startsWith("Bearer ") ? verifyToken(header.slice(7)) : null;
      if (!auth) throw new HttpError(401, "unauthorized", t(lang, "errors.unauthorized"));
    }
    const { body, form } = await parseBody(req.method, req.headers, req.readBody);
    const result = await found.route.handler({
      method: req.method,
      path: url.pathname,
      params: found.params,
      query,
      headers: req.headers,
      lang,
      auth,
      body,
      form,
      ...services,
    });
    return { status: result.status || 200, body: { success: true, data: result.body ?? null } };
  } catch (err) {
    if (err instanceof HttpError) {
      return { status: err.status, body: { success: false, error: { code: err.code, message: err.message } } };
    }
    if (err?.code === "body_too_large") {
      return { status: 413, body: { success: false, error: { code: "image_too_big", message: t(lang, "errors.image_too_big") } } };
    }
    console.error("Inama v1 error:", err);
    return { status: 500, body: { success: false, error: { code: "server_error", message: "Something went wrong. Please try again." } } };
  }
}

/** Reads a Node IncomingMessage stream into a Buffer with a size limit. */
export function streamReader(stream) {
  return (limit) =>
    new Promise((resolve, reject) => {
      const chunks = [];
      let size = 0;
      let failed = false;
      stream.on("data", (chunk) => {
        if (failed) return;
        size += chunk.length;
        if (size > limit) {
          failed = true;
          const e = new Error("Body too large");
          e.code = "body_too_large";
          reject(e);
          stream.resume();
          return;
        }
        chunks.push(chunk);
      });
      stream.on("end", () => !failed && resolve(Buffer.concat(chunks)));
      stream.on("error", (e) => !failed && reject(e));
    });
}
