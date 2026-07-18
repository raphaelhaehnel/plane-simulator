package planesim.core;

import planesim.api.Plane;
import planesim.behavior.FlightBehavior;
import planesim.behavior.StepResult;
import planesim.geo.GeoMath;
import planesim.geo.Vector2;

/**
 * Internal runtime state for one plane. Physics ("ground truth") is kept entirely in the local
 * flat frame (meters); the externally-provided {@link Plane} object is only populated right
 * before it's sent, by projecting the local position back to lat/lon radians. How the plane
 * actually moves each tick is delegated to its {@link FlightBehavior}.
 *
 * Not thread-safe by design — {@link SimulationEngine} only ever touches these from its single
 * scheduler thread.
 */
final class SimulatedPlane {

    private final Plane plane;
    private final double originLatRad;
    private final double originLonRad;
    private final double altitudeMeters;
    private final FlightBehavior behavior;

    private Vector2 position;
    private Vector2 velocity;

    SimulatedPlane(Plane plane,
                   double originLatRad,
                   double originLonRad,
                   double altitudeMeters,
                   Vector2 startPosition,
                   Vector2 startVelocity,
                   FlightBehavior behavior) {
        this.plane = plane;
        this.originLatRad = originLatRad;
        this.originLonRad = originLonRad;
        this.altitudeMeters = altitudeMeters;
        this.position = startPosition;
        this.velocity = startVelocity;
        this.behavior = behavior;
    }

    /** Projects current local state into the external Plane object, ready for networkAPI.send(). */
    void writeToPlane() {
        double[] latLon = GeoMath.toLatLon(position, originLatRad, originLonRad);
        plane.latitude = latLon[0];
        plane.longitude = latLon[1];
        plane.altitude = altitudeMeters;
        plane.vx = velocity.x();
        plane.vy = velocity.y();
        // Heading is derived purely for the UI; it plays no part in the physics.
        plane.heading = GeoMath.headingDegrees(velocity);
    }

    Plane plane() {
        return plane;
    }

    void advance(double dtSeconds) {
        StepResult result = behavior.step(position, velocity, dtSeconds);
        position = result.position();
        velocity = result.velocity();
    }
}
