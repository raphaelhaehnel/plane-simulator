package planesim.core.formation;

/**
 * Objects arranged in a straight line perpendicular to the source-&gt;destination route, evenly
 * spaced and centered on that route. A {@code MOBILE} object (e.g. a plane) flies its own parallel
 * copy of the route back and forth forever; a {@code STATIC} object (e.g. a radar) just stays at
 * its point on the line — see {@code planesim.core.engine.MovementStyle}.
 *
 * @param destLatRad     destination latitude, radians
 * @param destLonRad     destination longitude, radians
 * @param spacingMeters  distance between adjacent objects in the line, meters
 */
public record LineFormation(double destLatRad, double destLonRad, double spacingMeters) implements FormationSpec {
    public LineFormation {
        if (spacingMeters < 0) {
            throw new IllegalArgumentException("spacingMeters must not be negative");
        }
    }
}
