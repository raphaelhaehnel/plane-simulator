package planesim.core.scenario;

/**
 * Latest published state of one geographic object (plane, radar, ...) within a scenario, as last
 * observed via {@link ScenarioNetworkApi}. {@code headingDeg} is always {@code 0.0} for a static
 * object (e.g. a radar), since direction is meaningless for something that never moves. The
 * geographic counterpart to {@link NonGeoLiveState}.
 */
public record GeoLiveState(int index, double latRad, double lonRad, double headingDeg) {
}
