package planesim;

/**
 * Planes arranged in a straight line perpendicular to the source-&gt;destination route, evenly
 * spaced and centered on that route, each flying its own parallel copy of the route back and
 * forth forever.
 *
 * @param destLatRad     destination latitude, radians
 * @param destLonRad     destination longitude, radians
 * @param spacingMeters  distance between adjacent planes in the line, meters
 */
public record LineFormation(double destLatRad, double destLonRad, double spacingMeters) implements FormationSpec {
    public LineFormation {
        if (spacingMeters < 0) {
            throw new IllegalArgumentException("spacingMeters must not be negative");
        }
    }
}
