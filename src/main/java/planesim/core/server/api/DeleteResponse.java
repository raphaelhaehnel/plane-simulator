package planesim.core.server.api;

/** POST /deleteScenario response body. */
public class DeleteResponse {
    public String id;

    public DeleteResponse(String id) {
        this.id = id;
    }
}
