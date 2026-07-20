# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A standalone simulation module that drives simulated objects — some geographic (planes, radars;
placed in a line or circle pattern and, for mobile ones, flown), one non-geographic (weather; no
position at all) — and periodically publishes each object's state to a `NetworkApi`. **The one
distinction that matters architecturally is geographic vs. non-geographic** — not "plane vs. radar
vs. weather" — see "Adding a new object type" below.

The three top-level packages map directly to what happens on integration into the real host
system:
- **`planesim.core`** — all the actual simulation logic **and** the JSON HTTP API
  (`SimulationServerApp` and everything under it). **This is what stays in the real environment**
  — the host system runs this HTTP API and drives it from its own existing UI/orchestration
  system, instead of the disposable Swing UI in this repo. Nothing in it depends on `view`.
- **`planesim.external`** — placeholders/mocks for the externally-provided classes (`Plane`,
  `Radar`, `Weather`, `NetworkApi`) that don't exist in this repo yet. In the real environment
  these get deleted and replaced by the host system's real imports — `core` only depends on their
  *shape* (field names/types), never on this package's actual files existing.
- **`planesim.view`** — the Swing UI and the HTTP calls it makes to reach the API in `core`. **Not
  needed in the real environment**, since the host system already has its own UI hitting the API
  directly; this package exists purely to exercise and visually verify `core`'s HTTP API
  standalone, without needing a real consumer wired up yet.

This repo will eventually be installed into a different environment where `Plane`, `Radar`,
`Weather`, and `NetworkApi` are imported from an external library instead of defined locally.
Everything in `planesim.external` is a **mock** that exists only so this module compiles and runs
standalone — do not add behavior to them beyond matching the real library's shape (a `toString()`
override for readable logging is the one exception already present; it's diagnostic-only and
doesn't affect the field contract), and don't treat them as the real contract to design around.

The project's one real purpose is: **create simulated objects, evolve them over time (move them,
keep them fixed, or regenerate their values — whatever fits the object), and send their state
through the NetworkApi.** That's `planesim.core.engine` (`SimulationEngine`,
`SimulatedEntity`/`SimulatedObject`/`SimulatedValue`, `FormationPlanner`), `planesim.core.behavior`
(`FlightBehavior`/implementations), `planesim.core.geo` (`GeoMath`/`Vector2`), and
`planesim.core.scenario` (multi-scenario orchestration) — all type-agnostic over the external
object type, see "Adding a new object type" below. `planesim.core.server` exposes a real,
maintained JSON HTTP API (`SimulationServerApp`) over `planesim.core.scenario` for creating,
listing, starting, pausing (individually or all at once via `/stopAll`), and deleting multiple
concurrent simulation *scenarios* — this is production code, part of what ships to the real
environment, not scaffolding. `planesim.view.app` (`SimulationApp`) and `planesim.view.ui`
(`PlaneSimulatorUiApp`, `MapPanel`, `PlaneSnapshot`, `ScenarioPollingClient`), by contrast, exist
only to exercise and visually verify the simulation/API; they are test/demo scaffolding, not
features to expand for their own sake. In particular the UI is deliberately **view-only** — it has
no code path that can create, start, pause, or delete a scenario, only `GET /getScenarios` polling
— don't add one without being asked. The UI also only ever renders *geographic* objects (it skips
any scenario whose `geoObjects` field is null); it never gains a way to visualize a non-geographic
object like weather — there's nothing to plot.

Coordinates on every *geographic* simulated object (`Plane.latitude`/`longitude`,
`Radar.latitude`/`longitude`, and everywhere upstream of them: `GeoScenarioConfig`, `LineFormation`,
`CircleFormation`) **must stay WGS84 radians** — that's the format the real external types require,
so it's not a local choice to change. Local meters (`Vector2`, the flat frame) exist only as an
internal computation detail behind `GeoMath.toLocal`/`toLatLon`; they must never leak into the
public/external-facing data (`Plane`, `Radar`, `GeoScenarioConfig`, `FormationSpec`
implementations). `Weather` has no coordinates at all — it's a genuinely different, non-geographic
kind of object (see "Adding a new object type" and the Architecture section) and none of the
coordinate-frame conventions apply to it, or to any future non-geographic type. Because the
projection is a flat equirectangular approximation, `GeoScenarioConfig`'s compact constructor also
rejects an `originLatRad` too close to a pole, where `Math.cos(originLatRad)` would blow the
projection up toward infinity.

