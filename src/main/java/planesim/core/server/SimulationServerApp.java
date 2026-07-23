package planesim.core.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import planesim.core.network.NetworkConfiguration;
import planesim.core.network.NetworkManager;
import planesim.core.scenario.ScenarioEngineFactories;
import planesim.core.scenario.ScenarioManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Entry point for the HTTP API: exposes {@code POST /createScenario}, {@code GET /getScenarios},
 * {@code POST /deleteScenario}, {@code POST /start}, {@code POST /pause}, and
 * {@code POST /stopAll} over a {@link ScenarioManager}. All scenarios' ticking shares one bounded
 * {@link ScheduledExecutorService} (see {@code SimulationEngine}'s javadoc for why that's safe),
 * separate from the pool that serves incoming HTTP requests.
 */
public final class SimulationServerApp {

    private static final int DEFAULT_PORT = 8080;

    /** Bounded so a burst of requests can't grow the HTTP request-handling pool without limit. */
    private static final int HTTP_HANDLER_THREADS = 64;

    /** Classpath location of the network configuration (see {@code src/main/resources}). */
    private static final String CONFIG_RESOURCE = "/config.json";

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        ScheduledExecutorService scenarioScheduler = Executors.newScheduledThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()));
        ScenarioManager manager = new ScenarioManager(scenarioScheduler, ScenarioEngineFactories.DEFAULTS, buildNetwork());

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/createScenario", new CreateScenarioHandler(manager));
        server.createContext("/getScenarios", new GetScenariosHandler(manager));
        server.createContext("/deleteScenario", new DeleteScenarioHandler(manager));
        server.createContext("/start", new StartScenarioHandler(manager));
        server.createContext("/pause", new PauseScenarioHandler(manager));
        server.createContext("/stopAll", new StopAllScenariosHandler(manager));
        server.setExecutor(Executors.newFixedThreadPool(HTTP_HANDLER_THREADS));
        server.start();

        System.out.println("SimulationServer listening on http://localhost:" + port);
    }

    /**
     * Builds the one {@link NetworkManager} for this JVM from {@code config.json} on the classpath.
     * No topics are opened here anymore — each scenario opens its own writer from the
     * {@code topicName} it was created with (see {@link NetworkManager#openWriter(String)}).
     */
    private static NetworkManager buildNetwork() {
        return NetworkManager.builder()
                .configuration(loadConfiguration())
                .build();
    }

    /** Reads {@code config.json} from the classpath into a {@link NetworkConfiguration} via Jackson. */
    private static NetworkConfiguration loadConfiguration() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = SimulationServerApp.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Configuration resource not found on classpath: " + CONFIG_RESOURCE);
            }
            return mapper.readValue(in, NetworkConfiguration.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + CONFIG_RESOURCE, e);
        }
    }
}
