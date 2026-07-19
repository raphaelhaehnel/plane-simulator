package planesim.core;

import planesim.behavior.FlightBehavior;
import planesim.behavior.StepResult;
import planesim.geo.GeoMath;
import planesim.geo.Vector2;

/**
 * Internal runtime state for one simulated object of external type {@code T} (e.g. {@link
 * planesim.api.Plane}, {@link planesim.api.Radar}). Physics ("ground truth") is kept entirely in
 * the local flat frame (meters); the externally-provided object is only populated right before
 * it's sent, by projecting the local position back to lat/lon radians via its {@link ObjectWriter}.
 * How the object actually moves each tick is delegated to its {@link FlightBehavior}.
 *
 * Not thread-safe by design — {@link SimulationEngine} only ever touches these from its single
 * scheduler thread.
 */
final class SimulatedObject<T> {

    private final T external;
    private final double originLatRad;
    private final double originLonRad;
    private final double altitudeMeters;
    private final FlightBehavior behavior;
    private final ObjectWriter<T> writer;

    private Vector2 position;
    private Vector2 velocity;

    SimulatedObject(T external,
                     double originLatRad,
                     double originLonRad,
                     double altitudeMeters,
                     Vector2 startPosition,
                     Vector2 startVelocity,
                     FlightBehavior behavior,
                     ObjectWriter<T> writer) {
        this.external = external;
        this.originLatRad = originLatRad;
        this.originLonRad = originLonRad;
        this.altitudeMeters = altitudeMeters;
        this.position = startPosition;
        this.velocity = startVelocity;
        this.behavior = behavior;
        this.writer = writer;
    }

    /** Projects current local state into the external object, ready for networkApi.send(); returns the same state for logging. */
    ObjectState writeToExternal() {
        double[] latLon = GeoMath.toLatLon(position, originLatRad, originLonRad);
        writer.write(external, latLon[0], latLon[1], altitudeMeters, velocity);
        return new ObjectState(latLon[0], latLon[1], altitudeMeters, velocity);
    }

    T external() {
        return external;
    }

    void advance(double dtSeconds) {
        StepResult result = behavior.step(position, velocity, dtSeconds);
        position = result.position();
        velocity = result.velocity();
    }
}
