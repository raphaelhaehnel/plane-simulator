package planesim.core.server.api;

/** Live state of one geographic simulated object (plane, radar, ...), embedded in {@link ScenarioDto}. */
public class GeoStateDto {
    public int index;
    public double latRad;
    public double lonRad;
    public double headingDeg;
}
