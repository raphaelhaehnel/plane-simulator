package planesim;

/**
 * Immutable 2D vector used for all local-frame math (meters, or meters/second for velocities).
 * Convention: x = east component, y = north component.
 */
public record Vector2(double x, double y) {

    public static final Vector2 ZERO = new Vector2(0, 0);

    public Vector2 plus(Vector2 other) {
        return new Vector2(x + other.x, y + other.y);
    }

    public Vector2 minus(Vector2 other) {
        return new Vector2(x - other.x, y - other.y);
    }

    public Vector2 scaled(double factor) {
        return new Vector2(x * factor, y * factor);
    }

    public Vector2 negated() {
        return new Vector2(-x, -y);
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public Vector2 normalized() {
        double len = length();
        if (len == 0.0) {
            return ZERO;
        }
        return new Vector2(x / len, y / len);
    }

    /** 90-degree rotation, used to build the perpendicular offset axis for the formation line. */
    public Vector2 perpendicular() {
        return new Vector2(-y, x);
    }

    /** Rotates this vector by the given angle (radians, counterclockwise), preserving its length exactly. */
    public Vector2 rotated(double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        return new Vector2(x * cos - y * sin, x * sin + y * cos);
    }
}