## Build / run

Maven project, Java 17, dependencies are Gson (JSON HTTP API) and Log4j2 (console logging),
`exec-maven-plugin` wired up, no test framework yet.

```
mvn compile
mvn exec:java -Dexec.mainClass=planesim.core.server.SimulationServerApp # HTTP API, default port 8080
mvn exec:java -Dexec.mainClass=planesim.view.app.SimulationApp          # headless console demo
mvn exec:java -Dexec.mainClass=planesim.view.ui.PlaneSimulatorUiApp     # view-only Swing dashboard, polls the API
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

`planesim.external.{Plane,Radar,Weather,NetworkApi}` are placeholders (see their javadoc). To wire
in the real types from the host system:
- Delete the whole `planesim.external` package and `planesim.view` package; only `planesim.core`
  (and its subpackages, including `core.server` — the HTTP API is production code, it ships too)
  is needed in the real environment.
- Point `planesim.core`'s imports of `Plane`/`Radar`/`Weather`/`NetworkApi` at the host system's
  real classes instead (they're only referenced from `planesim.core.engine.ObjectWriters`/
  `ValueGenerators` and `planesim.core.scenario.ScenarioNetworkApi`/`ScenarioManager` — nothing in
  `core.server` references them directly, it only ever deals with `core.scenario`'s
  already-abstracted `ScenarioConfig`/`Scenario` types).
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
- Nothing else in the design needs to change — `SimulationEngine<T>` never references `NetworkApi`
  directly (see Architecture below), so it doesn't care what the real `NetworkApi`'s full method
  set looks like beyond the one overload it's handed a method reference to.

### Adding a new object type

`planesim.core` is entirely generic over the external object type `T` — this is what let `Radar`
and `Weather` get added without duplicating `SimulationEngine`/`FormationPlanner`. **The only
question that matters is: does the new type have coordinates (geographic) or not
(non-geographic)?** Not "is it like a plane" or "is it like weather" — those are just examples.

**Geographic** (has lat/lon, gets placed by a formation — like `Plane`/`Radar`):
1. Add the placeholder class to `planesim.external` and a `send(...)` overload to `NetworkApi`.
2. Add a predefined `ObjectWriter<YourType>` constant to `planesim.core.engine.ObjectWriters` —
   the only place that knows how to project local-frame state (lat/lon/alt/velocity) onto your
   type's fields.
3. Decide its `MovementStyle`: `MOBILE` reuses the existing per-formation behaviors
   (`LineBounceBehavior`/`CircleRandomWalkBehavior`, in `planesim.core.behavior`); `STATIC` reuses
   `StaticBehavior` regardless of formation shape. If neither fits, add a new `FlightBehavior`
   implementation and wire it into `planesim.core.engine.FormationPlanner`'s
   `movementStyle == ...` branches.
4. Add `ScenarioType.YOUR_TYPE(ScenarioCategory.GEOGRAPHIC)` and one constant to
   `planesim.core.scenario.ScenarioEngineFactories` (+ its `DEFAULTS` map) that calls
   `SimulationEngine.create(...)` with your `ObjectWriter` and `MovementStyle`.
   `ScenarioManager` itself never needs to change.
5. In `planesim.core.scenario.ScenarioNetworkApi`, add a `send(YourType obj)` override that
   records into the existing `latestGeoByIndex` map via a new `GeoLiveState` (same shape as
   plane/radar — just lat/lon/heading, nothing new to define).

**Non-geographic** (no coordinates at all — like `Weather`):
1. Add the placeholder class to `planesim.external` and a `send(...)` overload to `NetworkApi`.
2. Add a predefined `ValueGenerator<YourType>` constant to `planesim.core.engine.ValueGenerators`
   — this produces each tick's field values directly (no position/velocity involved at all).
3. Add `ScenarioType.YOUR_TYPE(ScenarioCategory.NON_GEOGRAPHIC)` and one constant to
   `planesim.core.scenario.ScenarioEngineFactories` (+ its `DEFAULTS` map) that calls
   `SimulationEngine.createValueEngine(...)`. `ScenarioManager` itself never needs to change.
4. In `ScenarioNetworkApi`, add a `send(YourType obj)` override that's a **one-line delegation to
   the existing `recordNonGeo(obj)`** — that's it. No new record, no new DTO, no new
   `RequestMapper` branch. `NonGeoLiveState`/`NonGeoStateDto` are already generic (a
   `Map<String, Object>` of whatever public fields your type has, captured via reflection — see
   `NonGeoFieldReader`), specifically so this step never grows.
5. **Don't** add it to `planesim.view.ui` — the UI only ever renders geographic objects, by design
   (see "What this is" above).

In both cases, `ScenarioType`'s `category()` is what `RequestMapper.toScenarioConfig` dispatches
on to decide between building a `GeoScenarioConfig` or `NonGeoScenarioConfig`, so once the category
is set correctly the new type is reachable over HTTP automatically — no `RequestMapper` change
needed either.

## Package structure

```
planesim.core                    all logic + the HTTP API — what ships to the real environment
  planesim.core.engine             SimulationEngine, SimulatedObject/SimulatedValue/SimulatedEntity,
                                    ObjectWriter(s), ValueGenerator(s), MovementStyle, FormationPlanner,
                                    GeoScenarioConfig, NonGeoScenarioConfig, ScenarioConfig
  planesim.core.behavior           FlightBehavior + LineBounceBehavior/CircleRandomWalkBehavior/StaticBehavior
  planesim.core.formation          FormationSpec, LineFormation, CircleFormation
  planesim.core.geo                Vector2, GeoMath
  planesim.core.scenario           ScenarioType/Status/Category, GeoLiveState, NonGeoLiveState(+FieldReader),
                                    ScenarioEngineFactory(ies), ScenarioLimitExceededException,
                                    ScenarioNetworkApi, Scenario, ScenarioManager
  planesim.core.server              SimulationServerApp, com.sun.net.httpserver handlers, RequestMapper
  planesim.core.server.dto          JSON wire-format DTOs

