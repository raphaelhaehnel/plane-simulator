package planesim.core.engine;

/**
 * Config surface shared by every scenario kind, geographic ({@link SimulationConfig} — has an
 * origin/formation) or not ({@link ValueSimulationConfig} — e.g. weather, no position at all).
 * Only what's meaningful for every kind lives here; scenario-kind-specific fields (origin,
 * formation, speed, altitude) stay on the concrete type and are read via an {@code instanceof}
 * check where needed (see {@code planesim.core.server.RequestMapper.toDto}).
 */
public sealed interface ScenarioConfig permits SimulationConfig, ValueSimulationConfig {
    int objectCount();

    long publishIntervalMs();
}
