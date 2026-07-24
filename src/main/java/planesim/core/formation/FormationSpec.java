package planesim.core.formation;

/**
 * The shape/pattern simulated objects are arranged in (and, for mobile objects, flown in).
 * Implemented by {@link LineFormation}, {@link CircleFormation}, and {@link OrbitFormation} —
 * pick one and pass it into a simulation config.
 */
public sealed interface FormationSpec permits LineFormation, CircleFormation, OrbitFormation {
}
