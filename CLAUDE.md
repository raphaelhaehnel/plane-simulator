# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A standalone simulation module that drives a formation of planes (line or circle pattern) and
periodically publishes each plane's state (lat/lon, altitude, velocity, heading) to a
`NetworkApi`. It's designed to be dropped into a larger system: `Plane` and `NetworkApi` are
explicitly marked as **placeholders** standing in for externally-provided classes that don't
exist in this repo yet.

This repo will eventually be installed into a different environment where `Plane` and
`NetworkApi` are imported from an external library instead of defined locally. Everything in
`Plane.java` and `NetworkApi.java` here is a **mock** that exists only so this module compiles and
runs standalone — do not add behavior to them beyond matching the real library's shape, and don't
treat them as the real contract to design around.

The project's one real purpose is: **create simulated plane objects, move them over time, and
send their state through the NetworkApi.** That's `SimulationEngine`, `SimulatedPlane`,
`FlightBehavior`, `FormationPlanner`, `GeoMath`, and `Vector2`. On top of that core loop,
`planesim.scenario`/`planesim.server` expose a real, maintained JSON HTTP API
(`SimulationServerApp`) for creating, listing, starting, pausing (individually or all at once via
`/stopAll`), and deleting multiple concurrent simulation *scenarios* — this is a genuine feature,
not scaffolding. `SimulationApp` and the `planesim.ui` package (`PlaneSimulatorUiApp`, `MapPanel`,
`PlaneSnapshot`), by contrast, exist only to exercise and visually verify the simulation/API; they
are test/demo scaffolding, not features to expand for their own sake. In particular the UI is
deliberately **view-only** — it has no code path that can create, start, pause, or delete a
scenario, only `GET /getScenarios` polling — don't add one without being asked.

Coordinates on the simulated objects (`Plane.latitude`/`longitude`, and everywhere upstream of
them: `SimulationConfig`, `LineFormation`, `CircleFormation`) **must stay WGS84 radians** — that's
the format the real external `Plane` type requires, so it's not a local choice to change. Local
meters (`Vector2`, the flat frame) exist only as an internal computation detail behind
`GeoMath.toLocal`/`toLatLon`; they must never leak into the public/external-facing data (`Plane`,
`SimulationConfig`, `FormationSpec` implementations).

## Build / run

Maven project, Java 17, dependencies are Gson (JSON HTTP API) and Log4j2 (console logging),
`exec-maven-plugin` wired up, no test framework yet.

```
mvn compile
mvn exec:java -Dexec.mainClass=planesim.app.SimulationApp          # headless console demo
mvn exec:java -Dexec.mainClass=planesim.server.SimulationServerApp # HTTP API, default port 8080
mvn exec:java -Dexec.mainClass=planesim.ui.PlaneSimulatorUiApp     # view-only Swing dashboard, polls the API
```

The UI expects the server already running (it polls `http://localhost:8080` by default, override
with `-DserverUrl=http://host:port`). There are no tests in `src/test` yet — see "Manual API smoke
test" below for how the HTTP endpoints were last verified end-to-end.

### Manual API smoke test

```
curl -X POST http://localhost:8080/createScenario -H "Content-Type: application/json" -d '{
  "type":"PLANE","amount":5,"originLatRad":0.3575,"originLonRad":0.9838,"sendInterval":500,
  "formation":{"type":"LINE","destLatRad":0.4300,"destLonRad":1.0500,"spacingMeters":2000}
}'
curl http://localhost:8080/getScenarios
curl -X POST http://localhost:8080/start  -H "Content-Type: application/json" -d '{"id":"<id>"}'
curl -X POST http://localhost:8080/pause  -H "Content-Type: application/json" -d '{"id":"<id>"}'
curl -X POST http://localhost:8080/stopAll
curl -X POST http://localhost:8080/deleteScenario -H "Content-Type: application/json" -d '{"id":"<id>"}'
```

## Integrating the real Plane / NetworkApi

