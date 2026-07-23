package planesim.core.scenario;

/**
 * The kind of object a scenario simulates. Its {@link ScenarioCategory} is what the HTTP layer
 * dispatches on to decide between a geographic and a non-geographic config. The network topic is
 * no longer tied to the type — it is chosen per scenario via the {@code topicName} on
 * {@code POST /createScenario}.
 */
public enum ScenarioType {
    PLANE(ScenarioCategory.GEOGRAPHIC),
    RADAR(ScenarioCategory.GEOGRAPHIC),
    WEATHER(ScenarioCategory.NON_GEOGRAPHIC);

    private final ScenarioCategory category;

    ScenarioType(ScenarioCategory category) {
        this.category = category;
    }

    public ScenarioCategory category() {
        return category;
    }
}
