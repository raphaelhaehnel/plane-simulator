package planesim.scenario;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads every public instance field of a non-geographic external object (e.g. {@link
 * planesim.api.Weather}) into a name-&gt;value map, so {@link ScenarioNetworkApi} can capture a
 * live-state snapshot generically — without needing a hand-written record/DTO pair for every new
 * non-geographic object type. Geographic objects don't use this: their live state ({@link
 * GeoLiveState}) is always the same lat/lon/heading shape, so it's captured directly instead of
 * reflectively.
 */
final class NonGeoFieldReader {

    static Map<String, Object> readFields(Object target) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Field field : target.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
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

    private NonGeoFieldReader() {
    }
}
