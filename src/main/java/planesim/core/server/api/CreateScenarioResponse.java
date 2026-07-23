package planesim.core.server.api;

/** POST /createScenario response body. */
public class CreateScenarioResponse {
    public String id;

    public CreateScenarioResponse(String id) {
        this.id = id;
    }
}
