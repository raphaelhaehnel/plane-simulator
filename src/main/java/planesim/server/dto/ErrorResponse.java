package planesim.server.dto;

/** Response body for any non-2xx response. */
public class ErrorResponse {
    public String error;

    public ErrorResponse(String error) {
        this.error = error;
    }
}
