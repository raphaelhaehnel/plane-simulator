package planesim.external;

/**
 * PLACEHOLDER ONLY.
 * <p>
 * Stands in for your real network topic (channel/queue/DDS topic/...) so this module compiles
 * standalone. {@link NetworkManager} only ever looks one up by name and hands it to
 * {@link NetworkWriter}; nothing in {@code planesim.core} ever touches a {@code Topic} directly.
 * Delete this file and use your actual import instead.
 */
public final class Topic {

    public final String name;

    public Topic(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
