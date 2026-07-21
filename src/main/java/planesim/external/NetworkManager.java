package planesim.external;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PLACEHOLDER ONLY.
 * <p>
 * Stands in for your real, externally-provided network manager so this module compiles standalone.
 * Delete this file and use your actual import instead — {@code planesim.core} only relies on the
 * shape of this class: a single instance, obtained once at startup, with one
 * {@link #send(Entity, String)} method taking any {@link Entity} plus the name of the topic to
 * publish it on.
 * <p>
 * <b>Singleton, built once.</b> The instance is created through {@link #builder()} exactly once per
 * JVM (from the application's {@code main}), and read everywhere else through
 * {@link #getInstance()}. The builder takes one input — a {@link NetworkConfiguration} — and from
 * it instantiates the collaborators: the {@link NetworkWriter}(s) and the whole
 * {@code name -> }{@link Topic} map. Every topic is opened up front, at build time; {@link #send}
 * only ever picks an already-open one by name.
 */
public final class NetworkManager {

    private static volatile NetworkManager instance;

    private final NetworkWriter writer;
    private final Map<String, Topic> topics;

    private NetworkManager(NetworkWriter writer, Map<String, Topic> topics) {
        this.writer = writer;
        this.topics = topics;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The single instance.
     *
     * @throws IllegalStateException if {@link Builder#build()} hasn't run yet — fail fast at the
     *                               first send rather than silently publishing nowhere
     */
    public static NetworkManager getInstance() {
        NetworkManager current = instance;
        if (current == null) {
            throw new IllegalStateException(
                    "NetworkManager has not been built yet - call NetworkManager.builder()...build() once at startup");
        }
        return current;
    }

    /**
     * Publishes one entity on the named topic.
     *
     * @throws IllegalArgumentException if no topic was registered under {@code topicName}
     */
    public void send(Entity entity, String topicName) {
        Topic topic = topics.get(topicName);
        if (topic == null) {
            throw new IllegalArgumentException("Unknown topic: " + topicName);
        }
        writer.write(entity, topic);
    }

    /** Instantiates the manager's collaborators from the configuration, and installs the singleton. */
    public static final class Builder {

        private NetworkConfiguration configuration;

        private Builder() {
        }

        public Builder configuration(NetworkConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        /**
         * @throws IllegalStateException if no configuration was supplied, or if the singleton was
         *                               already built — it is meant to be created exactly once, at
         *                               startup
         */
        public NetworkManager build() {
            if (configuration == null) {
                throw new IllegalStateException(
                        "NetworkManager needs a configuration - call configuration(...) before build()");
            }
            // TODO(real impl): open the real writer(s) and topics from the configuration here.
            Map<String, Topic> topics = new LinkedHashMap<>();
            for (String topicName : configuration.topicNames) {
                topics.put(topicName, new Topic(topicName));
            }
            return install(new NetworkManager(new NetworkWriter(), Map.copyOf(topics)));
        }
    }

    private static synchronized NetworkManager install(NetworkManager built) {
        if (instance != null) {
            throw new IllegalStateException("NetworkManager has already been built");
        }
        instance = built;
        return instance;
    }
}
