package planesim.core.engine;

import planesim.core.formation.CircleFormation;
import planesim.core.formation.FormationSpec;
import planesim.core.formation.LineFormation;

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
public record GeoScenarioConfig(
        double originLatRad,
        double originLonRad,
        int objectCount,
        double speedMps,
        double altitudeMeters,
        long publishIntervalMs,
        FormationSpec formation
) implements ScenarioConfig {

    /**
     * Below this, {@code 1 / cos(originLatRad)} in {@code GeoMath.toLocal}/{@code toLatLon} blows
     * up toward infinity — the flat equirectangular projection this simulator uses isn't valid
     * within ~{@code arccos(MIN_COS_LATITUDE)} of the poles.
     */
    private static final double MIN_COS_LATITUDE = 0.01;

    public GeoScenarioConfig {
        if (objectCount <= 0) {
            throw new IllegalArgumentException("objectCount must be positive");
        }
        if (objectCount > MAX_OBJECT_COUNT) {
            throw new IllegalArgumentException("objectCount must not exceed " + MAX_OBJECT_COUNT);
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
        if (Math.abs(Math.cos(originLatRad)) < MIN_COS_LATITUDE) {
            throw new IllegalArgumentException(
                    "originLatRad is too close to a pole for this simulator's flat-earth (equirectangular) "
                            + "projection to stay accurate: " + originLatRad + " rad");
        }
    }
}
