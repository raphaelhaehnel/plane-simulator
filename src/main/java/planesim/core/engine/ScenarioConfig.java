package planesim.core.engine;

/**
 * Config surface shared by every scenario kind, geographic ({@link GeoScenarioConfig} — has an
 * origin/formation) or not ({@link NonGeoScenarioConfig} — e.g. weather, no position at all).
 * Only what's meaningful for every kind lives here; scenario-kind-specific fields (origin,
 * formation, speed, altitude) stay on the concrete type and are read via an {@code instanceof}
 * check where needed (see {@code planesim.core.server.RequestMapper.toDto}).
 */
public sealed interface ScenarioConfig permits GeoScenarioConfig, NonGeoScenarioConfig {

    /** Upper bound on {@link #objectCount()}, shared by every scenario kind — protects against an accidental/malicious huge allocation. */
    int MAX_OBJECT_COUNT = 10_000;

    int objectCount();

    long publishIntervalMs();
}
