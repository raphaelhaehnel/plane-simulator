package planesim.core.scenario;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Latest published reading of one non-geographic object (weather, ...) within a scenario, as last
 * observed via {@link ScenarioNetworkApi}. Unlike {@link GeoLiveState} — always the same
 * lat/lon/heading shape — a non-geographic object's fields are arbitrary and type-specific, so
 * they're captured generically (via reflection, see {@link NonGeoFieldReader}) into a
 * name-&gt;value map rather than a hand-written record per type. This is what lets a brand new
 * non-geographic object type (beyond weather) be added later without ever touching this class.
 */
public record NonGeoLiveState(int index, Map<String, Object> fields) {
    public NonGeoLiveState {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
