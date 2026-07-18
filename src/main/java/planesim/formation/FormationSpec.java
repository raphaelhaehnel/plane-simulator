package planesim.formation;

/**
 * The shape/pattern the planes are arranged and flown in. Implemented by {@link LineFormation}
 * and {@link CircleFormation} — pick one and pass it into a simulation config.
 */
public sealed interface FormationSpec permits LineFormation, CircleFormation {
}
