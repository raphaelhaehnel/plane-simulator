package planesim.core.engine;

/**
 * Whether a formation's objects fly their formation's natural movement pattern ({@link #MOBILE} —
 * e.g. a plane bouncing along a line, or wandering around a circle), or stay fixed in place at
 * wherever the formation placed them ({@link #STATIC} — e.g. a radar). Orthogonal to the
 * formation's shape (line/circle): the shape only ever decides initial placement, this decides
 * what happens afterward. See {@code FormationPlanner}.
 */
public enum MovementStyle {
    MOBILE,
    STATIC
}
