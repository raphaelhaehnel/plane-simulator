package planesim.core.network;

import planesim.external.Entity;
import planesim.external.Topic;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The one network access point for this JVM: a singleton built once at startup from a
 * {@link NetworkConfiguration}, then injected everywhere else (never fetched via
 * {@link #getInstance()} inside {@code planesim.core.scenario} — {@code SimulationServerApp} hands
 * it to {@code ScenarioManager}, which hands it to each {@code ScenarioPublisher}).
 *
 * <p><b>Writers are opened on demand, per scenario.</b> Unlike the old design where every topic was
 * opened up front from the configuration, topics are now supplied per scenario via the
 * {@code topicName} on {@code POST /createScenario}. {@code ScenarioManager} calls
 * {@link #openWriter(String)} when a scenario is created and {@link #closeWriter(String)} when it is
 * deleted. Several scenarios may share the same topic name: the writer is opened once and
 * <b>reference-counted</b>, so it is only actually closed once the last scenario using it is gone.
 *
 * <p>{@link #send(Entity, String)} stays type-agnostic — every external object is an {@link Entity},
 * so a new object type never adds a method here, only a new {@code Entity} subclass and a topic
 * name chosen by the caller.
 */
public final class NetworkManager {

    private static volatile NetworkManager instance;

    private final NetworkConfiguration configuration;

    /** topic name -&gt; the shared open writer and how many scenarios currently hold it. */
    private final ConcurrentHashMap<String, OpenWriter> writers = new ConcurrentHashMap<>();

    private NetworkManager(NetworkConfiguration configuration) {
        this.configuration = configuration;
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

    public NetworkConfiguration configuration() {
        return configuration;
    }

    /**
     * Opens a writer for {@code topicName}, or bumps its reference count if one is already open for
     * that name (so scenarios can share a topic). Balance every call with exactly one
     * {@link #closeWriter(String)}.
     */
    public void openWriter(String topicName) {
        if (topicName == null || topicName.isBlank()) {
            throw new IllegalArgumentException("topicName must not be blank");
        }
        writers.compute(topicName, (name, existing) -> {
            if (existing == null) {
                return new OpenWriter(new NetworkWriter(new Topic(name)));
            }
            existing.refCount++;
            return existing;
        });
    }

    /**
     * Releases one hold on {@code topicName}'s writer; the writer is actually closed only when the
     * last holder releases it. A no-op for an unknown/already-closed topic.
     */
    public void closeWriter(String topicName) {
        writers.compute(topicName, (name, existing) -> {
            if (existing == null) {
                return null;
            }
            existing.refCount--;
            if (existing.refCount <= 0) {
                existing.writer.close();
                return null; // drop the entry so the topic can be reopened later
            }
            return existing;
        });
    }

    /**
     * Publishes one entity on the named topic.
     *
     * @throws IllegalArgumentException if no writer is currently open for {@code topicName}
     */
    public void send(Entity entity, String topicName) {
        OpenWriter open = writers.get(topicName);
        if (open == null) {
            throw new IllegalArgumentException("No open writer for topic: " + topicName);
        }
        open.writer.write(entity);
    }

    /** A shared writer plus the number of scenarios currently holding it; mutated only under {@link #writers}' per-key lock. */
    private static final class OpenWriter {
        private final NetworkWriter writer;
        private int refCount;

        OpenWriter(NetworkWriter writer) {
            this.writer = writer;
            this.refCount = 1;
        }
    }

    /** Installs the singleton from a {@link NetworkConfiguration}. Writers are opened later, on demand. */
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
            return install(new NetworkManager(configuration));
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
