package planesim.api;

/**
 * PLACEHOLDER ONLY.
 * <p>
 * Stands in for your real, externally-provided networkAPI so this module compiles standalone.
 * Delete this file and use your actual import instead. One overload per simulated object type.
 */
public interface NetworkApi {
    void send(Plane plane);
    void send(Radar radar);
    void send(Weather weather);
}
