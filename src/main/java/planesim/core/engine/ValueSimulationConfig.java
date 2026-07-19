package planesim.core.engine;

/**
 * Tunable parameters for a non-geographic, value-only simulation run (e.g. weather) — no origin,
 * no formation, no movement, just {@code objectCount} independent instances regenerated every
 * {@code publishIntervalMs}. See {@link SimulationConfig} for the geographic counterpart.
 */
public record ValueSimulationConfig(int objectCount, long publishIntervalMs) implements ScenarioConfig {
    public ValueSimulationConfig {
        if (objectCount <= 0) {
            throw new IllegalArgumentException("objectCount must be positive");
        }
        if (publishIntervalMs <= 0) {
            throw new IllegalArgumentException("publishIntervalMs must be positive");
        }
    }
}
