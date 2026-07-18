package planesim;

/**
 * Example wiring for both formation types. Replace the {@link Plane}/{@link NetworkApi}
 * placeholders with your real library imports and this is basically all the setup code you need.
 */
public final class SimulationApp {

    public static void main(String[] args) throws InterruptedException {
        runLineExample();
        runCircleExample();
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
        runFor(config, 5_000);
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
        runFor(config, 5_000);
    }

    private static void runFor(SimulationConfig config, long millis) throws InterruptedException {
        NetworkApi networkApi = plane -> System.out.printf(
                "lat=%.6f lon=%.6f alt=%.1f vx=%.2f vy=%.2f heading=%.1f%n",
                plane.latitude, plane.longitude, plane.altitude, plane.vx, plane.vy, plane.heading);

        SimulationEngine engine = SimulationEngine.create(config, networkApi, Plane::new);
        engine.start();
        Thread.sleep(millis);
        engine.stop();
    }
}
