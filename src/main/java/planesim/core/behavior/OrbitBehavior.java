package planesim.core.behavior;

import planesim.core.geo.Vector2;

/**
 * Flies a perfect circle around the local-frame origin (where the orbit formation is centered),
 * clockwise as seen on a north-up map (east point → south → west → north), at constant speed.
 * The tangential speed is {@code speedMps}, so the angular speed is {@code speedMps / radius}.
 *
 * <p>The behavior tracks its own polar angle and places the object exactly on the ring every tick
 * (position = radius * (cos a, sin a), velocity = tangent), rather than integrating the velocity —
 * integrating a rotating velocity would spiral outward, drifting off the circle a little more each
 * tick. Clockwise means the angle <em>decreases</em> over time (angles are standard math
 * convention: counterclockwise from east), so the tangent at angle {@code a} is
 * {@code (sin a, -cos a)}.
 */
public final class OrbitBehavior implements FlightBehavior {

    private final double radiusMeters;
    private final double speedMps;
    private double angleRad;

    /**
     * @param initialAngleRad the polar angle this object was placed at by the formation, so the
     *                        first step continues from exactly where placement put it
     */
    public OrbitBehavior(double radiusMeters, double speedMps, double initialAngleRad) {
        this.radiusMeters = radiusMeters;
        this.speedMps = speedMps;
        this.angleRad = initialAngleRad;
    }

    @Override
    public StepResult step(Vector2 position, Vector2 velocity, double dtSeconds) {
        angleRad -= (speedMps / radiusMeters) * dtSeconds;
        Vector2 newPosition = new Vector2(Math.cos(angleRad), Math.sin(angleRad)).scaled(radiusMeters);
        Vector2 newVelocity = new Vector2(Math.sin(angleRad), -Math.cos(angleRad)).scaled(speedMps);
        return new StepResult(newPosition, newVelocity);
    }
}
