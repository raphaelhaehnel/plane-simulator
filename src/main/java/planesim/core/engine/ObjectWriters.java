package planesim.core.engine;

import planesim.external.Plane;
import planesim.external.Radar;
import planesim.core.geo.GeoMath;

/** Predefined {@link ObjectWriter}s for the placeholder external object types in {@code planesim.external}. */
public final class ObjectWriters {

    public static final ObjectWriter<Plane> PLANE = (plane, latRad, lonRad, altitudeMeters, velocity) -> {
        plane.latitude = latRad;
        plane.longitude = lonRad;
        plane.altitude = altitudeMeters;
        plane.vx = velocity.x();
        plane.vy = velocity.y();
        // Heading is derived purely for the UI; it plays no part in the physics.
        plane.heading = GeoMath.headingDegrees(velocity);
    };

    /** A radar has no velocity/heading fields to write — it's static, see {@link planesim.core.behavior.StaticBehavior}. */
    public static final ObjectWriter<Radar> RADAR = (radar, latRad, lonRad, altitudeMeters, velocity) -> {
        radar.latitude = latRad;
        radar.longitude = lonRad;
        radar.altitude = altitudeMeters;
    };

    private ObjectWriters() {
    }
}
