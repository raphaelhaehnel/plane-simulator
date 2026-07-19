package planesim.core.engine;

/**
 * Non-geographic counterpart to {@link SimulatedObject}: no position, no behavior, just
 * regenerates its external object's fields every tick via a {@link ValueGenerator} (e.g. a
 * weather reading). {@link #advance} is a no-op — there's no motion to advance.
 *
 * Not thread-safe by design — {@link SimulationEngine} only ever touches these from its single
 * scheduler thread.
 */
final class SimulatedValue<T> implements SimulatedEntity<T> {

    private final T external;
    private final ValueGenerator<T> generator;

    SimulatedValue(T external, ValueGenerator<T> generator) {
        this.external = external;
        this.generator = generator;
    }

    @Override
    public T writeToExternal() {
        generator.generate(external);
        return external;
    }

    @Override
    public void advance(double dtSeconds) {
        // No motion to advance for a non-geographic object.
    }
}
