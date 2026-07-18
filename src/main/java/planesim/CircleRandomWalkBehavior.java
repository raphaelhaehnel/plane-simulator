package planesim;

import java.util.Random;

/**
 * Correlated random walk: each tick, the current direction is perturbed by a Gaussian-distributed
 * turn angle centered on 0 (i.e. centered on "keep going the way you're already going"), with a
 * standard deviation such that a full perpendicular turn (90 degrees) sits at exactly 2 standard
 * deviations — so 2*sigma = 90 degrees, sigma = 45 degrees.
 *
 * <p>The turn is applied by rotating the velocity vector directly, which both picks the new
 * direction and preserves speed (rotation never changes vector length), matching "the new
 * velocity will be updated according to this new direction."
 *
 * <p>Note: the turn distribution is applied once per simulation tick, not scaled by dt — so a
 * shorter publish interval means more frequent (and therefore more erratic-looking, over real
 * time) turns. Say the word if you'd rather the turniness be independent of the publish interval;
 * that just means scaling sigma by sqrt(dtSeconds) here.
 */
final class CircleRandomWalkBehavior implements FlightBehavior {

    private static final double TURN_SIGMA_RAD = Math.toRadians(45.0);

    private final Random random;

    CircleRandomWalkBehavior(Random random) {
        this.random = random;
    }

    @Override
    public StepResult step(Vector2 position, Vector2 velocity, double dtSeconds) {
        double turnAngleRad = random.nextGaussian() * TURN_SIGMA_RAD;
        Vector2 newVelocity = velocity.rotated(turnAngleRad);
        Vector2 newPosition = position.plus(newVelocity.scaled(dtSeconds));
        return new StepResult(newPosition, newVelocity);
    }
}
