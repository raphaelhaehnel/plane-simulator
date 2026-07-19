package planesim.core;

/**
 * One simulated "thing" that can project/generate its current state onto an external object of
 * type {@code T} and advance itself in time. {@link SimulatedObject} is the geographic
 * implementation (planes, radars, ... — anything with a formation-assigned position);
 * {@link SimulatedValue} is the non-geographic one (e.g. weather — no position, just regenerated
 * field values). {@link SimulationEngine} only ever depends on this interface, not on which kind
 * of object it's driving.
 */
interface SimulatedEntity<T> {

    /** Projects/generates current state onto the external object and returns it, ready to send. */
    T writeToExternal();

    void advance(double dtSeconds);
}
