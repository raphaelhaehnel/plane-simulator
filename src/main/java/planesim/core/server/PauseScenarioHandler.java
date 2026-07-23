package planesim.core.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import planesim.core.scenario.ScenarioManager;
import planesim.core.server.api.ErrorResponse;
import planesim.core.server.api.ScenarioIdRequest;
import planesim.core.server.api.ScenarioStatusResponse;

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
        ScenarioIdRequest request = readBody(exchange, gson, ScenarioIdRequest.class);
        String id = RequestMapper.requireScenarioId(request);
        if (!manager.pause(id)) {
            writeJson(exchange, 404, new ErrorResponse("scenario not found: " + id));
            return;
        }
        writeJson(exchange, 200, new ScenarioStatusResponse(id, "PAUSED"));
    }
}
