package planesim.core.network;

/**
 * Configuration for the {@link NetworkManager}, loaded from {@code src/main/resources/config.json}
 * via Jackson (see {@code SimulationServerApp.buildNetwork()}). It carries the environment id and
 * the DDS-style domain id the network layer joins; the topics themselves are <b>not</b> listed here
 * anymore — they are opened on demand, per scenario, from the {@code topicName} supplied to
 * {@code POST /createScenario} (see {@link NetworkManager#openWriter(String)}).
 *
 * <p>Public mutable fields + a no-arg constructor so Jackson can populate it by field; the
 * two-arg constructor is for building one in code (e.g. the {@code SimulationApp} demo).
 */
public final class NetworkConfiguration {

    public String environment;
    public int domainId;

    public NetworkConfiguration() {
    }

    public NetworkConfiguration(String environment, int domainId) {
        this.environment = environment;
        this.domainId = domainId;
    }
}
