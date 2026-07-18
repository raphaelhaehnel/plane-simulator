package planesim;

/**
 * Flies straight toward {@code legTarget}; on reaching or overshooting it within a single tick,
 * snaps exactly onto the waypoint, reverses velocity, and swaps which end is the target — so the
 * plane shuttles back and forth between its two waypoints forever.
 */
final class LineBounceBehavior implements FlightBehavior {

    private Vector2 legStart;
    private Vector2 legTarget;

    LineBounceBehavior(Vector2 legStart, Vector2 legTarget) {
        this.legStart = legStart;
        this.legTarget = legTarget;
    }

    @Override
    public StepResult step(Vector2 position, Vector2 velocity, double dtSeconds) {
        Vector2 toTarget = legTarget.minus(position);
        double remaining = toTarget.length();
        double stepLength = velocity.length() * dtSeconds;

        if (stepLength >= remaining) {
            Vector2 newVelocity = velocity.negated();
            Vector2 previousStart = legStart;
            legStart = legTarget;
            legTarget = previousStart;
            return new StepResult(legStart, newVelocity);
        }

        Vector2 newPosition = position.plus(velocity.scaled(dtSeconds));
        return new StepResult(newPosition, velocity);
    }
}
