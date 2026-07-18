package planesim.api;

/**
 * PLACEHOLDER ONLY.
 *
 * This stands in for your real, externally-provided Plane class so this module compiles
 * standalone. Delete this file and use your actual import instead — the rest of the code only
 * relies on these five public fields existing with these names/types, so as long as your real
 * class exposes them (as fields or as getters/setters of the same name), nothing else needs
 * to change.
 */
public class Plane {
    public double altitude;
    public double latitude;   // radians
    public double longitude;  // radians
    public double vx;         // m/s, east component
    public double vy;         // m/s, north component
    public double heading;    // degrees, [0, 360), UI-only, not used in kinematics
}
