package planesim.server.dto;

/** POST /createScenario response body. */
public class CreateScenarioResponse {
    public String id;

    public CreateScenarioResponse(String id) {
        this.id = id;
    }
}
