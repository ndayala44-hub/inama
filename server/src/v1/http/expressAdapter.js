// Mounts the Inama v1 router inside the existing AgriAI Express app.
// Mount it BEFORE express.json() so the v1 layer reads the raw body itself (JSON or multipart);
// if a body parser already ran, the parsed JSON body is reused.
import { handleRequest, streamReader } from "./core.js";

export function inamaExpressMiddleware(router, services) {
  return async (req, res) => {
    const alreadyParsed = req.body && typeof req.body === "object" && Object.keys(req.body).length > 0;
    const readBody = alreadyParsed ? async () => Buffer.from(JSON.stringify(req.body)) : streamReader(req);
    const result = await handleRequest(
      router,
      {
        method: req.method,
        // Inside app.use(prefix, …) Express strips the prefix from req.url.
        url: req.url || "/",
        headers: alreadyParsed ? { ...req.headers, "content-type": "application/json" } : req.headers,
        readBody,
      },
      services,
    );
    res.status(result.status).json(result.body);
  };
}
