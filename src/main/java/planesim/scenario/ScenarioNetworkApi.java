package planesim.scenario;

import planesim.api.NetworkApi;
import planesim.api.Plane;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side analogue of {@code planesim.ui.UiNetworkApi}/{@code MapPanel}: instead of forwarding
 * to a Swing panel, it records the latest state per plane so {@code GET /getScenarios} can serve a
 * live snapshot. Thread-safe because HTTP handler threads read {@link #snapshot()} concurrently
 * with the scenario's own tick thread calling {@link #send}.
 */
final class ScenarioNetworkApi implements NetworkApi {

    private final Map<Plane, Integer> indexByPlane = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Integer, PlaneLiveState> latestByIndex = new ConcurrentHashMap<>();
    private final AtomicInteger nextIndex = new AtomicInteger();

    @Override
    public void send(Plane plane) {
        int index = indexByPlane.computeIfAbsent(plane, p -> nextIndex.getAndIncrement());
        latestByIndex.put(index, new PlaneLiveState(index, plane.latitude, plane.longitude, plane.heading));
    }

    /** The latest known state of every plane in this scenario, ordered by index. */
    List<PlaneLiveState> snapshot() {
        return latestByIndex.values().stream()
                .sorted(Comparator.comparingInt(PlaneLiveState::index))
                .toList();
    }
}
