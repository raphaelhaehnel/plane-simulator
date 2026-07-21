package planesim.external;

import java.util.List;

/**
 * PLACEHOLDER ONLY — <b>this is the file to replace first.</b>
 * <p>
 * Stands in for the real network configuration, which will be loaded from a JSON file and carry
 * the topic names to open, the environment id, and whatever else the network layer needs. It is
 * the single input to {@link NetworkManager.Builder#build()}, which instantiates the
 * {@link NetworkWriter}(s) and the {@code name -> }{@link Topic} map from it.
 * <p>
 * Nothing in {@code planesim.core} reads this type — the callers that build the manager
 * ({@code SimulationServerApp}, {@code SimulationApp}) construct one in code purely so the module
 * runs standalone until the real JSON loading exists.
 */
public final class NetworkConfiguration {

    public final String environmentId;
    public final List<String> topicNames;

    public NetworkConfiguration(String environmentId, List<String> topicNames) {
        this.environmentId = environmentId;
        this.topicNames = List.copyOf(topicNames);
    }
}
