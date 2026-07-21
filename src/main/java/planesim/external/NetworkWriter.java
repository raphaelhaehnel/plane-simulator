package planesim.external;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * PLACEHOLDER ONLY.
 * <p>
 * Stands in for whatever actually puts bytes on the wire, so this module compiles and runs
 * standalone. It is reached only through {@link NetworkManager#send(Entity, String)}; the log line
 * below is diagnostic scaffolding (same spirit as the {@code toString()} overrides on
 * {@link Plane}/{@link Radar}/{@link Weather}), not behaviour to design around. Delete this file
 * and use your actual import instead.
 */
public class NetworkWriter {

    private static final Logger log = LogManager.getLogger(NetworkWriter.class);

    public void write(Entity entity, Topic topic) {
        entity.timestamp = System.currentTimeMillis();
        log.info("[{}] t={} {}", topic, entity.timestamp, entity);
    }
}
