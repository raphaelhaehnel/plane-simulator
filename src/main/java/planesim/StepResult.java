package planesim;

/** The new position and velocity (both local-frame) after one {@link FlightBehavior} step. */
record StepResult(Vector2 position, Vector2 velocity) {
}
