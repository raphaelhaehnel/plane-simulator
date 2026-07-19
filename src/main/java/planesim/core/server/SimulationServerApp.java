package planesim.core.server;

import com.sun.net.httpserver.HttpServer;
import planesim.core.scenario.ScenarioManager;

import java.io.IOException;
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

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        ScheduledExecutorService scenarioScheduler = Executors.newScheduledThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()));
        ScenarioManager manager = new ScenarioManager(scenarioScheduler);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/createScenario", new CreateScenarioHandler(manager));
        server.createContext("/getScenarios", new GetScenariosHandler(manager));
        server.createContext("/deleteScenario", new DeleteScenarioHandler(manager));
        server.createContext("/start", new StartScenarioHandler(manager));
        server.createContext("/pause", new PauseScenarioHandler(manager));
        server.createContext("/stopAll", new StopAllScenariosHandler(manager));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("SimulationServer listening on http://localhost:" + port);
    }
}
