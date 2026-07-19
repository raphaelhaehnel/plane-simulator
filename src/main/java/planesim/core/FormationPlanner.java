package planesim.core;

import planesim.behavior.CircleRandomWalkBehavior;
import planesim.behavior.FlightBehavior;
import planesim.behavior.LineBounceBehavior;
import planesim.behavior.StaticBehavior;
import planesim.formation.CircleFormation;
import planesim.formation.LineFormation;
import planesim.geo.GeoMath;
import planesim.geo.Vector2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Builds the initial formation from a {@link SimulationConfig}, dispatching on which
 * {@link planesim.formation.FormationSpec} was chosen. Type-agnostic over the external object
 * type {@code T} — the same line/circle placement geometry works for any object, only the
 * per-tick {@link FlightBehavior} differs by {@link MovementStyle}.
 */
public final class FormationPlanner {

    private FormationPlanner() {
    }

    /**
     * @param movementStyle whether the placed objects fly their formation's natural movement
     *                      pattern ({@link MovementStyle#MOBILE}) or stay fixed in place
     *                      ({@link MovementStyle#STATIC}, e.g. a radar)
     * @param objectFactory supplies one new externally-provided object instance per simulated
     *                      object (e.g. {@code Plane::new}, or a lambda that also assigns an id)
     */
    static <T> List<SimulatedEntity<T>> buildFormation(SimulationConfig config, MovementStyle movementStyle,
                                                         Supplier<T> objectFactory, ObjectWriter<T> writer) {
        if (config.formation() instanceof LineFormation line) {
            return buildLineFormation(config, line, movementStyle, objectFactory, writer);
        }
        if (config.formation() instanceof CircleFormation circle) {
            return buildCircleFormation(config, circle, movementStyle, objectFactory, writer);
        }
        throw new IllegalStateException("Unhandled formation type: " + config.formation());
    }

    /**
     * N objects placed along parallel lines between source and destination, evenly spaced along
     * the axis perpendicular to the route, centered on the source/destination centerline. Each
     * object gets its own source and destination point — both the config's source and destination
     * shifted by the same perpendicular offset — so a {@link MovementStyle#MOBILE} object flies its
     * own parallel line rather than converging onto a single destination point; a
     * {@link MovementStyle#STATIC} object just stays at its source point.
     */
    private static <T> List<SimulatedEntity<T>> buildLineFormation(SimulationConfig config, LineFormation line,
                                                                     MovementStyle movementStyle,
                                                                     Supplier<T> objectFactory, ObjectWriter<T> writer) {
        double originLatRad = config.originLatRad();
        double originLonRad = config.originLonRad();

        Vector2 sourceLocal = Vector2.ZERO;
        Vector2 destLocal = GeoMath.toLocal(line.destLatRad(), line.destLonRad(), originLatRad, originLonRad);

        Vector2 routeDirection = destLocal.minus(sourceLocal).normalized();
        Vector2 perpendicularAxis = routeDirection.perpendicular();

        int n = config.objectCount();
        List<SimulatedEntity<T>> formation = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            // Centered arrangement: e.g. for n=5 the offset indices are -2,-1,0,1,2.
            double offsetIndex = i - (n - 1) / 2.0;
            Vector2 offset = perpendicularAxis.scaled(offsetIndex * line.spacingMeters());

            Vector2 objectSource = sourceLocal.plus(offset);
            Vector2 objectDestination = destLocal.plus(offset);
            // A static object never has velocity, not even on its very first published tick.
            Vector2 velocity = movementStyle == MovementStyle.STATIC
                    ? Vector2.ZERO
                    : routeDirection.scaled(config.speedMps());

            FlightBehavior behavior = movementStyle == MovementStyle.STATIC
                    ? new StaticBehavior()
                    : new LineBounceBehavior(objectSource, objectDestination);

            T object = objectFactory.get();
            formation.add(new SimulatedObject<>(object, originLatRad, originLonRad, config.altitudeMeters(),
                    objectSource, velocity, behavior, writer));
        }
        return formation;
    }

    /**
     * N objects evenly spaced around a circle of the configured radius, centered on the origin:
     * 1 object sits at the center; 2+ objects are placed {@code 360/n} degrees apart starting due
     * east (so 2 objects land at east/west = "right/left", 3 objects at 0/120/240 degrees, etc).
     * Each object's initial direction points radially outward; a {@link MovementStyle#MOBILE}
     * object then evolves via an independent random walk (see {@link CircleRandomWalkBehavior}), a
     * {@link MovementStyle#STATIC} object just stays put.
     */
    private static <T> List<SimulatedEntity<T>> buildCircleFormation(SimulationConfig config, CircleFormation circle,
                                                                       MovementStyle movementStyle,
                                                                       Supplier<T> objectFactory, ObjectWriter<T> writer) {
        double originLatRad = config.originLatRad();
        double originLonRad = config.originLonRad();

        int n = config.objectCount();
        List<SimulatedEntity<T>> formation = new ArrayList<>(n);
        // Shared RNG is fine: the engine only ever calls into this from a single thread, and
        // sequential draws from one Random give every object an independent turn sequence anyway.
        Random random = new Random();

        for (int i = 0; i < n; i++) {
            Vector2 position;
            Vector2 direction;

            if (n == 1) {
                // A single object has no ring to sit on, so it goes at the center. "Toward the
                // exterior" is undefined for a point at the center; we pick due east arbitrarily.
                position = Vector2.ZERO;
                direction = new Vector2(1, 0);
            } else {
                double angleRad = i * (2.0 * Math.PI / n);
                direction = new Vector2(Math.cos(angleRad), Math.sin(angleRad));
                position = direction.scaled(circle.radiusMeters());
            }

            // A static object never has velocity, not even on its very first published tick.
            Vector2 velocity = movementStyle == MovementStyle.STATIC
                    ? Vector2.ZERO
                    : direction.scaled(config.speedMps());
            FlightBehavior behavior = movementStyle == MovementStyle.STATIC
                    ? new StaticBehavior()
                    : new CircleRandomWalkBehavior(random);

            T object = objectFactory.get();
            formation.add(new SimulatedObject<>(object, originLatRad, originLonRad, config.altitudeMeters(),
                    position, velocity, behavior, writer));
        }
        return formation;
    }
}
