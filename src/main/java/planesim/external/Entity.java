package planesim.external;

/**
 * PLACEHOLDER ONLY.
 * <p>
 * Common supertype of every externally-provided simulated object ({@link Plane}, {@link Radar},
 * {@link Weather}, ...). It exists so {@link NetworkManager#send(Entity, String)} can take one
 * parameter type instead of one overload per object type. Delete this file and use your actual
 * import instead.
 * <p>
 * Whatever fields the real {@code Entity} declares are <b>the network layer's to populate at send
 * time</b> (see {@link NetworkWriter}), never {@code planesim.core}'s — the simulation only ever
 * fills in the subclass's own fields. {@code timestamp} below is a stand-in for that whole
 * category; the exact set doesn't matter to this module, only that {@code core} leaves it alone.
 * {@code planesim.core.scenario.NonGeoFieldReader} skips everything declared here for the same
 * reason: these are transport metadata, not part of the simulated reading.
 */
public abstract class Entity {

    /** Set by the network layer when the entity is sent. */
    public long timestamp;
}
