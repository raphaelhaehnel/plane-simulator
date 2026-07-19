package planesim.core;

import planesim.geo.Vector2;

/** The state just projected onto an external object, kept around only so {@link SimulationEngine} can log it. */
record ObjectState(double latRad, double lonRad, double altitudeMeters, Vector2 velocity) {
}
