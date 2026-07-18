package planesim;

/**
 * Conversions between WGS84 lat/lon (radians) and a local flat "simulation frame" (meters,
 * x = east, y = north), plus the heading calculation.
 *
 * <p>We do all kinematics (position updates, formation offsets) in the local flat frame because
 * vx/vy are linear speeds (m/s) and lat/lon are angular — they can't be combined directly.
 * The projection used here is a simple equirectangular ("plate carr\u00e9e") projection around a
 * fixed origin. It's accurate for regional/continental distances (the error grows the further
 * you get from the origin and the higher the latitude); it is not a substitute for full
 * great-circle navigation over intercontinental distances.
 */
public final class GeoMath {

    /** Mean Earth radius, spherical approximation (meters). Good enough for this simulation. */
    public static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoMath() {
    }

    /**
     * Projects a lat/lon (radians) into the local flat frame centered at (originLat, originLon).
     */
    public static Vector2 toLocal(double latRad, double lonRad, double originLatRad, double originLonRad) {
        double x = EARTH_RADIUS_M * (lonRad - originLonRad) * Math.cos(originLatRad);
        double y = EARTH_RADIUS_M * (latRad - originLatRad);
        return new Vector2(x, y);
    }

    /**
     * Inverse of {@link #toLocal}: converts a local-frame point back to lat/lon radians.
     *
     * @return a 2-element array: {latitudeRad, longitudeRad}
     */
    public static double[] toLatLon(Vector2 local, double originLatRad, double originLonRad) {
        double lat = originLatRad + local.y() / EARTH_RADIUS_M;
        double lon = originLonRad + local.x() / (EARTH_RADIUS_M * Math.cos(originLatRad));
        return new double[]{lat, lon};
    }

    /**
     * Compass heading in degrees [0, 360) for a local-frame direction/velocity vector.
     * 0 = north, 90 = east, matching standard aviation heading convention.
     */
    public static double headingDegrees(Vector2 v) {
        double deg = Math.toDegrees(Math.atan2(v.x(), v.y()));
        return (deg + 360.0) % 360.0;
    }
}
