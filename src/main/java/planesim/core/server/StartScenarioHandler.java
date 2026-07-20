package planesim.core.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import planesim.core.scenario.ScenarioManager;
import planesim.core.server.dto.ErrorResponse;
import planesim.core.server.dto.ScenarioIdRequest;
import planesim.core.server.dto.ScenarioStatusResponse;

import java.io.IOException;

/** POST /start — starts a new scenario, or resumes a paused one. */
final class StartScenarioHandler extends AbstractJsonHandler {

    private final ScenarioManager manager;

    StartScenarioHandler(ScenarioManager manager) {
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
        if (!manager.start(id)) {
            writeJson(exchange, 404, new ErrorResponse("scenario not found: " + id));
            return;
        }
        writeJson(exchange, 200, new ScenarioStatusResponse(id, "RUNNING"));
    }
}
