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
 * <p>All planes currently share the same speed and altitude. If you want per-plane speed/altitude
 * later, swap those two fields for a per-index supplier; nothing else in the design needs to
 * change.
 */
public record SimulationConfig(
        double originLatRad,
        double originLonRad,
        int planeCount,
        double speedMps,
        double altitudeMeters,
        long publishIntervalMs,
        FormationSpec formation
) {
    public SimulationConfig {
        if (planeCount <= 0) {
            throw new IllegalArgumentException("planeCount must be positive");
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
