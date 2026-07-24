# How to add entities

This guide explains exactly what to do to add a new simulated object type (like `Plane` or
`Weather`) to the simulator.

## Step 0 — Answer the only question that matters

**Does the new type have coordinates (latitude/longitude) or not?**

- **Geographic** — it has a position, gets placed by a formation (LINE/CIRCLE/ORBIT), and may move
  or stay fixed. Examples: `Plane` (mobile), `Radar` (static).
- **Non-geographic** — it has no position at all; its field values are just regenerated every
  tick. Example: `Weather`.

That's the *only* architectural distinction. Don't think "is it like a plane?" — think "does it
have a position?".

## What you never need to touch

The whole design exists so that these stay unchanged no matter what type you add:

| Class | Why it doesn't change |
|---|---|
| `SimulationEngine` | Generic over `T`; only sees `SimulatedEntity<T>` |
| `FormationPlanner` | Generic over `T`; places any geographic type |
| `ScenarioManager` | Dispatches through the injected factory map |
| `ScenarioPublisher` | One `send(Entity)` for every type; live state is captured generically via reflection (`GeoFieldReader` / `NonGeoFieldReader`) |
| `NetworkManager` | `send(Entity, String)` already accepts any `Entity` subclass |
| `RequestMapper` / HTTP handlers | Dispatch on `ScenarioType.category()`, which you set in one place |
| `GeoLiveState` / `NonGeoLiveState` / the DTOs | Deliberately shaped by *category*, not by object type |
| The Swing UI (`planesim.view`) | View-only scaffolding; renders geographic objects generically and skips non-geographic ones |

A new type is automatically reachable over HTTP (`POST /createScenario` with `"type":"YOUR_TYPE"`)
and automatically advertised by `GET /getTypes` — both read `ScenarioType.values()`.

---

## Adding a GEOGRAPHIC type (4 steps)

Example used below: a `Ship`.

### 1. The external class — `planesim.external.Ship`

A placeholder that mirrors the real host-system class (it gets deleted and replaced by the real
import on integration). It must extend `Entity`, and it **must** expose its coordinates as public
`latitude` / `longitude` fields (radians) — plus `heading` (degrees) if it's a moving type. These
names are the integration contract: `GeoFieldReader` reads them reflectively to serve
`GET /getScenarios`, and `ObjectWriters` writes them.

```java
public class Ship extends Entity {
    public double latitude;   // radians  — required name
    public double longitude;  // radians  — required name
    public double heading;    // degrees  — required name for mobile types; omit for static ones
    public double vx;         // whatever else your type has...
    public double vy;

    @Override
    public String toString() { ... }  // used in the per-send log line
}
```

### 2. The field mapping — one constant in `planesim.core.engine.ObjectWriters`

The only place that knows how to project the simulated local-frame state
(lat/lon/altitude/velocity) onto your type's fields:

```java
public static final ObjectWriter<Ship> SHIP = (ship, latRad, lonRad, altitudeMeters, velocity) -> {
    ship.latitude = latRad;
    ship.longitude = lonRad;
    ship.vx = velocity.x();
    ship.vy = velocity.y();
    ship.heading = GeoMath.headingDegrees(velocity);
};
```

### 3. The movement choice — usually zero code

Pick a `MovementStyle`:

- `MOBILE` — reuses the existing per-formation behaviors (`LineBounceBehavior` shuttles along a
  route, `CircleRandomWalkBehavior` wanders).
- `STATIC` — reuses `StaticBehavior`; the object never moves (like a radar).

Only if neither fits do you write a new `FlightBehavior` implementation (in
`planesim.core.behavior`) and wire it into `FormationPlanner`'s `movementStyle == ...` branches.
(If what you actually want is a new placement/motion *pattern* rather than a new object type, see
"How to add formation.md" instead.)

### 4. Registering the type — `ScenarioType` + one factory constant

In `planesim.core.scenario.ScenarioType`:

```java
SHIP(ScenarioCategory.GEOGRAPHIC),
```

In `planesim.core.scenario.ScenarioEngineFactories`, one constant plus a `DEFAULTS` entry:

```java
public static final ScenarioEngineFactory SHIP = (config, publisher, scheduler) ->
        SimulationEngine.<Ship>create((GeoScenarioConfig) config, MovementStyle.MOBILE,
                publisher::send, Ship::new, ObjectWriters.SHIP, scheduler);

// ...and add ScenarioType.SHIP, SHIP to the DEFAULTS map.
```

