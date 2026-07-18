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
`FlightBehavior`, `FormationPlanner`, `GeoMath`, and `Vector2`. Everything else — `SimulationApp`,
`PlaneSimulatorUiApp`, `MapPanel`, `UiNetworkApi`, `PlaneSnapshot` — exists only to exercise and
visually verify that core loop; it is test/demo scaffolding, not a feature to expand for its own
sake.

Coordinates on the simulated objects (`Plane.latitude`/`longitude`, and everywhere upstream of
them: `SimulationConfig`, `LineFormation`, `CircleFormation`) **must stay WGS84 radians** — that's
the format the real external `Plane` type requires, so it's not a local choice to change. Local
meters (`Vector2`, the flat frame) exist only as an internal computation detail behind
`GeoMath.toLocal`/`toLatLon`; they must never leak into the public/external-facing data (`Plane`,
`SimulationConfig`, `FormationSpec` implementations).

## Build / run

Plain Maven project, Java 17, no external dependencies, no test framework wired up yet.

```
mvn compile
mvn exec:java -Dexec.mainClass=planesim.SimulationApp        # headless console demo
mvn exec:java -Dexec.mainClass=planesim.PlaneSimulatorUiApp  # Swing UI test harness
```

There is no `exec-maven-plugin` declared in `pom.xml`, so either add it first or run the compiled
classes directly (`java -cp target/classes planesim.SimulationApp`).

There are no tests in `src/test` yet.

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

## Architecture

**Two coordinate frames.** All physics happens in a local flat frame in meters (x = east,
y = north) via `Vector2`, centered on `SimulationConfig.originLatRad/originLonRad`. Lat/lon
(always radians) only exists at the boundary: `GeoMath.toLocal`/`toLatLon` convert on the way in
and out. This is because vx/vy are linear (m/s) and lat/lon are angular — they can't be mixed
directly. The projection is a simple equirectangular ("plate carrée") approximation, fine at
regional/continental scale, not for intercontinental great-circle distances.

**Single-threaded tick loop.** `SimulationEngine` owns one `ScheduledExecutorService` and, every
`publishIntervalMs`, does two full passes over the formation: (1) send every plane's *current*
state via `networkApi.send()`, then (2) advance every plane to its next-tick position/velocity.
This "send, then update" order means what's published is always the position as of that tick, not
one tick ahead. Because everything runs from a single scheduler thread, `SimulatedPlane` and the
`FlightBehavior` implementations are deliberately not thread-safe / hold no locks.

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

**Swing UI is a disposable test harness**, not production code. `PlaneSimulatorUiApp` builds a
`SimulationConfig` from form fields and swaps in `UiNetworkApi` (implements `NetworkApi` by
forwarding to `MapPanel` instead of a real network) — nothing else about the simulation setup
differs from the real `NetworkApi` path in `SimulationApp`. `MapPanel` auto-fits its view to
whichever planes are currently tracked (using `GeoMath`'s same local-meter projection so shapes
stay geometrically correct) rather than rendering a real world map, since a single formation is
meter/km-scale and would be sub-pixel on any real map projection.

## Conventions worth preserving

- Angles in the public data model (`Plane`, `SimulationConfig`, `LineFormation`, `CircleFormation`)
  are radians; the UI form fields are the one place degrees are accepted from a human and
  converted at the boundary (`Math.toRadians`/`toDegrees`).
- Package-private (no modifier) visibility is used deliberately for internals that only the
  engine/formation-planner should construct (`SimulatedPlane`, `FlightBehavior` implementations,
  `StepResult`, `MapPanel`, `UiNetworkApi`) — keep new internal-only types package-private rather
  than defaulting to `public`.
- Records (`Vector2`, `StepResult`, `PlaneSnapshot`, `SimulationConfig`, `LineFormation`,
  `CircleFormation`) are used for immutable value types; compact constructors validate invariants
  (e.g. `SimulationConfig`, `LineFormation`, `CircleFormation` reject negative/non-positive values).
