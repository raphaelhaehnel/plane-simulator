package planesim.server;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import planesim.server.dto.ErrorResponse;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Centralizes HTTP method checking, JSON body reading/writing, and mapping exceptions to status
 * codes, so each endpoint handler only needs to implement {@link #handleRequest}.
 */
abstract class AbstractJsonHandler implements HttpHandler {

    private static final Gson GSON = new Gson();

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try {
            if (!method().equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, new ErrorResponse("Method not allowed, expected " + method()));
                return;
            }
            handleRequest(exchange, GSON);
        } catch (BadRequestException | IllegalArgumentException | NullPointerException | JsonSyntaxException e) {
            writeJson(exchange, 400, new ErrorResponse(e.getMessage() != null ? e.getMessage() : "Invalid request"));
        } catch (RuntimeException e) {
            // Matches SimulationEngine.tick()'s "never let an exception escape silently" convention.
            e.printStackTrace();
            writeJson(exchange, 500, new ErrorResponse("Internal server error"));
        } finally {
            exchange.close();
        }
    }

    abstract String method();

    abstract void handleRequest(HttpExchange exchange, Gson gson) throws IOException;

    static <T> T readBody(HttpExchange exchange, Gson gson, Class<T> type) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            T body = gson.fromJson(reader, type);
            if (body == null) {
                throw new BadRequestException("Request body must not be empty");
            }
            return body;
        }
    }

    static void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
