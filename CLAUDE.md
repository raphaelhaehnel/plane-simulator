# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A standalone simulation module that drives simulated objects — some geographic (planes, radars;
placed in a line or circle pattern and, for mobile ones, flown), one non-geographic (weather; no
position at all) — and periodically publishes each object's state to a `NetworkApi`. **The one
distinction that matters architecturally is geographic vs. non-geographic** — not "plane vs. radar
vs. weather" — see "Adding a new object type" below. It's designed to be dropped into a larger
system: `Plane`, `Radar`, `Weather`, and `NetworkApi` are explicitly marked as **placeholders**
standing in for externally-provided classes that don't exist in this repo yet.

This repo will eventually be installed into a different environment where `Plane`, `Radar`,
`Weather`, and `NetworkApi` are imported from an external library instead of defined locally.
Everything in `Plane.java`, `Radar.java`, `Weather.java`, and `NetworkApi.java` here is a **mock**
that exists only so this module compiles and runs standalone — do not add behavior to them beyond
matching the real library's shape (a `toString()` override for readable logging is the one
exception already present; it's diagnostic-only and doesn't affect the field contract), and don't
treat them as the real contract to design around.

The project's one real purpose is: **create simulated objects, evolve them over time (move them,
keep them fixed, or regenerate their values — whatever fits the object), and send their state
through the NetworkApi.** That's `SimulationEngine`, `SimulatedEntity`/`SimulatedObject`/`SimulatedValue`,
`FlightBehavior`/`ValueGenerator`, `FormationPlanner`, `GeoMath`, and `Vector2` — all type-agnostic
over the external object type, see "Adding a new object type" below. On top of that core loop,
`planesim.scenario`/`planesim.server` expose a real, maintained JSON HTTP API
(`SimulationServerApp`) for creating, listing, starting, pausing (individually or all at once via
`/stopAll`), and deleting multiple concurrent simulation *scenarios* — this is a genuine feature,
not scaffolding. `SimulationApp` and the `planesim.ui` package (`PlaneSimulatorUiApp`, `MapPanel`,
`PlaneSnapshot`), by contrast, exist only to exercise and visually verify the simulation/API; they
are test/demo scaffolding, not features to expand for their own sake. In particular the UI is
deliberately **view-only** — it has no code path that can create, start, pause, or delete a
scenario, only `GET /getScenarios` polling — don't add one without being asked. The UI also only
ever renders *geographic* objects (it skips any scenario whose `geoObjects` field is null); it
never gains a way to visualize a non-geographic object like weather — there's nothing to plot.

