package planesim.scenario;

import planesim.api.Plane;
import planesim.api.Radar;
import planesim.core.MovementStyle;
import planesim.core.ObjectWriters;
import planesim.core.SimulationConfig;
import planesim.core.SimulationEngine;

import java.util.ArrayList;
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

    /**
     * Creates and registers a new scenario in {@link ScenarioStatus#CREATED} state. Does not start
     * it. Dispatches on {@code type} to pick the right external object class, its {@link
     * planesim.core.ObjectWriter}, and its {@link MovementStyle} (planes fly, radars stay put).
     */
    public Scenario createScenario(ScenarioType type, SimulationConfig config) {
        String id = UUID.randomUUID().toString();
        ScenarioNetworkApi networkApi = new ScenarioNetworkApi();
        SimulationEngine<?> engine = switch (type) {
            case PLANE -> SimulationEngine.<Plane>create(config, MovementStyle.MOBILE,
                    networkApi::send, Plane::new, ObjectWriters.PLANE, sharedScheduler);
            case RADAR -> SimulationEngine.<Radar>create(config, MovementStyle.STATIC,
                    networkApi::send, Radar::new, ObjectWriters.RADAR, sharedScheduler);
        };
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

    /** Halts ticking without losing object state. False if {@code id} is unknown. */
    public boolean pause(String id) {
        Scenario scenario = scenarios.get(id);
        if (scenario == null) {
            return false;
        }
        scenario.engine().pause();
        scenario.setStatus(ScenarioStatus.PAUSED);
        return true;
    }

    /** Pauses every currently {@link ScenarioStatus#RUNNING} scenario. Returns the ids that were actually stopped. */
    public List<String> stopAll() {
        List<String> stoppedIds = new ArrayList<>();
        for (Scenario scenario : scenarios.values()) {
            if (scenario.status() == ScenarioStatus.RUNNING) {
                scenario.engine().pause();
                scenario.setStatus(ScenarioStatus.PAUSED);
                stoppedIds.add(scenario.id());
            }
        }
        return stoppedIds;
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
