/*
 * Tiny zero-dependency server:
 *   - serves the static UI from ./public
 *   - proxies /api/* to the simulator (same-origin, so no CORS setup needed there)
 *
 * Config via environment variables:
 *   PORT     port this UI listens on            (default 3000)
 *   SIM_URL  base URL of the simulator's API    (default http://localhost:8080)
 */
"use strict";

const http = require("http");
const fs = require("fs");
const path = require("path");

const PORT = Number(process.env.PORT || 3000);
const SIM_URL = process.env.SIM_URL || "http://localhost:8080";

const PUBLIC_DIR = path.join(__dirname, "public");
const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon"
};

http
  .createServer((req, res) => {
    if (req.url.startsWith("/api/")) {
      proxyToSimulator(req, res);
    } else {
      serveStatic(req, res);
    }
  })
  .listen(PORT, () => {
    console.log(`PlaneSim web UI on http://localhost:${PORT} -> proxying /api to ${SIM_URL}`);
  });

/** Forwards /api/<endpoint> to <SIM_URL>/<endpoint>, streaming body and response through. */
function proxyToSimulator(req, res) {
  let target;
  try {
    target = new URL(req.url.slice("/api".length), SIM_URL);
  } catch (e) {
    res.writeHead(400, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "Bad proxy path" }));
    return;
  }

  const upstream = http.request(
    target,
    {
      method: req.method,
      headers: { "content-type": req.headers["content-type"] || "application/json" }
    },
    (up) => {
      res.writeHead(up.statusCode, {
        "content-type": up.headers["content-type"] || "application/json"
      });
      up.pipe(res);
    }
  );

  upstream.on("error", (err) => {
    res.writeHead(502, { "content-type": "application/json" });
    res.end(
      JSON.stringify({
        error: `Cannot reach the simulator at ${SIM_URL} — is it running?`,
        detail: err.message
      })
    );
  });

  req.pipe(upstream);
}

function serveStatic(req, res) {
  const urlPath = decodeURIComponent(req.url.split("?")[0]);
  const relative = urlPath === "/" ? "index.html" : urlPath.replace(/^\/+/, "");
  const filePath = path.join(PUBLIC_DIR, relative);

  // Keep requests inside ./public
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403);
    res.end("Forbidden");
    return;
  }

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { "content-type": "text/plain" });
      res.end("Not found");
      return;
    }
    res.writeHead(200, { "content-type": MIME[path.extname(filePath)] || "application/octet-stream" });
    res.end(data);
  });
}
