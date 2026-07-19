package planesim.core;

import planesim.api.Weather;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Predefined {@link ValueGenerator}s for the placeholder non-geographic external object types in
 * {@code planesim.api}. Uses {@link ThreadLocalRandom} rather than a shared/captured
 * {@code Random} since, unlike a formation's placement RNG (drawn once at construction, on one
 * thread), a value generator is invoked fresh on every tick — possibly from a different pool
 * thread each time.
 */
public final class ValueGenerators {

    private static final double MAX_WIND_MPS = 30.0;
    private static final float MIN_TEMPERATURE_C = -10f;
    private static final float TEMPERATURE_RANGE_C = 50f;

    public static final ValueGenerator<Weather> WEATHER = weather -> {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        weather.windVelocity = random.nextDouble(MAX_WIND_MPS);
        weather.temperature = MIN_TEMPERATURE_C + random.nextFloat() * TEMPERATURE_RANGE_C;
        weather.isSunny = random.nextBoolean();
    };

    private ValueGenerators() {
    }
}
