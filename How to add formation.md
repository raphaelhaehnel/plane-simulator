# How to add formation

This guide explains exactly what to do to add a new formation (like LINE, CIRCLE, or ORBIT) to
the simulator. A formation decides two things for **geographic** objects only:

1. **Placement** — where the N objects start (non-geographic objects like `Weather` have no
   position and skip formations entirely).
2. **Motion pattern** — how a `MOBILE` object flies from there (a `STATIC` object like a radar
   just stays at its placed point, whatever the formation).

The ORBIT formation (objects on a ring, flying along it clockwise) is used as the worked example
throughout — it was added exactly this way.

## What you never need to touch

| Class | Why it doesn't change |
|---|---|
| `SimulationEngine`, `SimulatedObject` | Only see a `FlightBehavior`, never a formation |
| `GeoScenarioConfig` | Holds any `FormationSpec` — the sealed interface, not a concrete type |
| `ScenarioManager` / `ScenarioPublisher` / `ScenarioEngineFactories` | Formation-agnostic; they work per object *type*, not per formation |
| `RequestMapper` | Parses and echoes formations by delegating to `FormationCatalog` — no per-formation branch |
| The webui | Builds its formation dropdown + fields from `GET /getFormations` at load time |
| The Swing view | Renders live positions; it neither knows nor cares what pattern produced them |

A new formation is automatically advertised by `GET /getFormations`, accepted by
`POST /createScenario`, and echoed by `GET /getScenarios` once it's in `FormationCatalog` (step 5) —
all three read the *same* registry, so they can never drift apart.

---

## The steps (ORBIT as the example)

### 1. The spec — a record in `planesim.core.formation`

Holds the formation's parameters, validates them in the compact constructor (that's what turns a
bad request into an HTTP 400 — `AbstractJsonHandler` maps `IllegalArgumentException` to 400), and
joins the sealed interface:

```java
public record OrbitFormation(double radiusMeters) implements FormationSpec {
    public OrbitFormation {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }
    }
}
```

Add it to the `permits` list in `FormationSpec`:

```java
public sealed interface FormationSpec permits LineFormation, CircleFormation, OrbitFormation {
}
```

### 2. The motion — a `FlightBehavior`, only if no existing one fits

Each `MOBILE` object gets its **own** behavior instance (never shared), so a behavior can hold
private mutable state without synchronization. Reuse `LineBounceBehavior`,
`CircleRandomWalkBehavior`, or `StaticBehavior` if the motion matches; otherwise add one in
`planesim.core.behavior`:

```java
public final class OrbitBehavior implements FlightBehavior {
    private final double radiusMeters;
    private final double speedMps;
    private double angleRad;   // private per-object state — fine, instances are never shared

    @Override
    public StepResult step(Vector2 position, Vector2 velocity, double dtSeconds) {
        angleRad -= (speedMps / radiusMeters) * dtSeconds;   // clockwise = decreasing angle
        Vector2 newPosition = new Vector2(Math.cos(angleRad), Math.sin(angleRad)).scaled(radiusMeters);
        Vector2 newVelocity = new Vector2(Math.sin(angleRad), -Math.cos(angleRad)).scaled(speedMps);
        return new StepResult(newPosition, newVelocity);
    }
}
```

Everything is in the **local flat frame** (meters, x = east, y = north, centered on the scenario
origin) — never lat/lon. The engine converts to lat/lon at the boundary via `GeoMath.toLatLon`.

Lesson from ORBIT worth keeping: if the path is a fixed shape, track your own parameter (here the
polar angle) and re-derive position/velocity from it each tick, instead of integrating the
velocity — integrating a turning velocity drifts off the shape a little more every tick.

### 3. The placement — a branch in `planesim.core.engine.FormationPlanner`

Add an `instanceof` dispatch line in `buildFormation` and a `buildXxxFormation` method that
places the N objects and picks each one's behavior. It's generic over `T`, so the same geometry
places planes, radars, or any future geographic type. Follow the two invariants the existing
builders keep:

- A `STATIC` object gets `Vector2.ZERO` velocity **and** `StaticBehavior`, regardless of the
  formation's natural motion — so even the very first published tick reports zero velocity.
- A `MOBILE` object's *initial* velocity should already point the way its behavior will fly it,
  so the first published tick is consistent with the motion that follows (ORBIT sets the initial
  velocity along the clockwise tangent; LINE sets it along the route).

```java
if (config.formation() instanceof OrbitFormation orbit) {
    return buildOrbitFormation(config, orbit, movementStyle, objectFactory, writer);
}
```

### 4. The wire fields — on `planesim.core.server.api.FormationDto`

