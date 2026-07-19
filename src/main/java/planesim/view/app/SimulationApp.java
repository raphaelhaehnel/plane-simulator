package planesim.view.app;

import planesim.external.NetworkApi;
import planesim.external.Plane;
import planesim.external.Radar;
import planesim.external.Weather;
import planesim.core.engine.MovementStyle;
import planesim.core.engine.ObjectWriters;
import planesim.core.engine.SimulationConfig;
import planesim.core.engine.SimulationEngine;
import planesim.core.engine.ValueGenerators;
import planesim.core.engine.ValueSimulationConfig;
import planesim.core.formation.CircleFormation;
import planesim.core.formation.LineFormation;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Example wiring for both formation types and every simulated object type. Replace the
 * {@link Plane}/{@link Radar}/{@link Weather}/{@link NetworkApi} placeholders with your real
 * library imports and this is basically all the setup code you need.
 */
public final class SimulationApp {

    private static final NetworkApi NETWORK_API = new NetworkApi() {
        @Override
        public void send(Plane plane) {
            System.out.printf("plane lat=%.6f lon=%.6f alt=%.1f vx=%.2f vy=%.2f heading=%.1f%n",
                    plane.latitude, plane.longitude, plane.altitude, plane.vx, plane.vy, plane.heading);
        }

        @Override
        public void send(Radar radar) {
            System.out.printf("radar lat=%.6f lon=%.6f alt=%.1f%n", radar.latitude, radar.longitude, radar.altitude);
        }

        @Override
        public void send(Weather weather) {
            System.out.printf("weather windVelocity=%.2f temperature=%.1f isSunny=%b%n",
                    weather.windVelocity, weather.temperature, weather.isSunny);
        }
    };

    public static void main(String[] args) throws InterruptedException {
        runLineExample();
        runCircleExample();
        runRadarExample();
        runWeatherExample();
    }

    private static void runLineExample() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(
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
        SimulationConfig config = new SimulationConfig(
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
        SimulationConfig config = new SimulationConfig(
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
        ValueSimulationConfig config = new ValueSimulationConfig(
                2,     // number of independent weather readings
                1000   // publish interval, ms
        );
        runWeatherFor(config, 3_000);
    }

    private static void runPlanesFor(SimulationConfig config, long millis) throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        SimulationEngine<Plane> engine = SimulationEngine.create(config, MovementStyle.MOBILE,
                NETWORK_API::send, Plane::new, ObjectWriters.PLANE, scheduler);
        engine.start();
        Thread.sleep(millis);
        engine.pause();
        scheduler.shutdown();
    }

    private static void runRadarsFor(SimulationConfig config, long millis) throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        SimulationEngine<Radar> engine = SimulationEngine.create(config, MovementStyle.STATIC,
                NETWORK_API::send, Radar::new, ObjectWriters.RADAR, scheduler);
        engine.start();
        Thread.sleep(millis);
        engine.pause();
        scheduler.shutdown();
    }

    private static void runWeatherFor(ValueSimulationConfig config, long millis) throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        SimulationEngine<Weather> engine = SimulationEngine.createValueEngine(config,
                NETWORK_API::send, Weather::new, ValueGenerators.WEATHER, scheduler);
        engine.start();
        Thread.sleep(millis);
        engine.pause();
        scheduler.shutdown();
    }
}
