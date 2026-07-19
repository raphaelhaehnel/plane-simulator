package planesim.api;

/**
 * PLACEHOLDER ONLY.
 *
 * This stands in for your real, externally-provided Radar class so this module compiles
 * standalone. Delete this file and use your actual import instead — the rest of the code only
 * relies on these three public fields existing with these names/types, so as long as your real
 * class exposes them (as fields or as getters/setters of the same name), nothing else needs
 * to change. Unlike {@link Plane}, a radar is static: it has no velocity/heading fields because
 * it never moves.
 */
public class Radar {
    public double altitude;
    public double latitude;   // radians
    public double longitude;  // radians
}