`FormationDto` is one flat class holding the union of every formation's fields (boxed types, so
absent JSON fields stay `null`). Add only the fields your formation introduces — ORBIT added
nothing, it reuses CIRCLE's `radiusMeters`:

```java
// CIRCLE and ORBIT, required
public Double radiusMeters;
```

### 5. The registry — one `Descriptor` in `planesim.core.server.FormationCatalog`

This single entry does **everything wire-facing** for the formation: it advertises it on
`GET /getFormations` (name + fields, which is what the webui builds its form from), **parses** an
incoming request (`FormationDto → FormationSpec`), and **serializes** it back out on
`GET /getScenarios` (`FormationSpec → FormationDto`). Both directions of the round-trip live here,
so there's no separate echo branch to add anywhere else:

```java
new Descriptor("ORBIT", OrbitFormation.class,
        List.of(new Field("radiusMeters", "Radius (m)")),
        FormationCatalog::parseOrbit, FormationCatalog::serializeOrbit));

private static FormationSpec parseOrbit(FormationDto dto) {
    if (dto.radiusMeters == null) {
        throw new BadRequestException("ORBIT formation requires radiusMeters");
    }
    return new OrbitFormation(dto.radiusMeters);
}

private static FormationDto serializeOrbit(FormationSpec spec) {
    OrbitFormation orbit = (OrbitFormation) spec;   // safe: matched by Descriptor.specType
    FormationDto dto = new FormationDto();
    dto.type = "ORBIT";
    dto.radiusMeters = orbit.radiusMeters();
    return dto;
}
```

The `parser` only checks *presence* (missing field → 400 with a clear message); *range* checks live
in the record's compact constructor (step 1) — don't duplicate them here. The `specType` is how
`toDto` finds the right descriptor for a given spec, so the `serializer`'s cast is always safe.

**Never** reintroduce a formation `switch`/`if` in `RequestMapper` — neither for request parsing
nor for the echo. The whole point of `FormationCatalog` is that the advertised catalog, the
accepted requests, **and** the echoed responses all come from one place. `RequestMapper.toFormationDto`
is just a one-line call to `FormationCatalog.toDto(spec)`.

---

## Verify

```
curl http://localhost:8080/getFormations        # your formation + fields appear
curl -X POST http://localhost:8080/createScenario -H "Content-Type: application/json" -d '{
  "type":"PLANE","topicName":"test","amount":4,"originLatRad":0.3575,"originLonRad":0.9838,
  "sendInterval":500,"formation":{"type":"ORBIT","radiusMeters":6000}
}'
curl -X POST http://localhost:8080/start -H "Content-Type: application/json" -d '{"id":"<id>"}'
curl http://localhost:8080/getScenarios         # watch geoObjects[] move per your pattern
```

Also send a request with a missing/invalid parameter and confirm you get a 400 with a helpful
message. The webui (`node webui/server.js`, then http://localhost:3000) picks the formation up
automatically in its dropdown; the Swing view (`mvn exec:java
-Dexec.mainClass=planesim.view.ui.PlaneSimulatorUiApp`) shows the motion live.

## Rules that keep this working

- **All placement/motion math is local-frame meters** (`Vector2`); lat/lon (radians) exists only
  at the `GeoMath.toLocal`/`toLatLon` boundary. If your formation takes a geographic parameter
  (like LINE's destination), convert it once with `GeoMath.toLocal` and work in meters after.
- **Formations are geographic-only.** A non-geographic scenario (weather) never reaches
  `FormationPlanner` — don't try to give it one.
- **Respect `MovementStyle`.** Every formation must handle `STATIC` (zero velocity +
  `StaticBehavior`) — a radar can be placed in *any* formation shape.
- **Coordinates in requests are radians**, same as everywhere else in the API — don't accept
  degrees at this boundary.
- One behavior instance per object; behaviors may hold mutable state but must never be shared.

## Summary

| Step | Where | ORBIT example |
|---|---|---|
| 1. Spec record + `permits` | `core.formation` | `OrbitFormation(radiusMeters)` |
| 2. Behavior (only if needed) | `core.behavior` | `OrbitBehavior` |
| 3. Placement branch | `core.engine.FormationPlanner` | `buildOrbitFormation` |
| 4. Wire fields | `core.server.api.FormationDto` | none new (reuses `radiusMeters`) |
| 5. Catalog descriptor + parser + serializer | `core.server.FormationCatalog` | `"ORBIT"` + `parseOrbit` + `serializeOrbit` |
| webui / Swing view / `RequestMapper` / `ScenarioManager` / engine | — | never touched |