`Plane.java` and `NetworkApi.java` are placeholders (see their javadoc). To wire in the real
types from the host system:
- `NetworkApi` just needs a `void send(Plane plane)` method.
- `Plane` just needs the five public fields `altitude`, `latitude`, `longitude`, `vx`, `vy`,
  `heading` (or equivalently-named getters/setters) — `latitude`/`longitude` are radians, `vx`/`vy`
  are m/s in east/north components, `heading` is UI-only degrees derived from velocity, not used
  in kinematics.
- Delete the placeholder files and point imports at the real classes; nothing else in the design
  needs to change.

## Package structure

Code is split into packages along dependency direction — nothing depends "outward," so the graph
is acyclic:

- `planesim.api` — `Plane`, `NetworkApi`: the external contract. Zero dependencies on the rest of
  the codebase, since these mock a library that lives outside this repo (Dependency Inversion:
  everything else depends on this abstraction, never the reverse).
- `planesim.geo` — `Vector2`, `GeoMath`: coordinate/vector math only. Zero dependencies.
- `planesim.behavior` — `FlightBehavior`, `StepResult`, `LineBounceBehavior`,
  `CircleRandomWalkBehavior`: the flight-behavior Strategy pattern. Depends only on `geo`. New
  behaviors can be added here without touching `core` (Open/Closed).
- `planesim.formation` — `FormationSpec`, `LineFormation`, `CircleFormation`: pure declarative
  formation specs (data only, no logic, no dependencies on anything else in the project).
- `planesim.core` — `SimulationConfig`, `SimulatedPlane`, `FormationPlanner`, `SimulationEngine`:
  the orchestration nucleus, i.e. the project's one real purpose (see above). Depends on `api`,
  `geo`, `behavior`, and `formation`. `SimulationEngine` doesn't own a thread — see Architecture
  below.
- `planesim.scenario` — `ScenarioType`, `ScenarioStatus`, `PlaneLiveState`, `ScenarioNetworkApi`,
  `Scenario`, `ScenarioManager`: the multi-scenario domain layer (a thread-safe registry of
  concurrently-running `SimulationEngine`s, one shared thread pool). No HTTP/JSON knowledge.
  Depends on `core`, `api`.
- `planesim.server` (+ `planesim.server.dto`) — `SimulationServerApp`, the `com.sun.net.httpserver`
  wiring/handlers, `RequestMapper`, and the JSON wire-format DTOs. Pure transport; depends on
  `scenario`, `core`, `formation`.
- `planesim.app` — `SimulationApp`: headless console demo. Depends on `core`, `formation`, `api`.
- `planesim.ui` — `PlaneSimulatorUiApp`, `MapPanel`, `PlaneSnapshot`, `ScenarioPollingClient`: a
  view-only Swing dashboard. Depends on `server.dto` (reuses `ScenarioDto`/`PlaneStateDto` as its
  wire format directly rather than inventing a parallel client-side DTO set — still acyclic, since
  nothing in `server`/`scenario`/`core` ever imports `ui`) and `geo`. Notably does **not** depend
  on `core`/`api` anymore — the UI never runs a `SimulationEngine` in-process; it only talks HTTP.

Because Java visibility doesn't reach across packages, a few types that used to be package-private
when everything lived in one package are now `public` purely so `core` can consume them from
`behavior` (`FlightBehavior`, `StepResult`, `LineBounceBehavior`, `CircleRandomWalkBehavior`).
Types only ever constructed by a class in their own package kept their package-private visibility:
`SimulatedPlane` (built by `FormationPlanner`, driven by `SimulationEngine`, both in `core`),
`MapPanel`/`PlaneSnapshot` (only used by `PlaneSimulatorUiApp`, in `ui`), and `ScenarioNetworkApi`
(only used by `ScenarioManager`, in `scenario`).

## Architecture

**Two coordinate frames.** All physics happens in a local flat frame in meters (x = east,
y = north) via `Vector2`, centered on `SimulationConfig.originLatRad/originLonRad`. Lat/lon
(always radians) only exists at the boundary: `GeoMath.toLocal`/`toLatLon` convert on the way in
and out. This is because vx/vy are linear (m/s) and lat/lon are angular — they can't be mixed
directly. The projection is a simple equirectangular ("plate carrée") approximation, fine at
regional/continental scale, not for intercontinental great-circle distances.

