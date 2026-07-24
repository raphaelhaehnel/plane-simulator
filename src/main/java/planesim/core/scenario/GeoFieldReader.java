package planesim.core.scenario;

import planesim.external.Entity;

import java.lang.reflect.Field;

/**
 * Reads a geographic external object's {@code latitude}/{@code longitude}/{@code heading} public
 * fields into a {@link GeoLiveState}, so {@link ScenarioPublisher} can capture a live-state
 * snapshot generically — without needing a hand-written {@code send(...)} overload for every new
 * geographic object type. The field names are not a local invention: they're the documented
 * integration contract every geographic external type must satisfy anyway (see the placeholder
 * javadoc on {@link planesim.external.Plane}/{@link planesim.external.Radar} and CLAUDE.md).
 * {@code heading} is optional — a static object like a radar has no heading field, and its
 * live-state heading is {@code 0.0}, since direction is meaningless for something that never
 * moves. The geographic counterpart to {@link NonGeoFieldReader}.
 */
final class GeoFieldReader {

    static GeoLiveState readState(int index, Entity target) {
        double latRad = requiredDouble(target, "latitude");
        double lonRad = requiredDouble(target, "longitude");
        double headingDeg = optionalDouble(target, "heading", 0.0);
        return new GeoLiveState(index, latRad, lonRad, headingDeg);
    }

    private static double requiredDouble(Entity target, String fieldName) {
        Field field;
        try {
            field = target.getClass().getField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Geographic object " + target.getClass().getName()
                    + " has no public '" + fieldName + "' field — every geographic external type must expose one", e);
        }
        return doubleValue(field, target);
    }

    private static double optionalDouble(Entity target, String fieldName, double fallback) {
        Field field;
        try {
            field = target.getClass().getField(fieldName);
        } catch (NoSuchFieldException e) {
            return fallback;
        }
        return doubleValue(field, target);
    }

    private static double doubleValue(Field field, Entity target) {
        try {
            return ((Number) field.get(target)).doubleValue();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot read public field " + field.getName() + " on " + target.getClass(), e);
        }
    }

    private GeoFieldReader() {
    }
}
