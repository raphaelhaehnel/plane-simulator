package planesim.core.scenario;

/**
 * The kind of object a scenario simulates, and the network topic its objects are published on —
 * a scenario is always homogeneous, so one topic per type is all that's needed.
 */
public enum ScenarioType {
    PLANE(ScenarioCategory.GEOGRAPHIC, "planes"),
    RADAR(ScenarioCategory.GEOGRAPHIC, "radars"),
    WEATHER(ScenarioCategory.NON_GEOGRAPHIC, "weather");

    private final ScenarioCategory category;
    private final String topicName;

    ScenarioType(ScenarioCategory category, String topicName) {
        this.category = category;
        this.topicName = topicName;
    }

    public ScenarioCategory category() {
        return category;
    }

    /** The {@code planesim.external.NetworkManager} topic this type's objects are sent on. */
    public String topicName() {
        return topicName;
    }
}