**Tick loop, on a shared pool.** `SimulationEngine` does not own a thread: it's handed a
`ScheduledExecutorService` (typically shared across many engines/scenarios) via `create(...)`, and
every `publishIntervalMs` does two full passes over its own formation: (1) send every plane's
*current* state via `networkApi.send()` (logging one Log4j2 INFO line per plane sent), then
(2) advance every plane to its next-tick position/velocity. This "send, then update" order means
what's published is always the position as of that tick, not one tick ahead. `SimulatedPlane` and
the `FlightBehavior` implementations are
deliberately not thread-safe / hold no locks — this is still safe with a shared pool because
`ScheduledThreadPoolExecutor` guarantees one task's successive executions never overlap (a late run
delays the next rather than running concurrently), and different engines never share
`SimulatedPlane` objects (each `create()` builds its own private formation list), so only *one*
engine's tick ever touches a given `SimulatedPlane` at a time, even across different pool threads.

**Lifecycle: `start()`/`pause()`, not `stop()`.** There is no `stop()` — `pause()` cancels the
engine's own scheduled task (via its `ScheduledFuture`) without touching the shared executor or
losing plane state, and `start()` either begins ticking for the first time or resumes a paused
engine from wherever it left off (same `SimulatedPlane` instances, same position/velocity). Both
methods, plus `tick()`, are `synchronized` on the engine to serialize a pause-then-immediately-resume
race (otherwise a new scheduled chain could start while an orphaned in-flight tick from the
cancelled chain is still running). Whoever creates the `ScheduledExecutorService` owns shutting it
down — the engine never does.

**Multiple concurrent scenarios.** `ScenarioManager` (in `planesim.scenario`) is the thread-safe
registry behind the HTTP API: `createScenario` builds a `SimulationEngine` on the manager's shared
pool plus a `ScenarioNetworkApi` (records each plane's latest published state — the same
capture-for-rendering idea `MapPanel` used to get fed with directly in-process, but thread-safe
since HTTP handler threads read it concurrently with the tick thread writing it); `start`/`pause`/
`delete` all key off the scenario's UUID id and return `false` for an unknown id (mapped to HTTP
404). `delete` calls `pause()` before dropping the scenario so its scheduled task doesn't leak on
the shared pool. `stopAll()` is the bulk equivalent of `pause` — it iterates every scenario, pauses
only the ones currently `RUNNING` (skipping `CREATED`/already-`PAUSED` ones), and returns the ids it
actually stopped; there's no separate "true stop" concept beyond pause, so bulk-stopping is always
resumable via `/start` just like a single pause is.

**Behavior strategy per plane.** `SimulatedPlane` holds local-frame `position`/`velocity` plus a
`FlightBehavior` (`step(position, velocity, dtSeconds) -> StepResult`) that owns how that one
plane's motion evolves. Each plane gets its own behavior instance (never shared), so a behavior
can hold private mutable state without synchronization:
- `LineBounceBehavior` — flies straight to a waypoint, and on reaching/overshooting it in one
  tick, snaps onto it, reverses velocity, and swaps start/target so the plane shuttles forever.
