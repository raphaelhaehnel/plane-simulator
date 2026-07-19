package planesim.scenario;

import planesim.api.NetworkApi;
import planesim.api.Plane;
import planesim.api.Radar;

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
 * a live snapshot. A single scenario is always homogeneous (all {@link Plane} or all {@link
 * Radar}, never mixed), but this implements both {@link NetworkApi} overloads since that's the
 * full contract. Thread-safe because HTTP handler threads read {@link #snapshot()} concurrently
 * with the scenario's own tick thread calling {@code send}.
 */
final class ScenarioNetworkApi implements NetworkApi {

    private final Map<Object, Integer> indexByObject = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Integer, ObjectLiveState> latestByIndex = new ConcurrentHashMap<>();
    private final AtomicInteger nextIndex = new AtomicInteger();

    @Override
    public void send(Plane plane) {
        record(plane, plane.latitude, plane.longitude, plane.heading);
    }

    @Override
    public void send(Radar radar) {
        record(radar, radar.latitude, radar.longitude, 0.0);
    }

    private void record(Object object, double latRad, double lonRad, double headingDeg) {
        int index = indexByObject.computeIfAbsent(object, o -> nextIndex.getAndIncrement());
        latestByIndex.put(index, new ObjectLiveState(index, latRad, lonRad, headingDeg));
    }

    /** The latest known state of every object in this scenario, ordered by index. */
    List<ObjectLiveState> snapshot() {
        return latestByIndex.values().stream()
                .sorted(Comparator.comparingInt(ObjectLiveState::index))
                .toList();
    }
}
