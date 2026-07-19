package planesim.core.server.dto;

/** POST /createScenario request body. */
public class CreateScenarioRequest {
    public String type;          // "PLANE", "RADAR", or "WEATHER"
    public int amount;           // object count
    public Double originLatRad;  // required for PLANE/RADAR, radians; unused for WEATHER
    public Double originLonRad;  // required for PLANE/RADAR, radians; unused for WEATHER
    public Double speed;         // optional, m/s, default 230.0; unused for RADAR/WEATHER
    public Double altitude;      // optional, meters, default 10000.0; unused for WEATHER
    public long sendInterval;    // required, ms
    public FormationDto formation; // required for PLANE/RADAR; unused for WEATHER
}