planesim.external                 Plane, Radar, Weather, NetworkApi — mocks, deleted on real integration

planesim.view                     Swing UI + the calls it makes to reach the API — not needed in the real environment
  planesim.view.ui                  PlaneSimulatorUiApp, MapPanel, PlaneSnapshot, ScenarioPollingClient
  planesim.view.app                 SimulationApp (headless console demo)
```

Dependency direction is one-way and acyclic: `planesim.external` has zero dependencies on the rest
of the codebase (Dependency Inversion — everything else depends on this abstraction, never the
reverse). Within `planesim.core`: `geo` and `formation` have zero internal dependencies;
`behavior` depends only on `geo`; `engine` depends on `external` (for `Plane`/`Radar`/`Weather` in
`ObjectWriters`/`ValueGenerators`), `geo`, `behavior`, and `formation`; `scenario` depends on
`engine` and `external`; `server` (+ `server.dto`) depends on `scenario`, `engine`, `formation`,
and `external` — all still within `core`, still nothing here ever depends on `view`.
`planesim.view.ui` depends on `core.server.dto` (reuses `ScenarioDto`/`GeoStateDto` as its wire
format directly rather than inventing a parallel client-side DTO set) and `core.geo` — notably
**not** on `core.engine`/`core.server`/`external` at all, since the UI never runs a
`SimulationEngine` in-process or calls `core.server`'s Java API directly, it only talks HTTP to
whatever's listening on the configured URL; `planesim.view.app` depends on `core.engine`,
`core.formation`, and `external`.

Within each subpackage, the same visibility convention holds as before the reorganization: because
Java visibility doesn't reach across packages, a few types are `public` purely so a sibling
subpackage can consume them (`FlightBehavior`, `StepResult`, `LineBounceBehavior`,
`CircleRandomWalkBehavior`, `StaticBehavior` in `core.behavior`; `SimulationEngine`,
`ScenarioConfig`, `MovementStyle`, `ObjectWriter`, `ObjectWriters`, `NonGeoScenarioConfig`,
`ValueGenerator`, `ValueGenerators` in `core.engine`). Types only ever constructed by a class in
their own package kept their package-private visibility: `SimulatedEntity`/`SimulatedObject`/
`SimulatedValue` (all in `core.engine`), `MapPanel`/`PlaneSnapshot` (in `view.ui`), and
`ScenarioNetworkApi`/`NonGeoFieldReader` (in `core.scenario`).

## Architecture

**Two coordinate frames** (geographic objects only). All physics happens in a local flat frame in
meters (x = east, y = north) via `Vector2`, centered on `GeoScenarioConfig.originLatRad/originLonRad`.
Lat/lon (always radians) only exists at the boundary: `GeoMath.toLocal`/`toLatLon` convert on the
way in and out. This is because vx/vy are linear (m/s) and lat/lon are angular — they can't be
mixed directly. The projection is a simple equirectangular ("plate carrée") approximation, fine at
regional/continental scale, not for intercontinental great-circle distances. None of this applies
to a non-geographic object (weather) — it has no local-frame state to begin with.

**`SimulatedEntity<T>`: the abstraction that makes the engine object-shape-agnostic.**
`SimulationEngine<T>` (in `core.engine`) only ever depends on `SimulatedEntity<T>`
(`writeToExternal() -> T`, `advance(dtSeconds)`), never on whether an object has a position. Two
implementations:
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
full passes over its own item list: (1) call `writeToExternal()` on every `SimulatedEntity<T>` and
send the result to a `Consumer<T> sink` (logging one Log4j2 INFO line per object sent —
`external.toString()`, so `Plane`/`Radar`/`Weather` each log their own meaningful fields via their
own `toString()` override), then (2) call `advance()` on every item (a no-op for non-geographic
ones). This "send, then update" order means what's published is always the state as of that tick,
not one tick ahead. `SimulatedObject`/`SimulatedValue` and the `FlightBehavior` implementations are
deliberately not thread-safe / hold no locks — this is still safe with a shared pool because
`ScheduledThreadPoolExecutor` guarantees one task's successive executions never overlap (a late run
delays the next rather than running concurrently), so one engine's own ticks never overlap
regardless of pool size, and different engines never share `SimulatedEntity` instances (each
`create*` call builds its own private item list), so only *one* engine's tick ever touches a given
item at a time, even across different pool threads.

`SimulationEngine<T>` takes a plain `Consumer<T> sink`, not `NetworkApi` directly — callers pass a
method reference like `networkApi::send`, which resolves to whichever `send(...)` overload matches
the `T` that particular engine was created with. This keeps `core.engine` decoupled from
`NetworkApi` entirely (it only needs `planesim.external.{Plane,Radar,Weather}` for
`ObjectWriters`/`ValueGenerators`, not the `NetworkApi` interface).

**Lifecycle: `start()`/`pause()`, not `stop()`.** There is no `stop()` — `pause()` cancels the
engine's own scheduled task (via its `ScheduledFuture`) without touching the shared executor or
losing object state, and `start()` either begins ticking for the first time or resumes a paused
engine from wherever it left off. Both methods, plus `tick()`, are `synchronized` on the engine to
serialize a pause-then-immediately-resume race (otherwise a new scheduled chain could start while
an orphaned in-flight tick from the cancelled chain is still running). Whoever creates the
`ScheduledExecutorService` owns shutting it down — the engine never does.

**Multiple concurrent scenarios.** `ScenarioManager` (in `core.scenario`) is the thread-safe
registry behind the HTTP API. It never references `Plane`/`Radar`/`Weather` or any other concrete
object type directly — it's handed a `Map<ScenarioType, ScenarioEngineFactory>` at construction
(`ScenarioEngineFactories.DEFAULTS`, built in `SimulationServerApp.main()`) and validates every
`ScenarioType` has a registered factory up front (fails fast at startup, not on the first request
for a type). `createScenario` looks up the matching `ScenarioEngineFactory` and calls its
`createEngine(config, networkApi, scheduler)`, which is where the object-class/`ObjectWriters`
`ValueGenerators`/`MovementStyle` choice actually lives (one factory constant per `ScenarioType` in
`ScenarioEngineFactories`, e.g. `PLANE` → `MovementStyle.MOBILE` + `ObjectWriters.PLANE`, `RADAR` →
`MovementStyle.STATIC` + `ObjectWriters.RADAR`, `WEATHER` → `createValueEngine` +
`ValueGenerators.WEATHER`) — adding a future `ScenarioType` means adding one factory constant here,
never touching `ScenarioManager` (see "Adding a new object type" above). `createScenario` also
enforces `MAX_SCENARIOS` (100), throwing `ScenarioLimitExceededException` (mapped to HTTP 429 by
`AbstractJsonHandler`) once reached — a soft, approximate cap (the check-then-put isn't atomic
under concurrent creates), meant only to stop unbounded growth, not to be a precise quota.

**`ScenarioNetworkApi` and the generic non-geographic live-state capture.** It implements every
`NetworkApi` overload (a scenario is always homogeneous, so only one is ever actually exercised per
instance), recording each object's latest published state keyed by object identity, so
`core.server`'s `GET /getScenarios` handler has something to serve — thread-safe since HTTP
handler threads read it concurrently with the tick thread writing it. Geographic and non-geographic
sends are tracked in two separate maps, because their live-state shapes have nothing in common:
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
`instanceof`) to build the initial list of `SimulatedEntity<T>`s (concretely, `SimulatedObject<T>`s)
with their starting positions, velocities, and behaviors — generic over `T`, so the exact same
geometry code places planes, radars, or any future geographic object type; only the
`MovementStyle` parameter decides which `FlightBehavior` gets attached:
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

**HTTP API.** `SimulationServerApp` (in `core.server`) wires six `com.sun.net.httpserver` handlers
(one per endpoint: `POST /createScenario`, `GET /getScenarios`, `POST /deleteScenario`, `POST
/start`, `POST /pause`, `POST /stopAll`) over a `ScenarioManager`, on top of one shared
bounded `Executors.newFixedThreadPool(HTTP_HANDLER_THREADS)` (64 threads — bounded deliberately, so
a burst of requests can't grow the request-handling pool without limit) that serves incoming HTTP
requests, separate from the scheduled pool used for scenario ticking. `AbstractJsonHandler`
centralizes method checking, a Log4j2 INFO log line for every request received (method + URI,
logged unconditionally before dispatch, so it fires even for requests that end up 4xx/5xx), JSON
body (de)serialization (Gson), and mapping exceptions to status codes: `400` for
`BadRequestException`/`IllegalArgumentException`/`NullPointerException`/`JsonSyntaxException` (this
is how `GeoScenarioConfig`/`NonGeoScenarioConfig`/`LineFormation`/`CircleFormation`'s own
compact-constructor validation surfaces as a 400 without `RequestMapper` duplicating those checks),
`429` for `ScenarioLimitExceededException` (the request itself is valid, the server is just out of
scenario capacity), `404` for an unknown scenario id, `405` for the wrong verb, `500` for anything
else (logged via Log4j2's `log.error`, not a raw stack trace). Coordinates in the JSON API are
radians, matching the internal representation exactly — no conversion at this boundary.
`createScenario`'s `type` field accepts `"PLANE"`, `"RADAR"`, or `"WEATHER"` (`RequestMapper.
toScenarioType` maps via `ScenarioType.valueOf`, so a new `ScenarioType` value becomes acceptable
automatically); `RequestMapper.toScenarioConfig` then dispatches on `type.category()` to build
either a `GeoScenarioConfig` (`GEOGRAPHIC` — requires `originLatRad`/`originLonRad`/`formation`,
and rejects an `amount` above `ScenarioConfig.MAX_OBJECT_COUNT` or an `originLatRad` too close to a
pole) or a `NonGeoScenarioConfig` (`NON_GEOGRAPHIC` — just `amount`/`sendInterval`, no
origin/formation needed or read even if the caller sends them anyway). `speed`/`altitude` default
to 230.0/10000.0 when omitted and are harmless-but-unused for a `RADAR` scenario (`StaticBehavior`
never applies velocity regardless of `GeoScenarioConfig.speedMps`) and
entirely absent from a `WEATHER` scenario's config. In `RequestMapper.toDto`, geographic fields
(`originLatRad`/`originLonRad`/`speed`/`altitude`/`formation`/`geoObjects`) on `ScenarioDto` are
boxed (`Double`) and left `null` — which Gson serializes as an absent field, not `0.0` — for a
`WEATHER` scenario, which populates `nonGeoObjects: List<NonGeoStateDto>` instead (each entry's
`fields` a generic `Map<String,Object>`, see above); a geographic scenario leaves `nonGeoObjects`
null.

**Swing UI is a disposable, view-only, geographic-only test harness**, not production code.
`PlaneSimulatorUiApp` (in `view.ui`) has no config form and no Start/Stop controls — on launch it
just polls `GET /getScenarios` on a fixed interval via `ScenarioPollingClient` (a small
`java.net.http.HttpClient` wrapper — the "calls to the API" half of `planesim.view`) and renders
every scenario's geographic objects together on one `MapPanel`, all with the same icon regardless
of type; a scenario with a null `geoObjects` field (e.g. weather) is skipped entirely in the poll
loop — nothing crashes, it just contributes nothing to the map. Because object data now arrives as
parsed JSON rather than shared in-process `Plane`/`Radar` objects, `MapPanel` keys objects by a
`scenarioId + "#" + index` string and does an atomic full-snapshot replace each poll
(`replaceAll`) rather than incremental per-object updates — this also correctly drops objects
belonging to a since-deleted scenario. `MapPanel` still auto-fits its view to whichever objects are
currently tracked (using `GeoMath`'s same local-meter projection so shapes stay geometrically
correct) rather than rendering a real world map, since a single formation is meter/km-scale and
would be sub-pixel on any real map projection. Poll-thread UI mutations are wrapped in
`SwingUtilities.invokeLater`.

## Conventions worth preserving

- Log4j2 (`LogManager.getLogger(...)`, console appender only, configured in
  `src/main/resources/log4j2.xml`) is used for **backend logging only** — `planesim.core.engine`
  (object-sent-to-`NetworkApi` events at INFO, uncaught tick exceptions at ERROR) and
  `planesim.core.server` (HTTP requests received at INFO, unexpected 500s at ERROR). Nothing in
  `planesim.view.ui` logs anything; keep it that way, the UI is view-only scaffolding, not a place
  to grow observability. Don't reintroduce `e.printStackTrace()` for error paths — use the class's
  `Logger` so errors go through the same configured appender/format as everything else.
- Angles in the public data model (`Plane`, `Radar`, `GeoScenarioConfig`, `LineFormation`,
  `CircleFormation`) are radians, all the way out through the JSON HTTP API — there is currently no
  place in this codebase that accepts degrees from a human (the old Swing config form that used to
  convert degrees at its boundary via `Math.toRadians`/`toDegrees` was removed when the UI became
  view-only). `Weather` has no angles/coordinates to begin with, so this convention simply doesn't
  apply to it, or to any future non-geographic type.
- Package-private (no modifier) visibility is used deliberately for internals only constructed by
  another class in the same package (`SimulatedEntity`, `SimulatedObject`, `SimulatedValue`,
  `MapPanel`, `PlaneSnapshot`, `ScenarioNetworkApi`, `NonGeoFieldReader`) — keep new
  same-package-only types package-private rather than defaulting to `public`. Types that must be
  constructed from a different (sub)package (e.g. `core.engine` building `core.behavior`
  implementations, or `core.scenario` building `core.engine` engines) have to be `public` — that's
  a Java visibility constraint, not license to make everything public; see "Package structure"
  above for which types are public out of necessity vs. by design.
- Records (`Vector2`, `StepResult`, `PlaneSnapshot`, `GeoScenarioConfig`, `NonGeoScenarioConfig`,
  `LineFormation`, `CircleFormation`, `GeoLiveState`, `NonGeoLiveState`) are used for immutable
  internal value types; compact constructors validate invariants (e.g. `GeoScenarioConfig`,
  `NonGeoScenarioConfig`, `LineFormation`, `CircleFormation` reject negative/non-positive values and
  an `objectCount` above `ScenarioConfig.MAX_OBJECT_COUNT`; `GeoScenarioConfig` additionally rejects
  a near-pole `originLatRad`; `NonGeoLiveState` wraps its field map unmodifiable). The HTTP API's wire-format types
  (`planesim.core.server.dto.*`) are the one deliberate exception — plain public classes with
  public fields, not records, since they're unvalidated JSON transfer objects (validation happens
  once, explicitly, in `RequestMapper`) and Gson deserialization of absent/optional JSON fields is
  simplest against plain mutable fields.
- `planesim.core.engine` is generic over the external object type (`SimulationEngine<T>`,
  `SimulatedEntity<T>`, `ObjectWriter<T>`, `ValueGenerator<T>`) specifically so a new object type
  never requires duplicating the engine — see "Adding a new object type" above. Don't special-case
  `Plane`, `Radar`, or `Weather` inside `core`; that logic belongs in
  `ObjectWriters`/`ValueGenerators` (field mapping/generation) and callers picking a
  `MovementStyle` or geo-vs-non-geo engine factory method (movement), not scattered
  `instanceof`/type checks.
- The geographic/non-geographic live-state and DTO types (`GeoLiveState`/`NonGeoLiveState`,
  `GeoStateDto`/`NonGeoStateDto`) are deliberately named after that one distinction, not after any
  specific object (there is no `WeatherLiveState` or `PlaneStateDto`) — `NonGeoLiveState` in
  particular is a generic `Map<String,Object>` field bag (via reflection, `NonGeoFieldReader`)
  specifically so adding a *second* non-geographic type never requires a new record/DTO/mapping
  function. Keep it that way: don't add object-specific fields to these types, and don't name a new
  live-state/DTO type after the object it happens to be introduced for.
- Not every object is geographic — don't assume a `ScenarioConfig`/external object has lat/lon/a
  formation. Check with `instanceof GeoScenarioConfig` (see `RequestMapper.toDto`) rather than
  casting unconditionally, and never add coordinate fields to `Weather` or a future non-geographic
  type just for consistency with `Plane`/`Radar`.
- **Never let `planesim.core` depend on `planesim.view`.** The whole point of the package split is
  that `core` (including `core.server`, the HTTP API) is exactly what gets lifted into the real
  environment; any accidental `core -> view` dependency would make that impossible. `core ->
  external` is fine and expected (that's the mocked contract `core` is written against); `view ->
  core` and `view -> external` are both fine (the disposable demo/UI layer legitimately needs the
  real logic and the mock types to exercise it) — but note `view.ui` specifically only ever reaches
  `core` over HTTP (via `core.server.dto`), never by calling `core.engine`/`core.server` Java code
  directly, since that's the whole point of it being a *client* of the API rather than embedding
  the engine.
