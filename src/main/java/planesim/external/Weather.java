package planesim.external;

/**
 * PLACEHOLDER ONLY.
 *
 * This stands in for your real, externally-provided Weather class so this module compiles
 * standalone. Delete this file and use your actual import instead — the rest of the code only
 * relies on these three public fields existing with these names/types, so as long as your real
 * class exposes them (as fields or as getters/setters of the same name), nothing else needs
 * to change. Unlike {@link Plane}/{@link Radar}, a weather reading has no coordinates at all —
 * it isn't a positioned object, can't be placed by a formation, and can't be shown on a map.
 */
public class Weather {
    public double windVelocity;  // m/s
    public float temperature;    // degrees Celsius
    public boolean isSunny;

    @Override
    public String toString() {
        return "windVelocity=" + windVelocity + ", temperature=" + temperature + ", isSunny=" + isSunny;
    }
}
