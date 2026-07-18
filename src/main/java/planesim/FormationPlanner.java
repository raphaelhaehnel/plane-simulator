package planesim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Builds the initial formation from a {@link SimulationConfig}, dispatching on which
 * {@link FormationSpec} was chosen.
 */
public final class FormationPlanner {

    private FormationPlanner() {
    }

    /**
     * @param planeFactory supplies one new externally-provided Plane instance per simulated
     *                     plane (e.g. {@code Plane::new}, or a lambda that also assigns an id)
     */
    static List<SimulatedPlane> buildFormation(SimulationConfig config, Supplier<Plane> planeFactory) {
        if (config.formation() instanceof LineFormation line) {
            return buildLineFormation(config, line, planeFactory);
        }
        if (config.formation() instanceof CircleFormation circle) {
            return buildCircleFormation(config, circle, planeFactory);
        }
        throw new IllegalStateException("Unhandled formation type: " + config.formation());
    }

    /**
     * N planes flying parallel lines between source and destination, evenly spaced along the
     * axis perpendicular to the route, centered on the source/destination centerline. Each plane
     * gets its own source and destination point — both the config's source and destination
     * shifted by the same perpendicular offset — so the planes fly parallel straight lines side
     * by side rather than converging onto a single destination point.
     */
    private static List<SimulatedPlane> buildLineFormation(SimulationConfig config, LineFormation line,
                                                             Supplier<Plane> planeFactory) {
        double originLatRad = config.originLatRad();
        double originLonRad = config.originLonRad();

        Vector2 sourceLocal = Vector2.ZERO;
        Vector2 destLocal = GeoMath.toLocal(line.destLatRad(), line.destLonRad(), originLatRad, originLonRad);

        Vector2 routeDirection = destLocal.minus(sourceLocal).normalized();
        Vector2 perpendicularAxis = routeDirection.perpendicular();

        int n = config.planeCount();
        List<SimulatedPlane> formation = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            // Centered arrangement: e.g. for n=5 the offset indices are -2,-1,0,1,2.
            double offsetIndex = i - (n - 1) / 2.0;
            Vector2 offset = perpendicularAxis.scaled(offsetIndex * line.spacingMeters());

            Vector2 planeSource = sourceLocal.plus(offset);
            Vector2 planeDestination = destLocal.plus(offset);
            Vector2 velocity = routeDirection.scaled(config.speedMps());

            FlightBehavior behavior = new LineBounceBehavior(planeSource, planeDestination);
            Plane plane = planeFactory.get();
            formation.add(new SimulatedPlane(plane, originLatRad, originLonRad, config.altitudeMeters(),
                    planeSource, velocity, behavior));
        }
        return formation;
    }

    /**
     * N planes evenly spaced around a circle of the configured radius, centered on the origin:
     * 1 plane sits at the center; 2+ planes are placed {@code 360/n} degrees apart starting due
     * east (so 2 planes land at east/west = "right/left", 3 planes at 0/120/240 degrees, etc).
     * Each plane's initial direction points radially outward; afterwards it evolves via an
     * independent random walk (see {@link CircleRandomWalkBehavior}).
     */
    private static List<SimulatedPlane> buildCircleFormation(SimulationConfig config, CircleFormation circle,
                                                               Supplier<Plane> planeFactory) {
        double originLatRad = config.originLatRad();
        double originLonRad = config.originLonRad();

        int n = config.planeCount();
        List<SimulatedPlane> formation = new ArrayList<>(n);
        // Shared RNG is fine: the engine only ever calls into this from a single thread, and
        // sequential draws from one Random give every plane an independent turn sequence anyway.
        Random random = new Random();

        for (int i = 0; i < n; i++) {
            Vector2 position;
            Vector2 direction;

            if (n == 1) {
                // A single plane has no ring to sit on, so it goes at the center. "Toward the
                // exterior" is undefined for a point at the center; we pick due east arbitrarily.
                position = Vector2.ZERO;
                direction = new Vector2(1, 0);
            } else {
                double angleRad = i * (2.0 * Math.PI / n);
                direction = new Vector2(Math.cos(angleRad), Math.sin(angleRad));
                position = direction.scaled(circle.radiusMeters());
            }

            Vector2 velocity = direction.scaled(config.speedMps());
            FlightBehavior behavior = new CircleRandomWalkBehavior(random);

            Plane plane = planeFactory.get();
            formation.add(new SimulatedPlane(plane, originLatRad, originLonRad, config.altitudeMeters(),
                    position, velocity, behavior));
        }
        return formation;
    }
}
