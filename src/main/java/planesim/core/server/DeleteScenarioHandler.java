package planesim.core.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import planesim.core.scenario.ScenarioManager;
import planesim.core.server.dto.DeleteResponse;
import planesim.core.server.dto.ErrorResponse;
import planesim.core.server.dto.ScenarioIdRequest;

import java.io.IOException;

/** POST /deleteScenario */
final class DeleteScenarioHandler extends AbstractJsonHandler {

    private final ScenarioManager manager;

    DeleteScenarioHandler(ScenarioManager manager) {
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
        if (!manager.delete(id)) {
            writeJson(exchange, 404, new ErrorResponse("scenario not found: " + id));
            return;
        }
        writeJson(exchange, 200, new DeleteResponse(id));
    }
}
