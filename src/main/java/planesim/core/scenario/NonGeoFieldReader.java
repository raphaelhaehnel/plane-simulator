package planesim.core.scenario;

import planesim.external.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a non-geographic external object's own public instance fields (e.g. {@link
 * planesim.external.Weather}'s) into a name-&gt;value map, so {@link ScenarioPublisher} can capture a
 * live-state snapshot generically — without needing a hand-written record/DTO pair for every new
 * non-geographic object type. Geographic objects don't use this: their live state ({@link
 * GeoLiveState}) is always the same lat/lon/heading shape, so it's captured directly instead of
 * reflectively.
 */
final class NonGeoFieldReader {

    static Map<String, Object> readFields(Entity target) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Field field : target.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || isTransportMetadata(field)) {
                continue;
            }
            try {
                fields.put(field.getName(), field.get(target));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Cannot read public field " + field.getName() + " on " + target.getClass(), e);
            }
        }
        return fields;
    }

    /**
     * {@code getFields()} also returns inherited fields, but anything declared by {@link Entity}
     * (or above it) is the network layer's transport metadata — e.g. the send timestamp — which it
     * populates itself and which isn't part of the simulated reading. Without this, every field the
     * real {@code Entity} happens to declare would silently show up in {@code GET /getScenarios}.
     */
    private static boolean isTransportMetadata(Field field) {
        return field.getDeclaringClass().isAssignableFrom(Entity.class);
    }

    private NonGeoFieldReader() {
    }
}
