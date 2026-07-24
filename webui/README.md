# PlaneSim Web UI

A minimal blue/white web console for sending requests to the PlanesSimulator HTTP API —
a replacement for Postman, nothing more. Zero npm dependencies (plain Node), no build step.

## Run

```
npm start
```

Then open http://localhost:3000. The simulator must be running (default
`http://localhost:8080`).

Configuration via environment variables:

| Variable  | Default                 | Meaning                              |
|-----------|-------------------------|--------------------------------------|
| `PORT`    | `3000`                  | Port this UI listens on              |
| `SIM_URL` | `http://localhost:8080` | Base URL of the simulator's HTTP API |

Example on the Linux server:

```
PORT=3000 SIM_URL=http://localhost:8080 npm start
```

## How it works

- `server.js` — tiny Node server that serves the static page and proxies every
  `/api/*` request to the simulator (`/api/start` → `<SIM_URL>/start`). Same-origin
  proxying means the simulator never needs CORS headers.
- `public/endpoints.js` — **the only file to edit when the API grows.** A declarative
  list of endpoints and their form fields (name, kind, default, required, conditional
  visibility via `showIf`). Dot-notation field names (`formation.type`) build nested
  JSON bodies. Scenario types and formations are *not* listed here — the page fetches
  them from the simulator (`GET /getTypes`, `GET /getFormations`) on load and builds
  the form from the response.
- `public/app.js` — generic renderer: builds the nav, forms, and response viewer from
  the catalog. Only needs changes for brand-new input behaviors, not new endpoints.
- `public/style.css` — the blue/white theme.

## Extending

**New scenario type or formation:** nothing to do here. The UI populates the Type and
Formation selects (and each formation's input fields) from `GET /getTypes` and
`GET /getFormations` at page load, so a type or formation added in the backend appears
automatically. (Optionally add a pre-filled default for a new formation field to
`FIELD_DEFAULTS` in `public/endpoints.js` — without one it just starts blank.)

**New field on an existing request:** add one entry to the endpoint's `fields` array in
`public/endpoints.js`. Use `showIf: (values) => ...` if it should appear only for some
scenario type or formation.

**New request:** add one object to the array returned by `buildEndpoints` with `method`,
`path`, and its `fields`. It appears in the nav automatically.
