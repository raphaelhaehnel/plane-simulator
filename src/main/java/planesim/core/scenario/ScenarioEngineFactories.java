package planesim.core.scenario;

import planesim.core.engine.GeoScenarioConfig;
import planesim.core.engine.MovementStyle;
import planesim.core.engine.NonGeoScenarioConfig;
import planesim.core.engine.ObjectWriters;
import planesim.core.engine.SimulationEngine;
import planesim.core.engine.ValueGenerators;
import planesim.external.Plane;
import planesim.external.Radar;
import planesim.external.Weather;

import java.util.Map;

/**
 * Predefined {@link ScenarioEngineFactory} for every existing {@link ScenarioType} (mirrors the
 * {@link ObjectWriters}/{@link ValueGenerators} "named constants" convention), plus the {@link
 * #DEFAULTS} map that {@code SimulationServerApp} wires into {@link ScenarioManager}. Adding a
 * future object type means adding one constant here (and to {@link #DEFAULTS}) — {@link
 * ScenarioManager} itself never needs to change.
 */
public final class ScenarioEngineFactories {

    public static final ScenarioEngineFactory PLANE = (config, publisher, scheduler) ->
            SimulationEngine.<Plane>create((GeoScenarioConfig) config, MovementStyle.MOBILE,
                    publisher::send, Plane::new, ObjectWriters.PLANE, scheduler);

    public static final ScenarioEngineFactory RADAR = (config, publisher, scheduler) ->
            SimulationEngine.<Radar>create((GeoScenarioConfig) config, MovementStyle.STATIC,
                    publisher::send, Radar::new, ObjectWriters.RADAR, scheduler);

    public static final ScenarioEngineFactory WEATHER = (config, publisher, scheduler) ->
            SimulationEngine.<Weather>createValueEngine((NonGeoScenarioConfig) config,
                    publisher::send, Weather::new, ValueGenerators.WEATHER, scheduler);

    public static final Map<ScenarioType, ScenarioEngineFactory> DEFAULTS = Map.of(
            ScenarioType.PLANE, PLANE,
            ScenarioType.RADAR, RADAR,
            ScenarioType.WEATHER, WEATHER);

    private ScenarioEngineFactories() {
    }
}
