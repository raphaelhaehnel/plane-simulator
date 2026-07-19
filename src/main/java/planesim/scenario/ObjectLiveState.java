package planesim.scenario;

/**
 * Latest published state of one object within a scenario, as last observed via {@link
 * ScenarioNetworkApi}. {@code headingDeg} is always {@code 0.0} for a static object (e.g. a
 * radar), since direction is meaningless for something that never moves.
 */
public record ObjectLiveState(int index, double latRad, double lonRad, double headingDeg) {
}
