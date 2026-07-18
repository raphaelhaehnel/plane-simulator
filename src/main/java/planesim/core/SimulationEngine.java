package planesim.core;

import planesim.api.NetworkApi;
import planesim.api.Plane;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Runs the formation: every {@code publishIntervalMs}, sends the current state of every plane
 * over the network API, then advances every plane's position for the next tick (matching the
 * "send, then update" order from the spec, so what's sent is always this tick's position, and
 * movement is applied for next tick).
 *
 * <p>All planes are driven from a single scheduler thread rather than one thread per plane —
 * simpler, no synchronization needed, and perfectly adequate unless you're simulating an
 * unusually large number of planes.
 */
public final class SimulationEngine {

    private final List<SimulatedPlane> formation;
    private final NetworkApi networkApi;
    private final long publishIntervalMs;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private SimulationEngine(List<SimulatedPlane> formation, NetworkApi networkApi, long publishIntervalMs) {
        this.formation = formation;
        this.networkApi = networkApi;
        this.publishIntervalMs = publishIntervalMs;
    }

    /**
     * Builds the formation from {@code config} and returns a ready-to-start engine.
     *
     * @param planeFactory supplies one new externally-provided Plane instance per simulated
     *                     plane, e.g. {@code Plane::new}
     */
    public static SimulationEngine create(SimulationConfig config, NetworkApi networkApi, Supplier<Plane> planeFactory) {
        List<SimulatedPlane> formation = FormationPlanner.buildFormation(config, planeFactory);
        return new SimulationEngine(formation, networkApi, config.publishIntervalMs());
    }

    /** Starts ticking at the configured publish interval. Call {@link #stop()} to end it. */
    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 0, publishIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void tick() {
        try {
            double dtSeconds = publishIntervalMs / 1000.0;

            // 1) Send every plane's current state.
            for (SimulatedPlane simulatedPlane : formation) {
                simulatedPlane.writeToPlane();
                networkApi.send(simulatedPlane.plane());
            }

            // 2) Advance every plane for the next tick (turning it around if it reached a waypoint).
            for (SimulatedPlane simulatedPlane : formation) {
                simulatedPlane.advance(dtSeconds);
            }
        } catch (RuntimeException e) {
            // scheduleAtFixedRate silently stops forever if a task throws, so never let one escape.
            // TODO: replace with your real logging.
            e.printStackTrace();
        }
    }
}
