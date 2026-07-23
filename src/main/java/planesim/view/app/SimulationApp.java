package planesim.view.app;

import planesim.core.network.NetworkConfiguration;
import planesim.core.network.NetworkManager;
import planesim.external.Plane;
import planesim.external.Radar;
import planesim.external.Weather;
import planesim.core.engine.GeoScenarioConfig;
import planesim.core.engine.MovementStyle;
import planesim.core.engine.NonGeoScenarioConfig;
import planesim.core.engine.ObjectWriters;
import planesim.core.engine.SimulationEngine;
import planesim.core.engine.ValueGenerators;
import planesim.core.formation.CircleFormation;
import planesim.core.formation.LineFormation;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Example wiring for both formation types and every simulated object type. Replace the
 * {@link Plane}/{@link Radar}/{@link Weather}/{@link NetworkManager} placeholders with your real
 * library imports and this is basically all the setup code you need.
 */
public final class SimulationApp {

    private static final String PLANE_TOPIC = "planes";
    private static final String RADAR_TOPIC = "radars";
    private static final String WEATHER_TOPIC = "weather";

    /**
     * The one {@link NetworkManager} for this JVM — built once, then sent through everywhere below.
     * Each example opens its own writer for the topic it uses (and closes it when done), mirroring
     * how the server opens a writer per scenario. The configuration is a stand-in until the real
     * JSON-backed {@link NetworkConfiguration} is loaded (the server loads it from {@code config.json}).
     */
    private static final NetworkManager NETWORK = NetworkManager.builder()
            .configuration(new NetworkConfiguration("demo", 0))
            .build();

    public static void main(String[] args) throws InterruptedException {
        runLineExample();
        runCircleExample();
        runRadarExample();
        runWeatherExample();
    }

    private static void runLineExample() throws InterruptedException {
        GeoScenarioConfig config = new GeoScenarioConfig(
                0.3575, 0.9838,                             // origin (source) lat/lon, radians (approx. Yemen)
                5,                                           // number of planes
                230.0,                                       // speed, m/s
                10000.0,                                     // altitude, meters
                500,                                         // publish interval, ms
                new LineFormation(0.4300, 1.0500, 2000.0)    // destination lat/lon (radians), spacing (meters)
        );
        runPlanesFor(config, 5_000);
    }

    private static void runCircleExample() throws InterruptedException {
        GeoScenarioConfig config = new GeoScenarioConfig(
                0.3575, 0.9838,              // circle center lat/lon, radians
                6,                            // number of planes
                230.0,                        // speed, m/s
                10000.0,                      // altitude, meters
                500,                          // publish interval, ms
                new CircleFormation(5000.0)  // circle radius, meters
        );
        runPlanesFor(config, 5_000);
    }

    /** Radars are static, so this only demonstrates placement (evenly spaced around a circle), not movement. */
    private static void runRadarExample() throws InterruptedException {
        GeoScenarioConfig config = new GeoScenarioConfig(
                0.3575, 0.9838,              // circle center lat/lon, radians
                4,                            // number of radars
                230.0,                        // speed, m/s (ignored - radars never move)
                50.0,                         // altitude, meters
                1000,                         // publish interval, ms
                new CircleFormation(8000.0)  // circle radius, meters - just spaces the radars out
        );
        runRadarsFor(config, 3_000);
    }

    /** Weather has no coordinates at all - no origin, no formation, just N independent readings. */
    private static void runWeatherExample() throws InterruptedException {
        NonGeoScenarioConfig config = new NonGeoScenarioConfig(
                2,     // number of independent weather readings
                1000   // publish interval, ms
        );
        runWeatherFor(config, 3_000);
    }

    private static void runPlanesFor(GeoScenarioConfig config, long millis) throws InterruptedException {
        NETWORK.openWriter(PLANE_TOPIC);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        SimulationEngine<Plane> engine = SimulationEngine.create(config, MovementStyle.MOBILE,
                plane -> NETWORK.send(plane, PLANE_TOPIC), Plane::new, ObjectWriters.PLANE, scheduler);
        engine.start();
        Thread.sleep(millis);
        engine.pause();
        scheduler.shutdown();
        NETWORK.closeWriter(PLANE_TOPIC);
    }

    private static void runRadarsFor(GeoScenarioConfig config, long millis) throws InterruptedException {
        NETWORK.openWriter(RADAR_TOPIC);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        SimulationEngine<Radar> engine = SimulationEngine.create(config, MovementStyle.STATIC,
                radar -> NETWORK.send(radar, RADAR_TOPIC), Radar::new, ObjectWriters.RADAR, scheduler);
        engine.start();
        Thread.sleep(millis);
        engine.pause();
        scheduler.shutdown();
        NETWORK.closeWriter(RADAR_TOPIC);
    }

    private static void runWeatherFor(NonGeoScenarioConfig config, long millis) throws InterruptedException {
        NETWORK.openWriter(WEATHER_TOPIC);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        SimulationEngine<Weather> engine = SimulationEngine.createValueEngine(config,
                weather -> NETWORK.send(weather, WEATHER_TOPIC), Weather::new, ValueGenerators.WEATHER, scheduler);
        engine.start();
        Thread.sleep(millis);
        engine.pause();
        scheduler.shutdown();
        NETWORK.closeWriter(WEATHER_TOPIC);
    }
}
