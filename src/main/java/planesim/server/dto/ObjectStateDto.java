package planesim.server.dto;

/** Live state of one simulated object (plane, radar, ...), embedded in {@link ScenarioDto}. */
public class ObjectStateDto {
    public int index;
    public double latRad;
    public double lonRad;
    public double headingDeg;
}
