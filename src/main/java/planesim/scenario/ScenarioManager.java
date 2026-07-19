package planesim.scenario;

import planesim.api.Plane;
import planesim.core.SimulationConfig;
import planesim.core.SimulationEngine;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Thread-safe in-memory registry of all scenarios, and the only place scenarios are created,
 * started, paused, or deleted. Every scenario's {@link SimulationEngine} runs on the same shared
 * {@code scheduler} passed in at construction — see {@link SimulationEngine}'s javadoc for why
 * that's safe across many concurrently-running scenarios.
 */
public final class ScenarioManager {

    private final ConcurrentHashMap<String, Scenario> scenarios = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sharedScheduler;

    public ScenarioManager(ScheduledExecutorService sharedScheduler) {
        this.sharedScheduler = sharedScheduler;
    }

    /** Creates and registers a new scenario in {@link ScenarioStatus#CREATED} state. Does not start it. */
    public Scenario createScenario(ScenarioType type, SimulationConfig config) {
        String id = UUID.randomUUID().toString();
        ScenarioNetworkApi networkApi = new ScenarioNetworkApi();
        SimulationEngine engine = SimulationEngine.create(config, networkApi, Plane::new, sharedScheduler);
        Scenario scenario = new Scenario(id, type, config, engine, networkApi);
        scenarios.put(id, scenario);
        return scenario;
    }

    public List<Scenario> listScenarios() {
        return List.copyOf(scenarios.values());
    }

    /** Starts a new scenario, or resumes a paused one from wherever it left off. False if {@code id} is unknown. */
    public boolean start(String id) {
        Scenario scenario = scenarios.get(id);
        if (scenario == null) {
            return false;
        }
        scenario.engine().start();
        scenario.setStatus(ScenarioStatus.RUNNING);
        return true;
    }

    /** Halts ticking without losing plane state. False if {@code id} is unknown. */
    public boolean pause(String id) {
        Scenario scenario = scenarios.get(id);
        if (scenario == null) {
            return false;
        }
        scenario.engine().pause();
        scenario.setStatus(ScenarioStatus.PAUSED);
        return true;
    }

    /** Removes a scenario from memory, stopping its ticking first. False if {@code id} is unknown. */
    public boolean delete(String id) {
        Scenario scenario = scenarios.remove(id);
        if (scenario == null) {
            return false;
        }
        scenario.engine().pause();
        return true;
    }
}
