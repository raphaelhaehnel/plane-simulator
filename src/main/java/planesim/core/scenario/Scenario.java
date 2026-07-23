package planesim.core.scenario;

import planesim.core.engine.ScenarioConfig;
import planesim.core.engine.SimulationEngine;

import java.util.List;

/**
 * One managed simulation run: its config, its {@link SimulationEngine}, and its current status.
 * Only {@link ScenarioManager} constructs one or touches its engine/status directly — everything
 * else (e.g. the HTTP layer) only ever reads through the public accessors. The engine's object
 * type parameter is erased here (wildcard) since nothing outside {@code ScenarioManager} needs to
 * know exactly which object type this scenario simulates beyond {@link #type()}. {@code config} is
 * a {@link ScenarioConfig} rather than a concrete type since a scenario may be geographic ({@code
 * SimulationConfig}, e.g. plane/radar) or not ({@code ValueSimulationConfig}, e.g. weather).
 */
public final class Scenario {

    private final String id;
    private final ScenarioType type;
    private final String topicName;
    private final ScenarioConfig config;
    private final SimulationEngine<?> engine;
    private final ScenarioPublisher publisher;

    private volatile ScenarioStatus status = ScenarioStatus.CREATED;

    Scenario(String id, ScenarioType type, String topicName, ScenarioConfig config,
             SimulationEngine<?> engine, ScenarioPublisher publisher) {
        this.id = id;
        this.type = type;
        this.topicName = topicName;
        this.config = config;
        this.engine = engine;
        this.publisher = publisher;
    }

    public String id() {
        return id;
    }

    public ScenarioType type() {
        return type;
    }

    /** The network topic this scenario's objects are published on, chosen at creation time. */
    public String topicName() {
        return topicName;
    }

    public ScenarioConfig config() {
        return config;
    }

    public ScenarioStatus status() {
        return status;
    }

    /** The latest known state of every geographic object (plane/radar/...) in this scenario, ordered by index. */
    public List<GeoLiveState> liveGeoSnapshot() {
        return publisher.geoSnapshot();
    }

    /** The latest known reading of every non-geographic object (weather/...) in this scenario, ordered by index. */
    public List<NonGeoLiveState> liveNonGeoSnapshot() {
        return publisher.nonGeoSnapshot();
    }

    SimulationEngine<?> engine() {
        return engine;
    }

    void setStatus(ScenarioStatus status) {
        this.status = status;
    }
}
