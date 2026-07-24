package planesim.core.server.api;

/** Wire shape of a formation spec: {@code type} is "LINE", "CIRCLE", or "ORBIT"; only the matching fields are required. */
public class FormationDto {
    public String type;

    // LINE only, all required
    public Double destLatRad;
    public Double destLonRad;
    public Double spacingMeters;

    // CIRCLE and ORBIT, required
    public Double radiusMeters;
}
