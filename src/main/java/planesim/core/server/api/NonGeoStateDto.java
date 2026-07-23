package planesim.core.server.api;

import java.util.Map;

/**
 * Live reading of one non-geographic simulated object (weather, ...), embedded in {@link
 * ScenarioDto}. Has no coordinates. {@code fields} is a generic name-&gt;value map (captured via
 * reflection server-side, see {@code planesim.core.scenario.NonGeoFieldReader}) rather than fixed
 * columns, so this same DTO shape works for any non-geographic object type without changes.
 */
public class NonGeoStateDto {
    public int index;
    public Map<String, Object> fields;
}
