package planesim;

/**
 * The shape/pattern the planes are arranged and flown in. Implemented by {@link LineFormation}
 * and {@link CircleFormation} — pick one and pass it into {@link SimulationConfig}.
 */
public sealed interface FormationSpec permits LineFormation, CircleFormation {
}
