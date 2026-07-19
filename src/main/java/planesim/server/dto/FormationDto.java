package planesim.server.dto;

/** Wire shape of a formation spec: {@code type} is "LINE" or "CIRCLE"; only the matching fields are required. */
public class FormationDto {
    public String type;

    // LINE only, all required
    public Double destLatRad;
    public Double destLonRad;
    public Double spacingMeters;

    // CIRCLE only, required
    public Double radiusMeters;
}
