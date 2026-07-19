package planesim.formation;

/**
 * Objects arranged evenly around a circle centered on the simulation origin: 1 object sits at the
 * center, 2 objects sit at opposite ends (right/left), 3 are spaced 120 degrees apart, and so on
 * (evenly spaced, {@code 360/n} degrees apart, starting due east).
 *
 * <p>Each object's first heading points radially outward from the center. A {@code MOBILE} object
 * (e.g. a plane) then wanders independently from there (see {@code CircleRandomWalkBehavior}) —
 * there is no destination and no turning back. A {@code STATIC} object (e.g. a radar) just stays
 * put at its point on the circle — see {@code planesim.core.MovementStyle}.
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
