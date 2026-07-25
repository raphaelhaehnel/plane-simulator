package planesim.core.formation;

/**
 * Objects arranged in a flying-V (wedge): one object at the apex, the rest trailing back-and-outward
 * along two symmetric arms. Like {@link LineFormation}, a wedge flies along a source-&gt;destination
 * route — a {@code MOBILE} object flies its own parallel copy of that route so the whole V holds
 * shape and shuttles back and forth forever (see {@code LineBounceBehavior}); a {@code STATIC}
 * object just holds its point in the V — see {@code planesim.core.engine.MovementStyle}.
 *
 * <p>The apex points forward (the direction of travel); each successive pair of objects steps one
 * {@code spacingMeters} further back along the two arms, which splay out from the apex at half the
 * {@code apexAngleRad} interior angle on each side.
 *
 * @param destLatRad    destination latitude, radians
 * @param destLonRad    destination longitude, radians
 * @param spacingMeters distance between adjacent objects along each arm, meters
 * @param apexAngleRad  interior angle at the apex between the two arms, radians — strictly between
 *                      0 and {@code PI} (0 would collapse the V onto a line straight back, PI onto a
 *                      straight line across)
 */
public record WedgeFormation(double destLatRad, double destLonRad, double spacingMeters,
                             double apexAngleRad) implements FormationSpec {
    public WedgeFormation {
        if (spacingMeters < 0) {
            throw new IllegalArgumentException("spacingMeters must not be negative");
        }
        if (apexAngleRad <= 0 || apexAngleRad >= Math.PI) {
            throw new IllegalArgumentException("apexAngleRad must be strictly between 0 and PI");
        }
    }
}
