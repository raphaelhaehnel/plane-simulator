package planesim.core.scenario;

import planesim.core.engine.ScenarioConfig;
import planesim.core.engine.SimulationEngine;
import planesim.core.network.NetworkManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /** Upper bound on concurrently-registered scenarios — protects against unbounded resource growth. */
    public static final int MAX_SCENARIOS = 100;

    private final ConcurrentHashMap<String, Scenario> scenarios = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sharedScheduler;
    private final Map<ScenarioType, ScenarioEngineFactory> engineFactories;
    private final NetworkManager network;

    /**
     * @param network the single {@link NetworkManager} every scenario publishes through — injected
     *                rather than looked up via {@code getInstance()} so this class stays
     *                independent of how the singleton is installed
     */
    public ScenarioManager(ScheduledExecutorService sharedScheduler,
                           Map<ScenarioType, ScenarioEngineFactory> engineFactories,
                           NetworkManager network) {
        this.sharedScheduler = sharedScheduler;
        this.network = network;
        this.engineFactories = new EnumMap<>(engineFactories);
        for (ScenarioType type : ScenarioType.values()) {
            if (!this.engineFactories.containsKey(type)) {
                throw new IllegalStateException("No engine factory registered for scenario type: " + type);
            }
        }
    }

    /**
     * Creates and registers a new scenario in {@link ScenarioStatus#CREATED} state. Does not start
     * it. Dispatches on {@code type} via the {@link ScenarioEngineFactory} supplied at
     * construction — adding a new {@link ScenarioType} only requires registering its factory, not
     * editing this class. {@code config} must be the matching {@link ScenarioConfig} kind for
     * {@code type} — {@link RequestMapper} guarantees that.
     *
     * @param topicName the network topic this scenario publishes on; a writer is opened for it now
     *                  (shared/reference-counted if another scenario already uses the same name) and
     *                  released on {@link #delete(String)}
     * @throws ScenarioLimitExceededException if {@link #MAX_SCENARIOS} scenarios are already registered
     */
    public Scenario createScenario(ScenarioType type, ScenarioConfig config, String topicName) {
        if (scenarios.size() >= MAX_SCENARIOS) {
            throw new ScenarioLimitExceededException(
                    "Maximum number of concurrent scenarios (" + MAX_SCENARIOS + ") reached; delete an existing one first");
        }
        String id = UUID.randomUUID().toString();
        ScenarioPublisher publisher = new ScenarioPublisher(network, topicName);
        ScenarioEngineFactory factory = engineFactories.get(type);
        SimulationEngine<?> engine = factory.createEngine(config, publisher, sharedScheduler);
        network.openWriter(topicName);
        Scenario scenario = new Scenario(id, type, topicName, config, engine, publisher);
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

    /**
     * Removes a scenario from memory, stopping its ticking first and releasing its network writer
     * (which is only actually closed if no other scenario is still using the same topic). False if
     * {@code id} is unknown.
     */
    public boolean delete(String id) {
        Scenario scenario = scenarios.remove(id);
        if (scenario == null) {
            return false;
        }
        scenario.engine().pause();
        network.closeWriter(scenario.topicName());
        return true;
    }
}
