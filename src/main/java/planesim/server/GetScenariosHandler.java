package planesim.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import planesim.scenario.ScenarioManager;
import planesim.server.dto.ScenarioDto;

import java.io.IOException;
import java.util.List;

/** GET /getScenarios */
final class GetScenariosHandler extends AbstractJsonHandler {

    private final ScenarioManager manager;

    GetScenariosHandler(ScenarioManager manager) {
        this.manager = manager;
    }

    @Override
    String method() {
        return "GET";
    }

    @Override
    void handleRequest(HttpExchange exchange, Gson gson) throws IOException {
        List<ScenarioDto> dtos = manager.listScenarios().stream().map(RequestMapper::toDto).toList();
        writeJson(exchange, 200, dtos);
    }
}
