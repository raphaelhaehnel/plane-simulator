package planesim.behavior;

import planesim.geo.Vector2;

/**
 * Never moves: every tick returns the exact same position it started at, with zero velocity,
 * regardless of whatever initial velocity the formation assigned. Used for objects that can't fly
 * (e.g. a ground-based radar) but still want a formation's placement pattern (line/circle spacing)
 * for their fixed positions.
 */
public final class StaticBehavior implements FlightBehavior {

    @Override
    public StepResult step(Vector2 position, Vector2 velocity, double dtSeconds) {
        return new StepResult(position, Vector2.ZERO);
    }
}
