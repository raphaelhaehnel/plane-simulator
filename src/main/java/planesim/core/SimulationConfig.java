package planesim.core;

import planesim.formation.CircleFormation;
import planesim.formation.FormationSpec;
import planesim.formation.LineFormation;

/**
 * All the tunable parameters for one formation run.
 *
 * <p>{@code originLatRad}/{@code originLonRad} is the source point for a {@link LineFormation}
 * or the circle's center for a {@link CircleFormation}.
 *
 * <p>All objects currently share the same speed and altitude (speed is meaningless for a static
 * object, e.g. a radar — it's simply not applied by that object's {@code FlightBehavior}). If you
 * want per-object speed/altitude later, swap those two fields for a per-index supplier; nothing
 * else in the design needs to change.
 */
public record SimulationConfig(
        double originLatRad,
        double originLonRad,
        int objectCount,
        double speedMps,
        double altitudeMeters,
        long publishIntervalMs,
        FormationSpec formation
) implements ScenarioConfig {
    public SimulationConfig {
        if (objectCount <= 0) {
            throw new IllegalArgumentException("objectCount must be positive");
        }
        if (speedMps <= 0) {
            throw new IllegalArgumentException("speedMps must be positive");
        }
        if (publishIntervalMs <= 0) {
            throw new IllegalArgumentException("publishIntervalMs must be positive");
        }
        if (formation == null) {
            throw new IllegalArgumentException("formation must not be null");
        }
    }
}
