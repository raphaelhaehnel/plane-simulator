package planesim.core.engine;

/**
 * Tunable parameters for a non-geographic, value-only simulation run (e.g. weather) — no origin,
 * no formation, no movement, just {@code objectCount} independent instances regenerated every
 * {@code publishIntervalMs}. See {@link GeoScenarioConfig} for the geographic counterpart.
 */
public record NonGeoScenarioConfig(int objectCount, long publishIntervalMs) implements ScenarioConfig {
    public NonGeoScenarioConfig {
        if (objectCount <= 0) {
            throw new IllegalArgumentException("objectCount must be positive");
        }
        if (objectCount > MAX_OBJECT_COUNT) {
            throw new IllegalArgumentException("objectCount must not exceed " + MAX_OBJECT_COUNT);
        }
        if (publishIntervalMs <= 0) {
            throw new IllegalArgumentException("publishIntervalMs must be positive");
        }
    }
}
