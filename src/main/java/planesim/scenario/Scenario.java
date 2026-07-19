package planesim.scenario;

import planesim.core.SimulationConfig;
import planesim.core.SimulationEngine;

import java.util.List;

/**
 * One managed simulation run: its config, its {@link SimulationEngine}, and its current status.
 * Only {@link ScenarioManager} constructs one or touches its engine/status directly — everything
 * else (e.g. the HTTP layer) only ever reads through the public accessors. The engine's object
 * type parameter is erased here (wildcard) since nothing outside {@code ScenarioManager} needs to
 * know whether this scenario simulates planes or radars beyond {@link #type()}.
 */
public final class Scenario {

    private final String id;
    private final ScenarioType type;
    private final SimulationConfig config;
    private final SimulationEngine<?> engine;
    private final ScenarioNetworkApi networkApi;

    private volatile ScenarioStatus status = ScenarioStatus.CREATED;

    Scenario(String id, ScenarioType type, SimulationConfig config, SimulationEngine<?> engine,
             ScenarioNetworkApi networkApi) {
        this.id = id;
        this.type = type;
        this.config = config;
        this.engine = engine;
        this.networkApi = networkApi;
    }

    public String id() {
        return id;
    }

    public ScenarioType type() {
        return type;
    }

    public SimulationConfig config() {
        return config;
    }

    public ScenarioStatus status() {
        return status;
    }

    /** The latest known state of every object in this scenario, ordered by index. */
    public List<ObjectLiveState> liveSnapshot() {
        return networkApi.snapshot();
    }

    SimulationEngine<?> engine() {
        return engine;
    }

    void setStatus(ScenarioStatus status) {
        this.status = status;
    }
}
