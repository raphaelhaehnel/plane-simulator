package planesim;

/** Immutable snapshot of one plane's state at the moment it was "published", used only by the UI. */
record PlaneSnapshot(double latRad, double lonRad, double headingDeg) {
}
