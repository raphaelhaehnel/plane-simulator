package planesim.core.scenario;

import planesim.core.network.NetworkManager;
import planesim.external.Entity;
import planesim.external.Plane;
import planesim.external.Radar;
import planesim.external.Weather;

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
 * <p>A scenario is always homogeneous (all one object type, never mixed), so only one {@code
 * send(...)} overload is ever exercised per instance; they all exist because the engine binds a
 * method reference for whichever type it was created with (see {@link ScenarioEngineFactories}).
 * Publishing is uniform — every external object is an {@link Entity}, so it's one shared {@link
 * #publish} call — but recording is not: geographic objects (plane/radar) and non-geographic ones
 * (weather) are tracked in separate maps since their live-state shapes have nothing in common
 * ({@link GeoLiveState} is always lat/lon/heading; {@link NonGeoLiveState} is a generic field map,
 * captured via {@link NonGeoFieldReader} so a new non-geographic object type never needs a new
 * map/method here — just a one-line {@code send(...)} overload that calls {@link #recordNonGeo}).
 * Only one of the two maps is ever populated per scenario instance.
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

    private final Map<Object, Integer> indexByObject = Collections.synchronizedMap(new IdentityHashMap<>());
    private final AtomicInteger nextIndex = new AtomicInteger();

    private final Map<Integer, GeoLiveState> latestGeoByIndex = new ConcurrentHashMap<>();
    private final Map<Integer, NonGeoLiveState> latestNonGeoByIndex = new ConcurrentHashMap<>();

    ScenarioPublisher(NetworkManager network, String topicName) {
        this.network = network;
        this.topicName = topicName;
    }

    public void send(Plane plane) {
        int index = indexFor(plane);
        latestGeoByIndex.put(index, new GeoLiveState(index, plane.latitude, plane.longitude, plane.heading));
        publish(plane);
    }

    public void send(Radar radar) {
        int index = indexFor(radar);
        latestGeoByIndex.put(index, new GeoLiveState(index, radar.latitude, radar.longitude, 0.0));
        publish(radar);
    }

    public void send(Weather weather) {
        recordNonGeo(weather);
        publish(weather);
    }

    /** Any future non-geographic {@code send(...)} overload can just delegate here — no new record/DTO needed. */
    private void recordNonGeo(Entity target) {
        int index = indexFor(target);
        latestNonGeoByIndex.put(index, new NonGeoLiveState(index, NonGeoFieldReader.readFields(target)));
    }

    private void publish(Entity entity) {
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
