package planesim.core.server.api;

/**
 * Wire shape of a formation spec: {@code type} is "LINE", "CIRCLE", "ORBIT", "WEDGE", or "SCATTER";
 * only the matching fields are required.
 */
public class FormationDto {
    public String type;

    // LINE and WEDGE, required
    public Double destLatRad;
    public Double destLonRad;
    public Double spacingMeters;

    // WEDGE only, required
    public Double apexAngleRad;

    // CIRCLE, ORBIT, and SCATTER, required
    public Double radiusMeters;
}
