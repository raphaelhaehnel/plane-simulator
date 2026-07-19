package planesim.server.dto;

/** POST /deleteScenario response body. */
public class DeleteResponse {
    public String id;

    public DeleteResponse(String id) {
        this.id = id;
    }
}
