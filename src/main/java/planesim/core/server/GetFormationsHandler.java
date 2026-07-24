package planesim.core.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import planesim.core.server.api.FormationDescriptorDto;

import java.io.IOException;
import java.util.List;

/** GET /getFormations */
final class GetFormationsHandler extends AbstractJsonHandler {

    @Override
    String method() {
        return "GET";
    }

    @Override
    void handleRequest(HttpExchange exchange, Gson gson) throws IOException {
        List<FormationDescriptorDto> dtos = FormationCatalog.ALL.stream()
                .map(RequestMapper::toFormationDescriptorDto)
                .toList();
        writeJson(exchange, 200, dtos);
    }
}
