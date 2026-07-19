package planesim.core.server.dto;

import java.util.List;

/**
 * GET /getScenarios element: a scenario's config, status, and live per-object snapshot. Geographic
 * fields ({@code originLatRad}/{@code originLonRad}/{@code speed}/{@code altitude}/{@code
 * formation}/{@code geoObjects}) are null for a non-geographic scenario (e.g. weather), which
 * populates {@code nonGeoObjects} instead.
 */
public class ScenarioDto {
    public String id;
    public String type;
    public String status;
    public int amount;
    public Double originLatRad;
    public Double originLonRad;
    public Double speed;
    public Double altitude;
    public long sendInterval;
    public FormationDto formation;
    public List<GeoStateDto> geoObjects;
    public List<NonGeoStateDto> nonGeoObjects;
}
