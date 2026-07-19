package planesim.core.server.dto;

/** POST /start and POST /pause response body. */
public class StartPauseResponse {
    public String id;
    public String status;

    public StartPauseResponse(String id, String status) {
        this.id = id;
        this.status = status;
    }
}
