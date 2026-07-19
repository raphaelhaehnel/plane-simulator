package planesim.core;

import planesim.geo.Vector2;

/**
 * Strategy for projecting local-frame simulation state (meters/m-per-second) onto an
 * externally-provided object of type {@code T} (radians/meters), the way {@link
 * planesim.geo.GeoMath} converts coordinates and this then applies them to the object's fields.
 * See {@link ObjectWriters} for the predefined writers for {@link planesim.api.Plane} and
 * {@link planesim.api.Radar}.
 */
public interface ObjectWriter<T> {
    void write(T target, double latRad, double lonRad, double altitudeMeters, Vector2 velocity);
}
