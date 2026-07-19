package planesim.scenario;

import planesim.api.NetworkApi;
import planesim.api.Plane;
import planesim.api.Radar;
import planesim.api.Weather;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side analogue of {@code planesim.ui.UiNetworkApi}/{@code MapPanel}: instead of forwarding
 * to a Swing panel, it records the latest state per object so {@code GET /getScenarios} can serve
 * a live snapshot. A single scenario is always homogeneous (all one object type, never mixed), but
 * this implements every {@link NetworkApi} overload since that's the full contract. Geographic
 * objects (plane/radar) and non-geographic ones (weather) are tracked in separate maps since they
 * have entirely different live-state shapes ({@link GeoLiveState} is always lat/lon/heading;
 * {@link NonGeoLiveState} is a generic field map, captured via {@link NonGeoFieldReader} so a new
 * non-geographic object type never needs a new map/method here — just a one-line {@code send(...)}
 * overload that calls {@link #recordNonGeo}) — only one of the two is ever populated per scenario
 * instance. Thread-safe because HTTP handler threads read {@link #geoSnapshot()}/{@link
 * #nonGeoSnapshot()} concurrently with the scenario's own tick thread calling {@code send}.
 */
final class ScenarioNetworkApi implements NetworkApi {

    private final Map<Object, Integer> indexByObject = Collections.synchronizedMap(new IdentityHashMap<>());
    private final AtomicInteger nextIndex = new AtomicInteger();

    private final Map<Integer, GeoLiveState> latestGeoByIndex = new ConcurrentHashMap<>();
    private final Map<Integer, NonGeoLiveState> latestNonGeoByIndex = new ConcurrentHashMap<>();

    @Override
    public void send(Plane plane) {
        int index = indexFor(plane);
        latestGeoByIndex.put(index, new GeoLiveState(index, plane.latitude, plane.longitude, plane.heading));
    }

    @Override
    public void send(Radar radar) {
        int index = indexFor(radar);
        latestGeoByIndex.put(index, new GeoLiveState(index, radar.latitude, radar.longitude, 0.0));
    }

    @Override
    public void send(Weather weather) {
        recordNonGeo(weather);
    }

    /** Any future non-geographic {@code send(...)} overload can just delegate here — no new record/DTO needed. */
    private void recordNonGeo(Object target) {
        int index = indexFor(target);
        latestNonGeoByIndex.put(index, new NonGeoLiveState(index, NonGeoFieldReader.readFields(target)));
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
