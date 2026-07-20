# PlanesSimulator API

Simulates objects — planes, radars (arranged in a line or circle formation) and weather (no
position at all) — and lets you control multiple simulations ("scenarios") over a simple JSON HTTP
API. Planes fly their formation; radars are static (placed by the formation but never move);
weather has no formation or coordinates — it's just periodic readings.

## Run it

```
mvn compile
mvn exec:java -Dexec.mainClass=planesim.core.server.SimulationServerApp   # starts the API on :8080
mvn exec:java -Dexec.mainClass=planesim.view.ui.PlaneSimulatorUiApp       # optional: view-only map (needs the server running)
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
`type` is `"PLANE"`, `"RADAR"`, or `"WEATHER"`. A radar is static (never moves) — it still needs a
`formation` to decide where it's placed, but `speed` doesn't do anything for it. For `"type":
"CIRCLE"` formations, replace `destLatRad`/`destLonRad`/`spacingMeters` with
`"radiusMeters": 5000`.

**Weather is different: it has no coordinates.** Don't send `originLatRad`/`originLonRad`/
`formation`/`speed`/`altitude` for a `"WEATHER"` scenario — just `type`, `amount` (how many
independent readings), and `sendInterval`:
```json
{ "type": "WEATHER", "amount": 3, "sendInterval": 1000 }
```

Response: `{ "id": "<uuid>" }`

### `GET /getScenarios`

The one distinction that matters here is **geographic vs. non-geographic**. A `PLANE`/`RADAR`
scenario (geographic) populates `geoObjects` (positions). A `WEATHER` scenario (non-geographic —
and any future non-geographic type) has no coordinates, so it populates `nonGeoObjects` instead and
leaves `originLatRad`/`originLonRad`/`speed`/`altitude`/`formation`/`geoObjects` absent.
`nonGeoObjects[].fields` is a generic key/value map of whatever fields that object type has — for
weather that's `windVelocity`/`temperature`/`isSunny`, but the shape itself isn't weather-specific:
```json
[
  {
    "id": "...", "type": "PLANE", "status": "RUNNING", "formation": { "type": "LINE", ... },
    "geoObjects": [ { "index": 0, "latRad": 0.357, "lonRad": 0.984, "headingDeg": 40.5 } ]
  },
  {
    "id": "...", "type": "WEATHER", "status": "RUNNING",
    "nonGeoObjects": [ { "index": 0, "fields": { "windVelocity": 12.3, "temperature": 21.5, "isSunny": true } } ]
  }
]
```
For a `RADAR` scenario, `geoObjects[].headingDeg` is always `0.0` (a static object has no heading)
and `latRad`/`lonRad` never change between polls. For a `WEATHER` scenario, `nonGeoObjects[].fields`
change every poll — there's nothing static about weather.

**Note:** the view-only Swing UI (`PlaneSimulatorUiApp`) can only show scenarios with `geoObjects`
(planes/radars) on its map — it has no way to display `nonGeoObjects` (weather and any future
non-geographic type), since there's no position to plot. Fetch those via `GET /getScenarios`
instead.

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

# a weather scenario - no coordinates needed
curl -X POST http://localhost:8080/createScenario -H "Content-Type: application/json" -d '{
  "type":"WEATHER","amount":3,"sendInterval":1000
}'
```

## Errors

`400` invalid request (including a missing/blank `id`, an `amount` over 10,000, or an
`originLatRad` too close to a pole), `404` unknown scenario id, `405` wrong HTTP method, `429` too
many concurrent scenarios (100 max — delete one first) — body is `{ "error": "..." }`.
