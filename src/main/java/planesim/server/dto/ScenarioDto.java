package planesim.server.dto;

import java.util.List;

/** GET /getScenarios element: a scenario's config, status, and live per-plane snapshot. */
public class ScenarioDto {
    public String id;
    public String type;
    public String status;
    public int amount;
    public double originLatRad;
    public double originLonRad;
    public double speed;
    public double altitude;
    public long sendInterval;
    public FormationDto formation;
    public List<PlaneStateDto> planes;
}
