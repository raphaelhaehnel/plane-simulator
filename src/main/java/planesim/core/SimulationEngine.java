package planesim.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import planesim.api.NetworkApi;
import planesim.api.Plane;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Runs the formation: every {@code publishIntervalMs}, sends the current state of every plane
 * over the network API, then advances every plane's position for the next tick (matching the
 * "send, then update" order from the spec, so what's sent is always this tick's position, and
 * movement is applied for next tick).
 *
 * <p>The engine does not own a thread: it's handed a {@link ScheduledExecutorService} (typically
 * shared across many engines/scenarios) and only tracks its own {@link ScheduledFuture}, so
 * {@link #pause()} cancels just this engine's task without touching the shared pool. Sharing one
 * pool across many engines is safe because {@code ScheduledThreadPoolExecutor} guarantees a given
 * periodic task's successive executions never overlap (a late run delays the next rather than
 * running concurrently), so one engine's own ticks never overlap regardless of pool size — and
 * different engines never share {@link SimulatedPlane} objects (each {@link #create} call builds
 * its own private formation list), so concurrent ticks from different engines on different pool
 * threads never touch shared state. {@code start()}/{@code pause()}/{@code tick()} are
 * synchronized on this engine's own monitor purely to serialize the pause-then-immediately-resume
 * handoff (otherwise a new scheduled chain could start while an orphaned in-flight tick from the
 * cancelled chain is still running, causing two concurrent executions of this engine's own tick).
 */
public final class SimulationEngine {

    private static final Logger log = LogManager.getLogger(SimulationEngine.class);

    private final List<SimulatedPlane> formation;
    private final NetworkApi networkApi;
    private final long publishIntervalMs;
    private final ScheduledExecutorService scheduler;

    private ScheduledFuture<?> tickHandle;

    private SimulationEngine(List<SimulatedPlane> formation, NetworkApi networkApi, long publishIntervalMs,
                              ScheduledExecutorService scheduler) {
        this.formation = formation;
        this.networkApi = networkApi;
        this.publishIntervalMs = publishIntervalMs;
        this.scheduler = scheduler;
    }

    /**
     * Builds the formation from {@code config} and returns a ready-to-start engine.
     *
     * @param planeFactory supplies one new externally-provided Plane instance per simulated
     *                     plane, e.g. {@code Plane::new}
     * @param scheduler    the (possibly shared) executor this engine's ticks will run on; the
     *                     engine never shuts it down — that's the caller's responsibility
     */
    public static SimulationEngine create(SimulationConfig config, NetworkApi networkApi,
                                           Supplier<Plane> planeFactory, ScheduledExecutorService scheduler) {
        List<SimulatedPlane> formation = FormationPlanner.buildFormation(config, planeFactory);
        return new SimulationEngine(formation, networkApi, config.publishIntervalMs(), scheduler);
    }

    /** Starts ticking, or resumes a paused engine from its current plane state. Idempotent while already running. */
    public synchronized void start() {
        if (tickHandle != null) {
            return;
        }
        tickHandle = scheduler.scheduleAtFixedRate(this::tick, 0, publishIntervalMs, TimeUnit.MILLISECONDS);
    }

    /** Cancels ticking without touching the shared executor or losing plane state. Idempotent. */
    public synchronized void pause() {
        if (tickHandle == null) {
            return;
        }
        tickHandle.cancel(false);
        tickHandle = null;
    }

    private synchronized void tick() {
        try {
            double dtSeconds = publishIntervalMs / 1000.0;

            // 1) Send every plane's current state.
            for (SimulatedPlane simulatedPlane : formation) {
                simulatedPlane.writeToPlane();
                Plane plane = simulatedPlane.plane();
                networkApi.send(plane);
                log.info("Sent plane to NetworkApi: lat={}, lon={}, alt={}, vx={}, vy={}, heading={}",
                        plane.latitude, plane.longitude, plane.altitude, plane.vx, plane.vy, plane.heading);
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
