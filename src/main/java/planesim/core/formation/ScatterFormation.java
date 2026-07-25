package planesim.core.formation;

/**
 * Objects placed at uniformly-random positions inside a disk of the configured radius, centered on
 * the simulation origin (uniform over area, so no clustering toward the center). Each object starts
 * with a random heading; a {@code MOBILE} object then wanders independently from there (see
 * {@code CircleRandomWalkBehavior}) — there is no destination and no turning back. A {@code STATIC}
 * object just stays put at its scattered point — see {@code planesim.core.engine.MovementStyle}.
 *
 * @param radiusMeters radius of the disk objects are scattered within, meters — strictly positive,
 *                     since a zero radius would collapse every object onto the origin
 */
public record ScatterFormation(double radiusMeters) implements FormationSpec {
    public ScatterFormation {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }
    }
}
