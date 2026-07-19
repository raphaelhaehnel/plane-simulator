package planesim.core.behavior;

import planesim.core.geo.Vector2;

/** The new position and velocity (both local-frame) after one {@link FlightBehavior} step. */
public record StepResult(Vector2 position, Vector2 velocity) {
}
