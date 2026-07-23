package planesim.core.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import planesim.external.Entity;
import planesim.external.Topic;

/**
 * PLACEHOLDER wire I/O, real structure.
 * <p>
 * An open writer bound to a single {@link Topic}. In the real environment this is where bytes go on
 * the wire; here it just logs. One is opened per distinct topic name by {@link NetworkManager} (and
 * shared by every scenario using that name — see {@link NetworkManager#openWriter(String)}) and
 * {@link #close() closed} once the last such scenario is deleted. Constructed and closed only by
 * {@link NetworkManager}, hence package-private.
 */
class NetworkWriter {

    private static final Logger log = LogManager.getLogger(NetworkWriter.class);

    private final Topic topic;

    NetworkWriter(Topic topic) {
        this.topic = topic;
        log.info("opened writer for topic [{}]", topic);
    }

    void write(Entity entity) {
        entity.timestamp = System.currentTimeMillis();
        log.info("[{}] t={} {}", topic, entity.timestamp, entity);
    }

    void close() {
        log.info("closed writer for topic [{}]", topic);
    }
}
