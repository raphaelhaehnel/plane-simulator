package planesim;

/**
 * Strategy for how a single plane's position/velocity evolve from one tick to the next.
 * Implementations may hold their own per-plane mutable state (e.g. current waypoint, RNG) —
 * each {@link SimulatedPlane} owns its own instance, never shared, so no synchronization is
 * needed.
 */
interface FlightBehavior {
    StepResult step(Vector2 position, Vector2 velocity, double dtSeconds);
}
