package planesim.core.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import planesim.core.engine.ScenarioConfig;
import planesim.core.scenario.Scenario;
import planesim.core.scenario.ScenarioManager;
import planesim.core.scenario.ScenarioType;
import planesim.core.server.dto.CreateScenarioRequest;
import planesim.core.server.dto.CreateScenarioResponse;

import java.io.IOException;

/** POST /createScenario */
final class CreateScenarioHandler extends AbstractJsonHandler {

    private final ScenarioManager manager;

    CreateScenarioHandler(ScenarioManager manager) {
        this.manager = manager;
    }

    @Override
    String method() {
        return "POST";
    }

    @Override
    void handleRequest(HttpExchange exchange, Gson gson) throws IOException {
        CreateScenarioRequest request = readBody(exchange, gson, CreateScenarioRequest.class);
        ScenarioType type = RequestMapper.toScenarioType(request.type);
        ScenarioConfig config = RequestMapper.toScenarioConfig(type, request);
        Scenario scenario = manager.createScenario(type, config);
        writeJson(exchange, 200, new CreateScenarioResponse(scenario.id()));
    }
}
