package planesim.core.engine;

import planesim.core.geo.Vector2;

/**
 * Strategy for projecting local-frame simulation state (meters/m-per-second) onto an
 * externally-provided object of type {@code T} (radians/meters), the way {@link
 * planesim.core.geo.GeoMath} converts coordinates and this then applies them to the object's fields.
 * See {@link ObjectWriters} for the predefined writers for {@link planesim.external.Plane} and
 * {@link planesim.external.Radar}.
 */
public interface ObjectWriter<T> {
    void write(T target, double latRad, double lonRad, double altitudeMeters, Vector2 velocity);
}
