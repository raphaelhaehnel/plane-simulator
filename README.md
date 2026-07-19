# PlanesSimulator API

Simulates planes flying in a line or circle formation and lets you control multiple simulations
("scenarios") over a simple JSON HTTP API.

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
For `"type": "CIRCLE"` formations, replace `destLatRad`/`destLonRad`/`spacingMeters` with
`"radiusMeters": 5000`.

Response: `{ "id": "<uuid>" }`

### `GET /getScenarios`

Returns every scenario's config, status (`CREATED` / `RUNNING` / `PAUSED`), and live plane
positions:
```json
[
  {
    "id": "...", "status": "RUNNING", "formation": { "type": "LINE", ... },
    "planes": [ { "index": 0, "latRad": 0.357, "lonRad": 0.984, "headingDeg": 40.5 } ]
  }
]
```

### `POST /start`, `POST /pause`, `POST /deleteScenario`

Body: `{ "id": "<uuid>" }`. `/start` also resumes a paused scenario from where it left off.

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
curl -X POST http://localhost:8080/deleteScenario -H "Content-Type: application/json" -d '{"id":"<id>"}'
```

## Errors

`400` invalid request, `404` unknown scenario id, `405` wrong HTTP method — body is
`{ "error": "..." }`.