- `CircleRandomWalkBehavior` — each tick rotates the velocity vector by a Gaussian-distributed
  turn angle (sigma = 45°, i.e. a 90° turn is 2 sigma), which changes heading while preserving
  speed exactly (rotation doesn't change vector length). Not scaled by `dtSeconds`, so a shorter
  publish interval means visually more erratic turning.

**Formation construction.** `FormationPlanner.buildFormation` dispatches on the sealed
`FormationSpec` (`LineFormation` | `CircleFormation`, pattern-matched via `instanceof`) to build
the initial list of `SimulatedPlane`s with their starting positions, velocities, and behaviors:
- Line: planes are centered on and evenly spaced along the axis perpendicular to the
  source→destination route (index offsets like -2,-1,0,1,2 for n=5); each plane gets its own
  parallel copy of the route (both endpoints shifted by the same perpendicular offset).
- Circle: for n=1 the plane sits at the center facing east (arbitrary, since "outward" is
  undefined at the center); for n>1, planes are spaced `360/n` degrees apart starting due east,
  each initially facing radially outward, then wandering independently via random walk.

**HTTP API.** `SimulationServerApp` wires six `com.sun.net.httpserver` handlers (one per endpoint:
`POST /createScenario`, `GET /getScenarios`, `POST /deleteScenario`, `POST /start`, `POST /pause`,
`POST /stopAll`) over a `ScenarioManager`, on top of one shared `Executors.newScheduledThreadPool(...)` for scenario
ticking, separate from the pool that serves incoming HTTP requests. `AbstractJsonHandler`
centralizes method checking, a Log4j2 INFO log line for every request received (method + URI,
logged unconditionally before dispatch, so it fires even for requests that end up 4xx/5xx), JSON
body (de)serialization (Gson), and mapping exceptions to status codes: `400` for
`BadRequestException`/`IllegalArgumentException`/`NullPointerException`/
`JsonSyntaxException` (this is how `SimulationConfig`/`LineFormation`/`CircleFormation`'s own
compact-constructor validation surfaces as a 400 without `RequestMapper` duplicating those checks),
`404` for an unknown scenario id, `405` for the wrong verb, `500` for anything else. Coordinates in
the JSON API are radians, matching the internal representation exactly — no conversion at this
boundary.

**Swing UI is a disposable, view-only test harness**, not production code. `PlaneSimulatorUiApp`
has no config form and no Start/Stop controls — on launch it just polls `GET /getScenarios` on a
fixed interval via `ScenarioPollingClient` (a small `java.net.http.HttpClient` wrapper) and renders
every scenario's planes together on one `MapPanel`. Because plane data now arrives as parsed JSON
rather than shared in-process `Plane` objects, `MapPanel` keys planes by a `scenarioId + "#" +
planeIndex` string and does an atomic full-snapshot replace each poll (`replaceAll`) rather than
incremental per-plane updates — this also correctly drops planes belonging to a since-deleted
scenario. `MapPanel` still auto-fits its view to whichever planes are currently tracked (using
`GeoMath`'s same local-meter projection so shapes stay geometrically correct) rather than rendering
a real world map, since a single formation is meter/km-scale and would be sub-pixel on any real map
projection. Poll-thread UI mutations are wrapped in `SwingUtilities.invokeLater`.

## Conventions worth preserving

- Log4j2 (`LogManager.getLogger(...)`, console appender only, configured in
  `src/main/resources/log4j2.xml`) is used for **backend logging only** — `planesim.core`
  (plane-sent-to-`NetworkApi` events) and `planesim.server` (HTTP requests received). Nothing in
  `planesim.ui` logs anything; keep it that way, the UI is view-only scaffolding, not a place to
  grow observability.
- Angles in the public data model (`Plane`, `SimulationConfig`, `LineFormation`, `CircleFormation`)
  are radians, all the way out through the JSON HTTP API — there is currently no place in this
  codebase that accepts degrees from a human (the old Swing config form that used to convert
  degrees at its boundary via `Math.toRadians`/`toDegrees` was removed when the UI became
  view-only).
- Package-private (no modifier) visibility is used deliberately for internals only constructed by
  another class in the same package (`SimulatedPlane`, `MapPanel`, `PlaneSnapshot`,
  `ScenarioNetworkApi`) — keep new same-package-only types package-private rather than defaulting
  to `public`. Types that must be constructed from a different package (e.g. `core` building
  `behavior` implementations) have to be `public` — that's a Java visibility constraint, not
  license to make everything public; see "Package structure" above for which types are public out
  of necessity vs. by design.
- Records (`Vector2`, `StepResult`, `PlaneSnapshot`, `SimulationConfig`, `LineFormation`,
  `CircleFormation`, `PlaneLiveState`) are used for immutable internal value types; compact
  constructors validate invariants (e.g. `SimulationConfig`, `LineFormation`, `CircleFormation`
  reject negative/non-positive values). The HTTP API's wire-format types
  (`planesim.server.dto.*`) are the one deliberate exception — plain public classes with public
  fields, not records, since they're unvalidated JSON transfer objects (validation happens once,
  explicitly, in `RequestMapper`) and Gson deserialization of absent/optional JSON fields is
  simplest against plain mutable fields.
