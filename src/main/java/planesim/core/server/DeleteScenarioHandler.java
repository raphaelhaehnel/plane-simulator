package planesim.core.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import planesim.core.scenario.ScenarioManager;
import planesim.core.server.dto.DeleteResponse;
import planesim.core.server.dto.ErrorResponse;
import planesim.core.server.dto.IdRequest;

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
        IdRequest request = readBody(exchange, gson, IdRequest.class);
        if (!manager.delete(request.id)) {
            writeJson(exchange, 404, new ErrorResponse("scenario not found: " + request.id));
            return;
        }
        writeJson(exchange, 200, new DeleteResponse(request.id));
    }
}
