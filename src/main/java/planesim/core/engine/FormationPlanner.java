package planesim.core.engine;

import planesim.core.behavior.CircleRandomWalkBehavior;
import planesim.core.behavior.FlightBehavior;
import planesim.core.behavior.LineBounceBehavior;
import planesim.core.behavior.OrbitBehavior;
import planesim.core.behavior.StaticBehavior;
import planesim.core.formation.CircleFormation;
import planesim.core.formation.LineFormation;
import planesim.core.formation.OrbitFormation;
import planesim.core.formation.ScatterFormation;
import planesim.core.formation.WedgeFormation;
import planesim.core.geo.GeoMath;
import planesim.core.geo.Vector2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Builds the initial formation from a {@link GeoScenarioConfig}, dispatching on which
 * {@link planesim.core.formation.FormationSpec} was chosen. Type-agnostic over the external object
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
    static <T> List<SimulatedEntity<T>> buildFormation(GeoScenarioConfig config, MovementStyle movementStyle,
                                                         Supplier<T> objectFactory, ObjectWriter<T> writer) {
        if (config.formation() instanceof LineFormation line) {
            return buildLineFormation(config, line, movementStyle, objectFactory, writer);
        }
        if (config.formation() instanceof CircleFormation circle) {
            return buildCircleFormation(config, circle, movementStyle, objectFactory, writer);
        }
        if (config.formation() instanceof OrbitFormation orbit) {
            return buildOrbitFormation(config, orbit, movementStyle, objectFactory, writer);
        }
        if (config.formation() instanceof WedgeFormation wedge) {
            return buildWedgeFormation(config, wedge, movementStyle, objectFactory, writer);
        }
        if (config.formation() instanceof ScatterFormation scatter) {
            return buildScatterFormation(config, scatter, movementStyle, objectFactory, writer);
        }
        throw new IllegalStateException("Unhandled formation type: " + config.formation());
    }

    /**
     * The initial velocity a placed object publishes on its very first tick: a {@link
     * MovementStyle#STATIC} object never has velocity (not even before its {@link StaticBehavior}
     * has run once), a {@link MovementStyle#MOBILE} object keeps the formation's placement velocity.
     * A {@code switch} expression with no {@code default}, so adding a {@link MovementStyle} constant
     * is a compile error here rather than being silently treated as {@code MOBILE}.
     */
    private static Vector2 initialVelocity(MovementStyle movementStyle, Vector2 mobileVelocity) {
        return switch (movementStyle) {
            case STATIC -> Vector2.ZERO;
            case MOBILE -> mobileVelocity;
        };
    }

    /**
     * The per-tick behavior a placed object gets: a {@link MovementStyle#STATIC} object never moves
     * ({@link StaticBehavior}, regardless of the formation's natural motion), a {@link
     * MovementStyle#MOBILE} object flies the formation's own behavior. Exhaustive {@code switch}
     * (no {@code default}) for the same compile-time safety as {@link #initialVelocity}.
     */
    private static FlightBehavior behaviorFor(MovementStyle movementStyle, FlightBehavior mobileBehavior) {
        return switch (movementStyle) {
            case STATIC -> new StaticBehavior();
            case MOBILE -> mobileBehavior;
        };
    }

    /**
     * N objects placed along parallel lines between source and destination, evenly spaced along
     * the axis perpendicular to the route, centered on the source/destination centerline. Each
     * object gets its own source and destination point — both the config's source and destination
     * shifted by the same perpendicular offset — so a {@link MovementStyle#MOBILE} object flies its
     * own parallel line rather than converging onto a single destination point; a
     * {@link MovementStyle#STATIC} object just stays at its source point.
     */
    private static <T> List<SimulatedEntity<T>> buildLineFormation(GeoScenarioConfig config, LineFormation line,
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

            Vector2 velocity = initialVelocity(movementStyle, routeDirection.scaled(config.speedMps()));
            FlightBehavior behavior = behaviorFor(movementStyle, new LineBounceBehavior(objectSource, objectDestination));

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
    private static <T> List<SimulatedEntity<T>> buildCircleFormation(GeoScenarioConfig config, CircleFormation circle,
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

            Vector2 velocity = initialVelocity(movementStyle, direction.scaled(config.speedMps()));
            FlightBehavior behavior = behaviorFor(movementStyle, new CircleRandomWalkBehavior(random));

            T object = objectFactory.get();
            formation.add(new SimulatedObject<>(object, originLatRad, originLonRad, config.altitudeMeters(),
                    position, velocity, behavior, writer));
        }
        return formation;
    }

    /**
     * N objects evenly spaced around a circle of the configured radius, centered on the origin —
     * same placement as {@link #buildCircleFormation}, except a single object goes <em>on</em> the
     * ring (due east), not at the center, since an orbiting object needs a ring to fly. A
     * {@link MovementStyle#MOBILE} object flies along the circle in the tangent direction,
     * clockwise, forever (see {@link OrbitBehavior}); a {@link MovementStyle#STATIC} object just
     * stays put. The initial velocity already points along the clockwise tangent so the very first
     * published tick matches the motion that follows.
     */
    private static <T> List<SimulatedEntity<T>> buildOrbitFormation(GeoScenarioConfig config, OrbitFormation orbit,
                                                                      MovementStyle movementStyle,
                                                                      Supplier<T> objectFactory, ObjectWriter<T> writer) {
        double originLatRad = config.originLatRad();
        double originLonRad = config.originLonRad();

        int n = config.objectCount();
        List<SimulatedEntity<T>> formation = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            double angleRad = i * (2.0 * Math.PI / n);
            Vector2 position = new Vector2(Math.cos(angleRad), Math.sin(angleRad)).scaled(orbit.radiusMeters());
            // Clockwise tangent at angle a is (sin a, -cos a) — see OrbitBehavior.
            Vector2 tangent = new Vector2(Math.sin(angleRad), -Math.cos(angleRad));

            Vector2 velocity = initialVelocity(movementStyle, tangent.scaled(config.speedMps()));
            FlightBehavior behavior = behaviorFor(movementStyle,
                    new OrbitBehavior(orbit.radiusMeters(), config.speedMps(), angleRad));

            T object = objectFactory.get();
            formation.add(new SimulatedObject<>(object, originLatRad, originLonRad, config.altitudeMeters(),
                    position, velocity, behavior, writer));
        }
        return formation;
    }

    /**
     * N objects in a flying-V: object 0 at the apex, the rest trailing back-and-outward along two
     * symmetric arms (alternating side, one {@code spacingMeters} further back each pair). The apex
     * points along the source-&gt;destination route; each object flies its own parallel copy of that
     * route ({@link MovementStyle#MOBILE}) so the whole V holds shape and shuttles back and forth
     * (same trick as {@link #buildLineFormation}), or just holds its point ({@link MovementStyle#STATIC}).
     */
    private static <T> List<SimulatedEntity<T>> buildWedgeFormation(GeoScenarioConfig config, WedgeFormation wedge,
                                                                      MovementStyle movementStyle,
                                                                      Supplier<T> objectFactory, ObjectWriter<T> writer) {
        double originLatRad = config.originLatRad();
        double originLonRad = config.originLonRad();

        Vector2 destLocal = GeoMath.toLocal(wedge.destLatRad(), wedge.destLonRad(), originLatRad, originLonRad);
        Vector2 routeDirection = destLocal.normalized();
        Vector2 back = routeDirection.negated();
        Vector2 perpendicularAxis = routeDirection.perpendicular();

        double halfAngle = wedge.apexAngleRad() / 2.0;
        Vector2 leftArm = back.scaled(Math.cos(halfAngle)).plus(perpendicularAxis.scaled(Math.sin(halfAngle)));
        Vector2 rightArm = back.scaled(Math.cos(halfAngle)).minus(perpendicularAxis.scaled(Math.sin(halfAngle)));

        int n = config.objectCount();
        List<SimulatedEntity<T>> formation = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            // Apex at i=0; then alternate left/right arm, stepping one spacing further back each pair.
            Vector2 offset = i == 0
                    ? Vector2.ZERO
                    : (i % 2 == 1 ? leftArm : rightArm).scaled(((i + 1) / 2) * wedge.spacingMeters());

            Vector2 objectSource = offset;
            Vector2 objectDestination = destLocal.plus(offset);

            Vector2 velocity = initialVelocity(movementStyle, routeDirection.scaled(config.speedMps()));
            FlightBehavior behavior = behaviorFor(movementStyle, new LineBounceBehavior(objectSource, objectDestination));

            T object = objectFactory.get();
            formation.add(new SimulatedObject<>(object, originLatRad, originLonRad, config.altitudeMeters(),
                    objectSource, velocity, behavior, writer));
        }
        return formation;
    }

    /**
     * N objects placed at uniformly-random positions inside a disk of the configured radius, centered
     * on the origin (uniform over area via {@code r = R * sqrt(u)}, so no clustering toward the
     * center). Each starts with a random heading; a {@link MovementStyle#MOBILE} object then wanders
     * independently via random walk (see {@link CircleRandomWalkBehavior}), a {@link MovementStyle#STATIC}
     * object just stays put.
     */
    private static <T> List<SimulatedEntity<T>> buildScatterFormation(GeoScenarioConfig config, ScatterFormation scatter,
                                                                        MovementStyle movementStyle,
                                                                        Supplier<T> objectFactory, ObjectWriter<T> writer) {
        double originLatRad = config.originLatRad();
        double originLonRad = config.originLonRad();

        int n = config.objectCount();
        List<SimulatedEntity<T>> formation = new ArrayList<>(n);
        // Shared RNG is fine: the engine only ever calls into this from a single thread, and
        // sequential draws from one Random give every object an independent placement/turn sequence.
        Random random = new Random();

        for (int i = 0; i < n; i++) {
            double radius = scatter.radiusMeters() * Math.sqrt(random.nextDouble());
            double placementAngle = 2.0 * Math.PI * random.nextDouble();
            Vector2 position = new Vector2(radius * Math.cos(placementAngle), radius * Math.sin(placementAngle));

            double headingRad = 2.0 * Math.PI * random.nextDouble();
            Vector2 direction = new Vector2(Math.cos(headingRad), Math.sin(headingRad));

            Vector2 velocity = initialVelocity(movementStyle, direction.scaled(config.speedMps()));
            FlightBehavior behavior = behaviorFor(movementStyle, new CircleRandomWalkBehavior(random));

            T object = objectFactory.get();
            formation.add(new SimulatedObject<>(object, originLatRad, originLonRad, config.altitudeMeters(),
                    position, velocity, behavior, writer));
        }
        return formation;
    }
}
