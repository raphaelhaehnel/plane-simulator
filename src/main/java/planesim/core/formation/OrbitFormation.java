package planesim.core.formation;

/**
 * Objects arranged evenly around a circle centered on the simulation origin — same placement
 * geometry as {@link CircleFormation} ({@code 360/n} degrees apart, starting due east) — but a
 * {@code MOBILE} object then flies <em>along</em> the circle, in the tangent direction, clockwise
 * (as seen on a north-up map: east point → south → west → north), forever (see
 * {@code OrbitBehavior}). A {@code STATIC} object just stays put at its point on the circle.
 *
 * <p>Unlike {@link CircleFormation}, a single object ({@code n=1}) is placed <em>on</em> the ring
 * (due east of the center), not at the center — an orbiting object needs a ring to fly.
 *
 * @param radiusMeters radius of the circle, meters — strictly positive, since the orbit's angular
 *                     speed is {@code speed / radius}
 */
public record OrbitFormation(double radiusMeters) implements FormationSpec {
    public OrbitFormation {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }
    }
}
