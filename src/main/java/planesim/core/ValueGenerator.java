package planesim.core;

/**
 * Strategy for producing each tick's field values directly onto a non-geographic external object
 * of type {@code T} — the value-only counterpart to {@link ObjectWriter}, which projects position
 * instead. See {@link ValueGenerators} for the predefined ones.
 */
public interface ValueGenerator<T> {
    void generate(T target);
}
