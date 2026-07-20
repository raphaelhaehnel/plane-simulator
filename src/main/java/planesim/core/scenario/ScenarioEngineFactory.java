package planesim.core.scenario;

import planesim.core.engine.ScenarioConfig;
import planesim.core.engine.SimulationEngine;
import planesim.external.NetworkApi;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Builds a ready-to-start {@link SimulationEngine} for one {@link ScenarioType}. See
 * {@link ScenarioEngineFactories} for the predefined ones and {@link ScenarioManager} for how a
 * type is looked up to its factory.
 */
public interface ScenarioEngineFactory {
    SimulationEngine<?> createEngine(ScenarioConfig config, NetworkApi networkApi, ScheduledExecutorService scheduler);
}
