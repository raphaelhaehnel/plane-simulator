package planesim;

/**
 * Planes arranged evenly around a circle centered on the simulation origin: 1 plane sits at the
 * center, 2 planes sit at opposite ends (right/left), 3 are spaced 120 degrees apart, and so on
 * (evenly spaced, {@code 360/planeCount} degrees apart, starting due east).
 *
 * <p>Each plane's first heading points radially outward from the center; after that, heading
 * evolves as an independent random walk per plane (see {@link CircleRandomWalkBehavior}) — there
 * is no destination and no turning back, planes simply wander indefinitely.
 *
 * @param radiusMeters radius of the circle, meters
 */
public record CircleFormation(double radiusMeters) implements FormationSpec {
    public CircleFormation {
        if (radiusMeters < 0) {
            throw new IllegalArgumentException("radiusMeters must not be negative");
        }
    }
}
