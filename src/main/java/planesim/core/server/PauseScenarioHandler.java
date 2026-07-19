package planesim.core.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import planesim.core.scenario.ScenarioManager;
import planesim.core.server.dto.ErrorResponse;
import planesim.core.server.dto.IdRequest;
import planesim.core.server.dto.StartPauseResponse;

import java.io.IOException;

/** POST /pause */
final class PauseScenarioHandler extends AbstractJsonHandler {

    private final ScenarioManager manager;

    PauseScenarioHandler(ScenarioManager manager) {
        this.manager = manager;
    }

    @Override
    String method() {
        return "POST";
    }

    @Override
    void handleRequest(HttpExchange exchange, Gson gson) throws IOException {
        IdRequest request = readBody(exchange, gson, IdRequest.class);
        if (!manager.pause(request.id)) {
            writeJson(exchange, 404, new ErrorResponse("scenario not found: " + request.id));
            return;
        }
        writeJson(exchange, 200, new StartPauseResponse(request.id, "PAUSED"));
    }
}
