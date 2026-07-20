package planesim.core.scenario;

/**
 * Signals that {@link ScenarioManager} already holds its maximum number of concurrent scenarios.
 * A plain domain error — no HTTP semantics here; {@code planesim.core.server.AbstractJsonHandler}
 * maps it to a status code (429).
 */
public final class ScenarioLimitExceededException extends RuntimeException {
    public ScenarioLimitExceededException(String message) {
        super(message);
    }
}
