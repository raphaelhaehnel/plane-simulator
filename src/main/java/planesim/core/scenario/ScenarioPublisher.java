package planesim.core.scenario;

import planesim.core.network.NetworkManager;
import planesim.external.Entity;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One scenario's outbound end: every object the engine produces each tick is (1) published on the
 * scenario's {@link NetworkManager} topic and (2) recorded as this object's latest state, so
 * {@code core.server}'s {@code GET /getScenarios} has a live snapshot to serve.
 *
 * <p>Fully type-agnostic: there is exactly one {@link #send(Entity)} method, shared by every
 * object type — adding a new external type (geographic or not) never touches this class.
 * Publishing is uniform since every external object is an {@link Entity}. Recording dispatches on
 * the scenario's {@link ScenarioCategory} (fixed at construction — a scenario is always
 * homogeneous) into one of two maps, because the two live-state shapes have nothing in common:
 * geographic state is always lat/lon/heading ({@link GeoLiveState}, captured via
 * {@link GeoFieldReader}), non-geographic state is a generic field map ({@link NonGeoLiveState},
 * captured via {@link NonGeoFieldReader}). Only one of the two maps is ever populated per
 * scenario instance.
 *
 * <p>Thread-safe because HTTP handler threads read {@link #geoSnapshot()}/{@link #nonGeoSnapshot()}
 * concurrently with the scenario's own tick thread calling {@code send}.
 *
 * <p>Public only so the public {@link ScenarioEngineFactory} can name it; still constructed
 * exclusively by {@link ScenarioManager}, hence the package-private constructor.
 */
public final class ScenarioPublisher {

    private final NetworkManager network;
    private final String topicName;
    private final ScenarioCategory category;

    private final Map<Object, Integer> indexByObject = Collections.synchronizedMap(new IdentityHashMap<>());
    private final AtomicInteger nextIndex = new AtomicInteger();

    private final Map<Integer, GeoLiveState> latestGeoByIndex = new ConcurrentHashMap<>();
    private final Map<Integer, NonGeoLiveState> latestNonGeoByIndex = new ConcurrentHashMap<>();

    ScenarioPublisher(NetworkManager network, String topicName, ScenarioCategory category) {
        this.network = network;
        this.topicName = topicName;
        this.category = category;
    }

    /** Records {@code entity}'s current state as this object's latest, then publishes it on the scenario's topic. */
    public void send(Entity entity) {
        int index = indexFor(entity);
        if (category == ScenarioCategory.GEOGRAPHIC) {
            latestGeoByIndex.put(index, GeoFieldReader.readState(index, entity));
        } else {
            latestNonGeoByIndex.put(index, new NonGeoLiveState(index, NonGeoFieldReader.readFields(entity)));
        }
        network.send(entity, topicName);
    }

    private int indexFor(Object object) {
        return indexByObject.computeIfAbsent(object, o -> nextIndex.getAndIncrement());
    }

    /** The latest known state of every geographic object (plane/radar/...) in this scenario, ordered by index. */
    List<GeoLiveState> geoSnapshot() {
        return latestGeoByIndex.values().stream()
                .sorted(Comparator.comparingInt(GeoLiveState::index))
                .toList();
    }

    /** The latest known reading of every non-geographic object (weather/...) in this scenario, ordered by index. */
    List<NonGeoLiveState> nonGeoSnapshot() {
        return latestNonGeoByIndex.values().stream()
                .sorted(Comparator.comparingInt(NonGeoLiveState::index))
                .toList();
    }
}
