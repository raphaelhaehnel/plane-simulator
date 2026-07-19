# PlanesSimulator API

Simulates objects (planes, radars) arranged in a line or circle formation and lets you control
multiple simulations ("scenarios") over a simple JSON HTTP API. Planes fly their formation; radars
are static — they get placed by the formation but never move.

## Run it

```
mvn compile
mvn exec:java -Dexec.mainClass=planesim.server.SimulationServerApp   # starts the API on :8080
mvn exec:java -Dexec.mainClass=planesim.ui.PlaneSimulatorUiApp       # optional: view-only map (needs the server running)
```

## Endpoints

All bodies are JSON. Coordinates are radians. `speed`/`altitude` are optional (default `230.0` m/s
/ `10000.0` m).

### `POST /createScenario`

```json
{
  "type": "PLANE",
  "amount": 5,
  "originLatRad": 0.3575,
  "originLonRad": 0.9838,
  "sendInterval": 500,
  "formation": {
    "type": "LINE",
    "destLatRad": 0.4300,
    "destLonRad": 1.0500,
    "spacingMeters": 2000
  }
}
```
`type` is `"PLANE"` or `"RADAR"`. A radar is static (never moves) — it still needs a `formation` to
decide where it's placed, but `speed` doesn't do anything for it. For `"type": "CIRCLE"`
formations, replace `destLatRad`/`destLonRad`/`spacingMeters` with `"radiusMeters": 5000`.

Response: `{ "id": "<uuid>" }`

### `GET /getScenarios`

Returns every scenario's config, status (`CREATED` / `RUNNING` / `PAUSED`), and live object
positions:
```json
[
  {
    "id": "...", "type": "PLANE", "status": "RUNNING", "formation": { "type": "LINE", ... },
    "objects": [ { "index": 0, "latRad": 0.357, "lonRad": 0.984, "headingDeg": 40.5 } ]
  }
]
```
For a `RADAR` scenario, `objects[].headingDeg` is always `0.0` (a static object has no heading) and
`latRad`/`lonRad` never change between polls.

### `POST /start`, `POST /pause`, `POST /deleteScenario`

Body: `{ "id": "<uuid>" }`. `/start` also resumes a paused scenario from where it left off.

### `POST /stopAll`

No body needed. Pauses every currently running scenario (same as calling `/pause` on each one).
Response: `{ "stoppedIds": ["<uuid>", ...] }` — the ids that were actually running and got stopped.

## Example

```
curl -X POST http://localhost:8080/createScenario -H "Content-Type: application/json" -d '{
  "type":"PLANE","amount":5,"originLatRad":0.3575,"originLonRad":0.9838,"sendInterval":500,
  "formation":{"type":"LINE","destLatRad":0.4300,"destLonRad":1.0500,"spacingMeters":2000}
}'
# -> {"id":"..."}

curl -X POST http://localhost:8080/start -H "Content-Type: application/json" -d '{"id":"<id>"}'
curl http://localhost:8080/getScenarios
curl -X POST http://localhost:8080/pause -H "Content-Type: application/json" -d '{"id":"<id>"}'
curl -X POST http://localhost:8080/stopAll
curl -X POST http://localhost:8080/deleteScenario -H "Content-Type: application/json" -d '{"id":"<id>"}'

# a static radar scenario
curl -X POST http://localhost:8080/createScenario -H "Content-Type: application/json" -d '{
  "type":"RADAR","amount":3,"originLatRad":0.3575,"originLonRad":0.9838,"sendInterval":1000,
  "formation":{"type":"CIRCLE","radiusMeters":6000}
}'
```

## Errors

`400` invalid request, `404` unknown scenario id, `405` wrong HTTP method — body is
`{ "error": "..." }`.
