package planesim.core.scenario;

/**
 * Whether a {@link ScenarioType} has a position (geographic — planes, radars) or not
 * (non-geographic — weather). This is the one axis that matters for request validation and DTO
 * shaping (see {@code planesim.core.server.RequestMapper}); it's deliberately not per-object-type.
 */
public enum ScenarioCategory {
    GEOGRAPHIC,
    NON_GEOGRAPHIC
}
