package planesim.server.dto;

/** Live state of one plane, embedded in {@link ScenarioDto}. */
public class PlaneStateDto {
    public int index;
    public double latRad;
    public double lonRad;
    public double headingDeg;
}
