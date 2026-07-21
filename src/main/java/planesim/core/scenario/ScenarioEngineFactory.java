package planesim.core.scenario;

import planesim.core.engine.ScenarioConfig;
import planesim.core.engine.SimulationEngine;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Builds a ready-to-start {@link SimulationEngine} for one {@link ScenarioType}, wired to send
 * every tick's objects through {@code publisher}. See {@link ScenarioEngineFactories} for the
 * predefined ones and {@link ScenarioManager} for how a type is looked up to its factory.
 */
public interface ScenarioEngineFactory {
    SimulationEngine<?> createEngine(ScenarioConfig config, ScenarioPublisher publisher, ScheduledExecutorService scheduler);
}
