package planesim.core.scenario;

/** The kind of object a scenario simulates. */
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