Coordinates on every *geographic* simulated object (`Plane.latitude`/`longitude`,
`Radar.latitude`/`longitude`, and everywhere upstream of them: `SimulationConfig`, `LineFormation`,
`CircleFormation`) **must stay WGS84 radians** — that's the format the real external types require,
so it's not a local choice to change. Local meters (`Vector2`, the flat frame) exist only as an
internal computation detail behind `GeoMath.toLocal`/`toLatLon`; they must never leak into the
public/external-facing data (`Plane`, `Radar`, `SimulationConfig`, `FormationSpec`
implementations). `Weather` has no coordinates at all — it's a genuinely different, non-geographic
kind of object (see "Adding a new object type" and the Architecture section) and none of the
coordinate-frame conventions apply to it, or to any future non-geographic type.

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
curl -X POST http://localhost:8080/createScenario -H "Content-Type: application/json" -d '{
  "type":"RADAR","amount":3,"originLatRad":0.3575,"originLonRad":0.9838,"sendInterval":1000,
  "formation":{"type":"CIRCLE","radiusMeters":6000}
}'
curl -X POST http://localhost:8080/createScenario -H "Content-Type: application/json" -d '{
  "type":"WEATHER","amount":3,"sendInterval":500
}'
curl http://localhost:8080/getScenarios
curl -X POST http://localhost:8080/start  -H "Content-Type: application/json" -d '{"id":"<id>"}'
curl -X POST http://localhost:8080/pause  -H "Content-Type: application/json" -d '{"id":"<id>"}'
curl -X POST http://localhost:8080/stopAll
curl -X POST http://localhost:8080/deleteScenario -H "Content-Type: application/json" -d '{"id":"<id>"}'
```

## Integrating the real Plane / Radar / Weather / NetworkApi

`Plane.java`, `Radar.java`, `Weather.java`, and `NetworkApi.java` are placeholders (see their
javadoc). To wire in the real types from the host system:
- `NetworkApi` needs one `void send(...)` overload per object type it carries — today
  `send(Plane plane)`, `send(Radar radar)`, `send(Weather weather)`. Add one more overload per
  future object type.
- `Plane` needs the five public fields `altitude`, `latitude`, `longitude`, `vx`, `vy`, `heading`
  (or equivalently-named getters/setters) — `latitude`/`longitude` are radians, `vx`/`vy` are m/s
  in east/north components, `heading` is UI-only degrees derived from velocity, not used in
  kinematics.
- `Radar` needs just `altitude`, `latitude`, `longitude` — no velocity/heading fields, since it
  never moves.
- `Weather` needs just `windVelocity` (double, m/s), `temperature` (float, °C), `isSunny`
  (boolean) — no coordinates at all, since it isn't a positioned object.
- Delete the placeholder files and point imports at the real classes; nothing else in the design
  needs to change — `SimulationEngine<T>` never references `NetworkApi` directly (see below), so it
  doesn't care what the real `NetworkApi`'s full method set looks like beyond the one overload it's
  handed a method reference to.

### Adding a new object type

`planesim.core` is entirely generic over the external object type `T` — this is what let `Radar`
and `Weather` get added without duplicating `SimulationEngine`/`FormationPlanner`. **The only
question that matters is: does the new type have coordinates (geographic) or not
(non-geographic)?** Not "is it like a plane" or "is it like weather" — those are just examples.

**Geographic** (has lat/lon, gets placed by a formation — like `Plane`/`Radar`):
1. Add the placeholder class to `planesim.api` and a `send(...)` overload to `NetworkApi`.
2. Add a predefined `ObjectWriter<YourType>` constant to `planesim.core.ObjectWriters` — the only
   place that knows how to project local-frame state (lat/lon/alt/velocity) onto your type's
   fields.
3. Decide its `MovementStyle`: `MOBILE` reuses the existing per-formation behaviors
   (`LineBounceBehavior`/`CircleRandomWalkBehavior`); `STATIC` reuses `StaticBehavior` regardless of
   formation shape. If neither fits, add a new `FlightBehavior` implementation and wire it into
   `FormationPlanner`'s `movementStyle == ...` branches.
4. Wire it into `ScenarioManager.createScenario`'s `switch` via `SimulationEngine.create(...)`.
5. In `planesim.scenario.ScenarioNetworkApi`, add a `send(YourType obj)` override that records into
   the existing `latestGeoByIndex` map via a new `GeoLiveState` (same shape as plane/radar — just
   lat/lon/heading, nothing new to define).

**Non-geographic** (no coordinates at all — like `Weather`):
1. Add the placeholder class to `planesim.api` and a `send(...)` overload to `NetworkApi`.
2. Add a predefined `ValueGenerator<YourType>` constant to `planesim.core.ValueGenerators` — this
   produces each tick's field values directly (no position/velocity involved at all).
3. Wire it into `ScenarioManager.createScenario`'s `switch` via `SimulationEngine.createValueEngine(...)`.
4. In `planesim.scenario.ScenarioNetworkApi`, add a `send(YourType obj)` override that's a
   **one-line delegation to the existing `recordNonGeo(obj)`** — that's it. No new record, no new
   DTO, no new `RequestMapper` branch. `NonGeoLiveState`/`NonGeoStateDto` are already generic
   (a `Map<String, Object>` of whatever public fields your type has, captured via reflection —
   see `NonGeoFieldReader`), specifically so this step never grows.
5. **Don't** add it to `MapPanel`/`PlaneSimulatorUiApp` — the UI only ever renders geographic
   objects, by design (see "What this is" above).

In both cases, add a `ScenarioType` value if you want it reachable over HTTP — `RequestMapper.
toScenarioType` picks it up automatically via `ScenarioType.valueOf`.

## Package structure

Code is split into packages along dependency direction — nothing depends "outward," so the graph
is acyclic:

- `planesim.api` — `Plane`, `Radar`, `Weather`, `NetworkApi`: the external contract. Zero
  dependencies on the rest of the codebase, since these mock a library that lives outside this repo
  (Dependency Inversion: everything else depends on this abstraction, never the reverse).
- `planesim.geo` — `Vector2`, `GeoMath`: coordinate/vector math only. Zero dependencies. Only used
  by geographic code paths — nothing about `Weather`/`ValueGenerator`/`SimulatedValue` touches this
  package at all.
- `planesim.behavior` — `FlightBehavior`, `StepResult`, `LineBounceBehavior`,
  `CircleRandomWalkBehavior`, `StaticBehavior`: the flight-behavior Strategy pattern, for
  geographic objects only. Depends only on `geo`. New behaviors can be added here without touching
  `core` (Open/Closed).
- `planesim.formation` — `FormationSpec`, `LineFormation`, `CircleFormation`: pure declarative
  formation specs (data only, no logic, no dependencies on anything else in the project) — for
  geographic objects only; non-geographic objects (weather) don't use a formation at all. Shape
  (line/circle) only ever decides initial placement — see `planesim.core.MovementStyle` for what
  happens to an object's position afterward.
- `planesim.core` — the orchestration nucleus, i.e. the project's one real purpose (see above),
  generic over the external object type `T`. Depends on `api` (for `Plane`/`Radar`/`Weather` in
  `ObjectWriters`/`ValueGenerators`), `geo`, `behavior`, and `formation`. Two parallel families:
  - Geographic: `SimulationConfig`, `SimulatedObject<T>`, `ObjectWriter<T>`, `ObjectWriters`,
    `MovementStyle`.
  - Non-geographic: `ValueSimulationConfig`, `SimulatedValue<T>`, `ValueGenerator<T>`,
    `ValueGenerators`.
  - Shared: `ScenarioConfig` (sealed interface both configs implement), `SimulatedEntity<T>`
    (interface both `SimulatedObject`/`SimulatedValue` implement — see Architecture),
    `FormationPlanner` (geographic-only, despite living alongside the shared types),
    `SimulationEngine<T>` (genuinely shared — drives either kind via `SimulatedEntity<T>`, doesn't
    own a thread, doesn't depend on `NetworkApi` — see Architecture below).
- `planesim.scenario` — `ScenarioType` (`PLANE`, `RADAR`, `WEATHER`), `ScenarioStatus`,
  `GeoLiveState` (geographic), `NonGeoLiveState` + `NonGeoFieldReader` (non-geographic, generic —
  see below), `ScenarioNetworkApi`, `Scenario`, `ScenarioManager`: the multi-scenario domain layer
  (a thread-safe registry of concurrently-running `SimulationEngine`s, one shared thread pool). No
  HTTP/JSON knowledge. Depends on `core`, `api`.
- `planesim.server` (+ `planesim.server.dto`) — `SimulationServerApp`, the `com.sun.net.httpserver`
  wiring/handlers, `RequestMapper`, and the JSON wire-format DTOs. `ScenarioDto` carries both a
  `geoObjects: List<GeoStateDto>` (geographic, null for a non-geographic scenario) and a
  `nonGeoObjects: List<NonGeoStateDto>` (non-geographic, null for a geographic scenario) — see
  Architecture. Pure transport; depends on `scenario`, `core`, `formation`.
- `planesim.app` — `SimulationApp`: headless console demo (line/circle planes, a radar example, a
  weather example). Depends on `core`, `formation`, `api`.
- `planesim.ui` — `PlaneSimulatorUiApp`, `MapPanel`, `PlaneSnapshot`, `ScenarioPollingClient`: a
  view-only, **geographic-only** Swing dashboard that renders every tracked geographic object
  (plane, radar, ...) with the same icon — it has no notion of object type, and no notion of
  non-geographic objects at all (it just skips a scenario whose `geoObjects` is null). Depends on
  `server.dto` (reuses `ScenarioDto`/`GeoStateDto` as its wire format directly rather than
  inventing a parallel client-side DTO set — still acyclic, since nothing in
  `server`/`scenario`/`core` ever imports `ui`) and `geo`. Notably does **not** depend on
  `core`/`api` anymore — the UI never runs a `SimulationEngine` in-process; it only talks HTTP.

Because Java visibility doesn't reach across packages, a few types that used to be package-private
when everything lived in one package are now `public` purely so `core` can consume them from
`behavior` (`FlightBehavior`, `StepResult`, `LineBounceBehavior`, `CircleRandomWalkBehavior`,
`StaticBehavior`), or so `scenario`/`app` can consume them from `core` (`SimulationEngine`,
`ScenarioConfig`, `MovementStyle`, `ObjectWriter`, `ObjectWriters`, `ValueSimulationConfig`,
`ValueGenerator`, `ValueGenerators`). Types only ever constructed by a class in their own package
kept their package-private visibility: `SimulatedEntity`/`SimulatedObject`/`SimulatedValue` (built by
`FormationPlanner`/`SimulationEngine`, used by `SimulationEngine`, all in `core`),
`MapPanel`/`PlaneSnapshot` (only used by `PlaneSimulatorUiApp`, in `ui`), and
`ScenarioNetworkApi`/`NonGeoFieldReader` (only used within `scenario`).

## Architecture

**Two coordinate frames** (geographic objects only). All physics happens in a local flat frame in
meters (x = east, y = north) via `Vector2`, centered on `SimulationConfig.originLatRad/originLonRad`.
Lat/lon (always radians) only exists at the boundary: `GeoMath.toLocal`/`toLatLon` convert on the
way in and out. This is because vx/vy are linear (m/s) and lat/lon are angular — they can't be
mixed directly. The projection is a simple equirectangular ("plate carrée") approximation, fine at
regional/continental scale, not for intercontinental great-circle distances. None of this applies
to a non-geographic object (weather) — it has no local-frame state to begin with.

**`SimulatedEntity<T>`: the abstraction that makes the engine object-shape-agnostic.**
`SimulationEngine<T>` only ever depends on `SimulatedEntity<T>` (`writeToExternal() -> T`,
`advance(dtSeconds)`), never on whether an object has a position. Two implementations:
- `SimulatedObject<T>` (geographic) — holds local-frame `position`/`velocity`, a `FlightBehavior`,
  and an `ObjectWriter<T>`; `writeToExternal()` converts `position` to lat/lon via
  `GeoMath.toLatLon` and has the writer populate the external object, `advance()` delegates to the
  behavior.
- `SimulatedValue<T>` (non-geographic) — holds just a `ValueGenerator<T>`; `writeToExternal()`
  calls the generator to regenerate the external object's fields directly (no position involved at
  all); `advance()` is a no-op, since there's no motion to advance.

**Tick loop, on a shared pool.** `SimulationEngine<T>` does not own a thread: it's handed a
`ScheduledExecutorService` (typically shared across many engines/scenarios) via `create(...)`
(geographic) or `createValueEngine(...)` (non-geographic), and every `publishIntervalMs` does two
full passes over its own item list: (1) call `writeToExternal()` on every `SimulatedEntity<T>` and send
the result to a `Consumer<T> sink` (logging one Log4j2 INFO line per object sent —
`external.toString()`, so `Plane`/`Radar`/`Weather` each log their own meaningful fields via their
own `toString()` override), then (2) call `advance()` on every item (a no-op for non-geographic
ones). This "send, then update" order means what's published is always the state as of that tick,
not one tick ahead. `SimulatedObject`/`SimulatedValue` and the `FlightBehavior` implementations are
deliberately not thread-safe / hold no locks — this is still safe with a shared pool because
`ScheduledThreadPoolExecutor` guarantees one task's successive executions never overlap (a late run
delays the next rather than running concurrently), so one engine's own ticks never overlap
regardless of pool size, and different engines never share `SimulatedEntity` instances (each `create*`
call builds its own private item list), so only *one* engine's tick ever touches a given item at a
time, even across different pool threads.

`SimulationEngine<T>` takes a plain `Consumer<T> sink`, not `NetworkApi` directly — callers pass a
method reference like `networkApi::send`, which resolves to whichever `send(...)` overload matches
the `T` that particular engine was created with. This keeps `core` decoupled from `NetworkApi`
entirely (it only needs `planesim.api.Plane`/`Radar`/`Weather` for `ObjectWriters`/`ValueGenerators`,
not the `NetworkApi` interface).

**Lifecycle: `start()`/`pause()`, not `stop()`.** There is no `stop()` — `pause()` cancels the
engine's own scheduled task (via its `ScheduledFuture`) without touching the shared executor or
losing object state, and `start()` either begins ticking for the first time or resumes a paused
engine from wherever it left off. Both methods, plus `tick()`, are `synchronized` on the engine to
serialize a pause-then-immediately-resume race (otherwise a new scheduled chain could start while
an orphaned in-flight tick from the cancelled chain is still running). Whoever creates the
`ScheduledExecutorService` owns shutting it down — the engine never does.

**Multiple concurrent scenarios.** `ScenarioManager` (in `planesim.scenario`) is the thread-safe
registry behind the HTTP API: `createScenario` `switch`es on `ScenarioType` to pick the object
class, the matching `ObjectWriters`/`ValueGenerators` constant, and — for geographic types — the
matching `MovementStyle` (`PLANE` → `MOBILE`, `RADAR` → `STATIC`; `WEATHER` skips `MovementStyle`
entirely and goes through `createValueEngine`), then builds a `SimulationEngine<?>` on the
manager's shared pool plus a `ScenarioNetworkApi`.

**`ScenarioNetworkApi` and the generic non-geographic live-state capture.** It implements every
`NetworkApi` overload (a scenario is always homogeneous, so only one is ever actually exercised per
instance), recording each object's latest published state keyed by object identity — the same
capture-for-rendering idea `MapPanel` used to get fed with directly in-process, but thread-safe
since HTTP handler threads read it concurrently with the tick thread writing it. Geographic and
non-geographic sends are tracked in two separate maps, because their live-state shapes have nothing
in common:
- Geographic (`send(Plane)`/`send(Radar)`/...) → `GeoLiveState(index, latRad, lonRad, headingDeg)`
  — always this same shape, so it's built directly, field by field.
- Non-geographic (`send(Weather)`/...) → all delegate to one private `recordNonGeo(Object target)`
  helper, which builds a `NonGeoLiveState(index, Map<String,Object> fields)` by reading every
  public instance field off `target` via reflection (`NonGeoFieldReader.readFields`). This is the
  key generality move: a non-geographic object's fields are arbitrary and type-specific (weather's
  are `windVelocity`/`temperature`/`isSunny`; a future one could be anything), so instead of a
  hand-written record + DTO + mapping function per type, the *shape itself* is generic — adding a
  new non-geographic type only ever requires a one-line `send(...)` override that calls
  `recordNonGeo`, nothing else in `scenario` or `server.dto` changes.

Continuing `ScenarioManager`: `start`/`pause`/`delete` all key off the scenario's UUID id and return
`false` for an unknown id (mapped to HTTP 404). `delete` calls `pause()` before dropping the
scenario so its scheduled task doesn't leak on the shared pool. `stopAll()` is the bulk equivalent
of `pause` — it iterates every scenario, pauses only the ones currently `RUNNING` (skipping
`CREATED`/already-`PAUSED` ones), and returns the ids it actually stopped; there's no separate
"true stop" concept beyond pause, so bulk-stopping is always resumable via `/start` just like a
single pause is.

**Behavior strategy per geographic object.** `SimulatedObject<T>` holds local-frame
`position`/`velocity` plus a `FlightBehavior` (`step(position, velocity, dtSeconds) -> StepResult`)
that owns how that one object's motion evolves, and an `ObjectWriter<T>` that projects that local
state onto the external object's fields. Each object gets its own behavior instance (never
shared), so a behavior can hold private mutable state without synchronization:
- `LineBounceBehavior` — flies straight to a waypoint, and on reaching/overshooting it in one
  tick, snaps onto it, reverses velocity, and swaps start/target so the object shuttles forever.
- `CircleRandomWalkBehavior` — each tick rotates the velocity vector by a Gaussian-distributed
  turn angle (sigma = 45°, i.e. a 90° turn is 2 sigma), which changes heading while preserving
  speed exactly (rotation doesn't change vector length). Not scaled by `dtSeconds`, so a shorter
  publish interval means visually more erratic turning.
- `StaticBehavior` — every tick returns the exact same position with zero velocity, regardless of
  whatever initial velocity the formation assigned (e.g. a radar). `FormationPlanner` also zeroes
  out the *initial* velocity it hands a static object, so even the very first published tick — before
  `StaticBehavior.step()` has run once — already reports zero velocity, not the formation's raw
  placement velocity.

**Value generation for non-geographic objects.** `SimulatedValue<T>` has no behavior/position
concept at all — it just calls a `ValueGenerator<T>` (`void generate(T target)`) every tick, which
mutates the external object's fields directly. `ValueGenerators.WEATHER` draws fresh random
`windVelocity`/`temperature`/`isSunny` every tick via `ThreadLocalRandom.current()` (not a captured
`Random` field — a generator is invoked repeatedly from whichever pool thread happens to run that
tick, unlike a formation's placement RNG which is drawn once at construction on one thread, so
`ThreadLocalRandom` is the correct/contention-free choice here).

**Formation construction** (geographic objects only). `FormationPlanner.buildFormation` dispatches
on the sealed `FormationSpec` (`LineFormation` | `CircleFormation`, pattern-matched via
`instanceof`) to build the initial list of `SimulatedEntity<T>`s (concretely, `SimulatedObject<T>`s) with
their starting positions, velocities, and behaviors — generic over `T`, so the exact same geometry
code places planes, radars, or any future geographic object type; only the `MovementStyle`
parameter decides which `FlightBehavior` gets attached:
- Line: objects are centered on and evenly spaced along the axis perpendicular to the
  source→destination route (index offsets like -2,-1,0,1,2 for n=5); each object gets its own
  parallel copy of the route (both endpoints shifted by the same perpendicular offset) — relevant
  only to `MOBILE` objects, a `STATIC` object just stays at its point on the line.
- Circle: for n=1 the object sits at the center facing east (arbitrary, since "outward" is
  undefined at the center); for n>1, objects are spaced `360/n` degrees apart starting due east,
  each initially facing radially outward, then — if `MOBILE` — wandering independently via random
  walk; a `STATIC` object just stays put.

Non-geographic objects (weather) skip `FormationPlanner` entirely — `SimulationEngine.createValueEngine`
just builds `config.objectCount()` independent `SimulatedValue`s directly, no placement geometry
involved.

**HTTP API.** `SimulationServerApp` wires six `com.sun.net.httpserver` handlers (one per endpoint:
`POST /createScenario`, `GET /getScenarios`, `POST /deleteScenario`, `POST /start`, `POST /pause`,
`POST /stopAll`) over a `ScenarioManager`, on top of one shared
`Executors.newScheduledThreadPool(...)` for scenario ticking, separate from the pool that serves
incoming HTTP requests. `AbstractJsonHandler` centralizes method checking, a Log4j2 INFO log line
for every request received (method + URI, logged unconditionally before dispatch, so it fires even
for requests that end up 4xx/5xx), JSON body (de)serialization (Gson), and mapping exceptions to
status codes: `400` for `BadRequestException`/`IllegalArgumentException`/`NullPointerException`/
`JsonSyntaxException` (this is how `SimulationConfig`/`ValueSimulationConfig`/`LineFormation`/
`CircleFormation`'s own compact-constructor validation surfaces as a 400 without `RequestMapper`
duplicating those checks), `404` for an unknown scenario id, `405` for the wrong verb, `500` for
anything else. Coordinates in the JSON API are radians, matching the internal representation
exactly — no conversion at this boundary. `createScenario`'s `type` field accepts `"PLANE"`,
`"RADAR"`, or `"WEATHER"` (`RequestMapper.toScenarioType` maps via `ScenarioType.valueOf`, so a new
`ScenarioType` value becomes acceptable automatically); `RequestMapper.toScenarioConfig` then
dispatches on `type` to build either a `SimulationConfig` (PLANE/RADAR — requires
`originLatRad`/`originLonRad`/`formation`) or a `ValueSimulationConfig` (WEATHER — just
`amount`/`sendInterval`, no origin/formation needed or read even if the caller sends them anyway).
`speed`/`altitude` default to 230.0/10000.0 when omitted and are harmless-but-unused for a `RADAR`
scenario (`StaticBehavior` never applies velocity regardless of `SimulationConfig.speedMps`) and
entirely absent from a `WEATHER` scenario's config. In `RequestMapper.toDto`, geographic fields
(`originLatRad`/`originLonRad`/`speed`/`altitude`/`formation`/`geoObjects`) on `ScenarioDto` are
boxed (`Double`) and left `null` — which Gson serializes as an absent field, not `0.0` — for a
`WEATHER` scenario, which populates `nonGeoObjects: List<NonGeoStateDto>` instead (each entry's
`fields` a generic `Map<String,Object>`, see above); a geographic scenario leaves `nonGeoObjects`
null.

**Swing UI is a disposable, view-only, geographic-only test harness**, not production code.
`PlaneSimulatorUiApp` has no config form and no Start/Stop controls — on launch it just polls
`GET /getScenarios` on a fixed interval via `ScenarioPollingClient` (a small `java.net.http.HttpClient`
wrapper) and renders every scenario's geographic objects together on one `MapPanel`, all with the
same icon regardless of type; a scenario with a null `geoObjects` field (e.g. weather) is skipped
entirely in the poll loop — nothing crashes, it just contributes nothing to the map. Because object
data now arrives as parsed JSON rather than shared in-process `Plane`/`Radar` objects, `MapPanel`
keys objects by a `scenarioId + "#" + index` string and does an atomic full-snapshot replace each
poll (`replaceAll`) rather than incremental per-object updates — this also correctly drops objects
belonging to a since-deleted scenario. `MapPanel` still auto-fits its view to whichever objects are
currently tracked (using `GeoMath`'s same local-meter projection so shapes stay geometrically
correct) rather than rendering a real world map, since a single formation is meter/km-scale and
would be sub-pixel on any real map projection. Poll-thread UI mutations are wrapped in
`SwingUtilities.invokeLater`.

## Conventions worth preserving

- Log4j2 (`LogManager.getLogger(...)`, console appender only, configured in
  `src/main/resources/log4j2.xml`) is used for **backend logging only** — `planesim.core`
  (object-sent-to-`NetworkApi` events) and `planesim.server` (HTTP requests received). Nothing in
  `planesim.ui` logs anything; keep it that way, the UI is view-only scaffolding, not a place to
  grow observability.
- Angles in the public data model (`Plane`, `Radar`, `SimulationConfig`, `LineFormation`,
  `CircleFormation`) are radians, all the way out through the JSON HTTP API — there is currently no
  place in this codebase that accepts degrees from a human (the old Swing config form that used to
  convert degrees at its boundary via `Math.toRadians`/`toDegrees` was removed when the UI became
  view-only). `Weather` has no angles/coordinates to begin with, so this convention simply doesn't
  apply to it, or to any future non-geographic type.
- Package-private (no modifier) visibility is used deliberately for internals only constructed by
  another class in the same package (`SimulatedEntity`, `SimulatedObject`, `SimulatedValue`, `MapPanel`,
  `PlaneSnapshot`, `ScenarioNetworkApi`, `NonGeoFieldReader`) — keep new same-package-only types
  package-private rather than defaulting to `public`. Types that must be constructed from a
  different package (e.g. `core` building `behavior` implementations, or `scenario` building
  `core` engines) have to be `public` — that's a Java visibility constraint, not license to make
  everything public; see "Package structure" above for which types are public out of necessity vs.
  by design.
- Records (`Vector2`, `StepResult`, `PlaneSnapshot`, `SimulationConfig`, `ValueSimulationConfig`,
  `LineFormation`, `CircleFormation`, `GeoLiveState`, `NonGeoLiveState`) are used for immutable
  internal value types; compact constructors validate invariants (e.g. `SimulationConfig`,
  `ValueSimulationConfig`, `LineFormation`, `CircleFormation` reject negative/non-positive values;
  `NonGeoLiveState` wraps its field map unmodifiable). The HTTP API's wire-format types
  (`planesim.server.dto.*`) are the one deliberate exception — plain public classes with public
  fields, not records, since they're unvalidated JSON transfer objects (validation happens once,
  explicitly, in `RequestMapper`) and Gson deserialization of absent/optional JSON fields is
  simplest against plain mutable fields.
- `planesim.core` is generic over the external object type (`SimulationEngine<T>`, `SimulatedEntity<T>`,
  `ObjectWriter<T>`, `ValueGenerator<T>`) specifically so a new object type never requires
  duplicating the engine — see "Adding a new object type" above. Don't special-case `Plane`,
  `Radar`, or `Weather` inside `core`; that logic belongs in `ObjectWriters`/`ValueGenerators`
  (field mapping/generation) and callers picking a `MovementStyle` or geo-vs-non-geo engine factory
  method (movement), not scattered `instanceof`/type checks.
- The geographic/non-geographic live-state and DTO types (`GeoLiveState`/`NonGeoLiveState`,
  `GeoStateDto`/`NonGeoStateDto`) are deliberately named after that one distinction, not after any
  specific object (there is no `WeatherLiveState` or `PlaneStateDto`) — `NonGeoLiveState` in
  particular is a generic `Map<String,Object>` field bag (via reflection, `NonGeoFieldReader`)
  specifically so adding a *second* non-geographic type never requires a new record/DTO/mapping
  function. Keep it that way: don't add object-specific fields to these types, and don't name a new
  live-state/DTO type after the object it happens to be introduced for.
- Not every object is geographic — don't assume a `ScenarioConfig`/external object has lat/lon/a
  formation. Check with `instanceof SimulationConfig` (see `RequestMapper.toDto`) rather than
  casting unconditionally, and never add coordinate fields to `Weather` or a future non-geographic
  type just for consistency with `Plane`/`Radar`.
