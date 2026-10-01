// Runs the Inama v1 router on Node's built-in http server (no Express needed).
// Used by the zero-dependency dev server and the automated tests.
import http from "node:http";
import { handleRequest, streamReader } from "./core.js";

export function createNodeServer(router, services, { prefix = "/api/v1" } = {}) {
  return http.createServer(async (req, res) => {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept-Language");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
    if (req.method === "OPTIONS") {
      res.writeHead(204).end();
      return;
    }
    if (!req.url.startsWith(prefix)) {
      res.writeHead(404, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ success: false, error: { code: "not_found", message: `Inama API lives under ${prefix}` } }));
      return;
    }
    const result = await handleRequest(
      router,
      {
        method: req.method,
        url: req.url.slice(prefix.length) || "/",
        headers: req.headers,
        readBody: streamReader(req),
      },
      services,
    );
    res.writeHead(result.status, { "Content-Type": "application/json; charset=utf-8" });
    res.end(JSON.stringify(result.body));
  });
}
