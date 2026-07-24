package planesim.core.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import planesim.core.scenario.ScenarioType;
import planesim.core.server.api.ScenarioTypeDto;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** GET /getTypes */
final class GetTypesHandler extends AbstractJsonHandler {

    @Override
    String method() {
        return "GET";
    }

    @Override
    void handleRequest(HttpExchange exchange, Gson gson) throws IOException {
        List<ScenarioTypeDto> dtos = Arrays.stream(ScenarioType.values())
                .map(RequestMapper::toTypeDto)
                .toList();
        writeJson(exchange, 200, dtos);
    }
}
