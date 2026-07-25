package planesim.core.behavior;

import planesim.core.geo.Vector2;

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
 * <p>The turn's standard deviation is scaled by {@code sqrt(dtSeconds)} so the wander rate is
 * invariant to the publish interval: heading is a random walk, whose variance accumulates linearly
 * with the number of steps, so over a fixed real time {@code T} the accumulated variance is
 * {@code (T/dt) * sigma_step^2}; making that independent of {@code dt} requires
 * {@code sigma_step = sigma_rate * sqrt(dt)}. The 45-degree figure is therefore the standard
 * deviation <em>per sqrt(second)</em> (a rotational-diffusion rate), and reproduces the old
 * per-tick behavior at a 1-second publish interval.
 */
public final class CircleRandomWalkBehavior implements FlightBehavior {

    /** Rotational-diffusion rate: standard deviation of heading change per sqrt(second). */
    private static final double TURN_SIGMA_RATE_RAD_PER_SQRT_S = Math.toRadians(45.0);

    private final Random random;

    public CircleRandomWalkBehavior(Random random) {
        this.random = random;
    }

    @Override
    public StepResult step(Vector2 position, Vector2 velocity, double dtSeconds) {
        double sigma = TURN_SIGMA_RATE_RAD_PER_SQRT_S * Math.sqrt(dtSeconds);
        double turnAngleRad = random.nextGaussian() * sigma;
        Vector2 newVelocity = velocity.rotated(turnAngleRad);
        Vector2 newPosition = position.plus(newVelocity.scaled(dtSeconds));
        return new StepResult(newPosition, newVelocity);
    }
}
