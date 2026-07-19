package planesim.scenario;

/** Latest published state of one plane within a scenario, as last observed via {@link ScenarioNetworkApi}. */
public record PlaneLiveState(int index, double latRad, double lonRad, double headingDeg) {
}
