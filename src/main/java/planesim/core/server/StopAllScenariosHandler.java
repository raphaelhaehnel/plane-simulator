package planesim.core.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import planesim.core.scenario.ScenarioManager;
import planesim.core.server.api.StopAllResponse;

import java.io.IOException;
import java.util.List;

/** POST /stopAll — pauses every currently running scenario. No request body needed. */
final class StopAllScenariosHandler extends AbstractJsonHandler {

    private final ScenarioManager manager;

    StopAllScenariosHandler(ScenarioManager manager) {
        this.manager = manager;
    }

    @Override
    String method() {
        return "POST";
    }

    @Override
    void handleRequest(HttpExchange exchange, Gson gson) throws IOException {
        List<String> stoppedIds = manager.stopAll();
        writeJson(exchange, 200, new StopAllResponse(stoppedIds));
    }
}
