package planesim.core.server.api;

/** POST /start and POST /pause response body. */
public class ScenarioStatusResponse {
    public String id;
    public String status;

    public ScenarioStatusResponse(String id, String status) {
        this.id = id;
        this.status = status;
    }
}
