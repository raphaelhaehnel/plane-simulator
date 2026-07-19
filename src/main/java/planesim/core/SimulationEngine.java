package planesim.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs the formation: every {@code publishIntervalMs}, sends the current state of every object
 * over the network API, then advances every object's position for the next tick (matching the
 * "send, then update" order from the spec, so what's sent is always this tick's position, and
 * movement is applied for next tick). Type-agnostic over the external object type {@code T} (e.g.
 * {@link planesim.api.Plane}, {@link planesim.api.Radar}) — {@code T} is sent via a plain {@code
 * Consumer<T>} rather than {@code planesim.api.NetworkApi} directly, so callers integrating a real
 * multi-method {@code NetworkApi} just pass a method reference, e.g. {@code networkApi::send}
 * (which resolves to the matching overload for whichever {@code T} this engine was created with).
 *
 * <p>The engine does not own a thread: it's handed a {@link ScheduledExecutorService} (typically
 * shared across many engines/scenarios) and only tracks its own {@link ScheduledFuture}, so
 * {@link #pause()} cancels just this engine's task without touching the shared pool. Sharing one
 * pool across many engines is safe because {@code ScheduledThreadPoolExecutor} guarantees a given
 * periodic task's successive executions never overlap (a late run delays the next rather than
 * running concurrently), so one engine's own ticks never overlap regardless of pool size — and
 * different engines never share {@link SimulatedObject} instances (each {@link #create} call
 * builds its own private formation list), so concurrent ticks from different engines on different
 * pool threads never touch shared state. {@code start()}/{@code pause()}/{@code tick()} are
 * synchronized on this engine's own monitor purely to serialize the pause-then-immediately-resume
 * handoff (otherwise a new scheduled chain could start while an orphaned in-flight tick from the
 * cancelled chain is still running, causing two concurrent executions of this engine's own tick).
 */
public final class SimulationEngine<T> {

    private static final Logger log = LogManager.getLogger(SimulationEngine.class);

    private final List<SimulatedObject<T>> formation;
    private final Consumer<T> sink;
    private final long publishIntervalMs;
    private final ScheduledExecutorService scheduler;

    private ScheduledFuture<?> tickHandle;

    private SimulationEngine(List<SimulatedObject<T>> formation, Consumer<T> sink, long publishIntervalMs,
                              ScheduledExecutorService scheduler) {
        this.formation = formation;
        this.sink = sink;
        this.publishIntervalMs = publishIntervalMs;
        this.scheduler = scheduler;
    }

    /**
     * Builds the formation from {@code config} and returns a ready-to-start engine.
     *
     * @param movementStyle whether the objects fly their formation's natural movement pattern or
     *                      stay fixed in place (e.g. a radar) — see {@link MovementStyle}
     * @param sink          where each object's current state is sent every tick, e.g. {@code
     *                      networkApi::send}
     * @param objectFactory supplies one new externally-provided object instance per simulated
     *                      object, e.g. {@code Plane::new}
     * @param writer        projects local-frame state onto the external object's fields — see
     *                      {@link ObjectWriters} for the predefined ones
     * @param scheduler     the (possibly shared) executor this engine's ticks will run on; the
     *                      engine never shuts it down — that's the caller's responsibility
     */
    public static <T> SimulationEngine<T> create(SimulationConfig config, MovementStyle movementStyle,
                                                   Consumer<T> sink, Supplier<T> objectFactory,
                                                   ObjectWriter<T> writer, ScheduledExecutorService scheduler) {
        List<SimulatedObject<T>> formation = FormationPlanner.buildFormation(config, movementStyle, objectFactory, writer);
        return new SimulationEngine<>(formation, sink, config.publishIntervalMs(), scheduler);
    }

    /** Starts ticking, or resumes a paused engine from its current object state. Idempotent while already running. */
    public synchronized void start() {
        if (tickHandle != null) {
            return;
        }
        tickHandle = scheduler.scheduleAtFixedRate(this::tick, 0, publishIntervalMs, TimeUnit.MILLISECONDS);
    }

    /** Cancels ticking without touching the shared executor or losing object state. Idempotent. */
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

            // 1) Send every object's current state.
            for (SimulatedObject<T> simulatedObject : formation) {
                ObjectState state = simulatedObject.writeToExternal();
                T external = simulatedObject.external();
                sink.accept(external);
                log.info("Sent {} to NetworkApi: lat={}, lon={}, alt={}, vx={}, vy={}",
                        external.getClass().getSimpleName(), state.latRad(), state.lonRad(),
                        state.altitudeMeters(), state.velocity().x(), state.velocity().y());
            }

            // 2) Advance every object for the next tick (turning it around if it reached a waypoint).
            for (SimulatedObject<T> simulatedObject : formation) {
                simulatedObject.advance(dtSeconds);
            }
        } catch (RuntimeException e) {
            // scheduleAtFixedRate silently stops forever if a task throws, so never let one escape.
            // TODO: replace with your real logging.
            e.printStackTrace();
        }
    }
}
