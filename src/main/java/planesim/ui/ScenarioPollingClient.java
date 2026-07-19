package planesim.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import planesim.server.dto.ScenarioDto;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Minimal HTTP client for the view-only UI: polls {@code GET /getScenarios} and reuses the
 * server's own {@link ScenarioDto} wire type directly rather than inventing a parallel
 * client-side DTO set.
 */
final class ScenarioPollingClient {

    private static final Type SCENARIO_LIST_TYPE = new TypeToken<List<ScenarioDto>>() {
    }.getType();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private final URI getScenariosUri;

    ScenarioPollingClient(String baseUrl) {
        this.getScenariosUri = URI.create(baseUrl + "/getScenarios");
    }

    List<ScenarioDto> fetchScenarios() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(getScenariosUri).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected status " + response.statusCode() + " from " + getScenariosUri);
        }
        return gson.fromJson(response.body(), SCENARIO_LIST_TYPE);
    }
}