(`ScenarioManager` validates at startup that every `ScenarioType` has a factory, so forgetting the
`DEFAULTS` entry fails fast, not on the first request.)

**Done.** Verify:

```
curl -X POST http://localhost:8080/createScenario -H "Content-Type: application/json" -d '{
  "type":"SHIP","topicName":"ships","amount":3,"originLatRad":0.3575,"originLonRad":0.9838,
  "sendInterval":500,"formation":{"type":"LINE","destLatRad":0.43,"destLonRad":1.05,"spacingMeters":2000}
}'
curl -X POST http://localhost:8080/start -H "Content-Type: application/json" -d '{"id":"<id>"}'
curl http://localhost:8080/getScenarios   # geoObjects[] shows live lat/lon/heading
```

---

## Adding a NON-GEOGRAPHIC type (3 steps)

Example used below: an `AirQuality` sensor reading.

### 1. The external class — `planesim.external.AirQuality`

Extends `Entity`; has whatever public fields the reading consists of — **no** coordinates, and no
required field names (the fields are captured generically by name via reflection):

```java
public class AirQuality extends Entity {
    public double pm25;
    public int aqi;
    public boolean alert;

    @Override
    public String toString() { ... }
}
```

### 2. The value generation — one constant in `planesim.core.engine.ValueGenerators`

Produces each tick's field values directly (use `ThreadLocalRandom.current()`, not a captured
`Random` — ticks can run on different pool threads):

```java
public static final ValueGenerator<AirQuality> AIR_QUALITY = airQuality -> {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    airQuality.pm25 = random.nextDouble(150.0);
    airQuality.aqi = random.nextInt(0, 301);
    airQuality.alert = airQuality.aqi > 200;
};
```

### 3. Registering the type — `ScenarioType` + one factory constant

```java
// ScenarioType:
AIR_QUALITY(ScenarioCategory.NON_GEOGRAPHIC),

// ScenarioEngineFactories (+ DEFAULTS entry):
public static final ScenarioEngineFactory AIR_QUALITY = (config, publisher, scheduler) ->
        SimulationEngine.<AirQuality>createValueEngine((NonGeoScenarioConfig) config,
                publisher::send, AirQuality::new, ValueGenerators.AIR_QUALITY, scheduler);
```

**Done.** No formation, no origin, no coordinates anywhere:

```
curl -X POST http://localhost:8080/createScenario -H "Content-Type: application/json" -d '{
  "type":"AIR_QUALITY","topicName":"air","amount":2,"sendInterval":500
}'
```

`GET /getScenarios` returns each object's fields as a generic map — automatically:

```json
"nonGeoObjects": [ { "index": 0, "fields": { "pm25": 42.7, "aqi": 118, "alert": false } } ]
```

Don't add the type to `planesim.view.ui` — the UI only renders geographic objects, by design.

---

## Rules that keep this working

- **Coordinates are WGS84 radians** on every geographic external type and everything upstream of
  it. Local meters (`Vector2`) never leak out of `GeoMath`.
- **`latitude` / `longitude` / `heading` are the required public field names** for geographic
  types (heading only for mobile ones). They're the documented integration contract with the real
  host system, and `GeoFieldReader` depends on them at runtime.
- **Never add coordinates to a non-geographic type** "for consistency" — the categories exist
  precisely so you don't have to.
- **Don't special-case your type inside `core`** (no `instanceof Ship` anywhere) — field mapping
  belongs in `ObjectWriters`/`ValueGenerators`, movement in the `MovementStyle`/behavior choice,
  registration in `ScenarioEngineFactories`. That's the whole recipe.
- Placeholders in `planesim.external` stay dumb: fields + `toString()` only. They get deleted on
  integration into the real environment.

## Summary

| | Geographic | Non-geographic |
|---|---|---|
| External class in `planesim.external` | ✅ (must use `latitude`/`longitude`/`heading` names) | ✅ (any field names) |
| `ObjectWriters` constant | ✅ | — |
| `ValueGenerators` constant | — | ✅ |
| `MovementStyle` / behavior choice | ✅ (usually just pick MOBILE or STATIC) | — |
| `ScenarioType` value | ✅ `GEOGRAPHIC` | ✅ `NON_GEOGRAPHIC` |
| `ScenarioEngineFactories` constant + `DEFAULTS` entry | ✅ | ✅ |
| `ScenarioPublisher`, `ScenarioManager`, DTOs, `RequestMapper`, UI | never | never |
