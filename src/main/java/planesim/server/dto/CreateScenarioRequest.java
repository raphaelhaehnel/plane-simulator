package planesim.server.dto;

/** POST /createScenario request body. */
public class CreateScenarioRequest {
    public String type;          // "PLANE" (only supported value today)
    public int amount;           // plane count
    public Double originLatRad;  // required, radians
    public Double originLonRad;  // required, radians
    public Double speed;         // optional, m/s, default 230.0
    public Double altitude;      // optional, meters, default 10000.0
    public long sendInterval;    // required, ms
    public FormationDto formation;
}
