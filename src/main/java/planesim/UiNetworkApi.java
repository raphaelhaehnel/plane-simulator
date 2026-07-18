package planesim;

/**
 * Stands in for your real network API while it's not ready yet: instead of sending each plane
 * over the wire, it hands its state to the {@link MapPanel} for rendering. Once your real
 * NetworkApi exists, just pass that to {@link SimulationEngine#create} instead of this class —
 * nothing else about the simulation needs to change.
 */
final class UiNetworkApi implements NetworkApi {

    private final MapPanel mapPanel;

    UiNetworkApi(MapPanel mapPanel) {
        this.mapPanel = mapPanel;
    }

    @Override
    public void send(Plane plane) {
        // `plane` itself is used as the map key: the engine reuses the same Plane instance for a
        // given simulated plane on every tick, so its identity is a stable, free id.
        mapPanel.updatePlane(plane, plane.latitude, plane.longitude, plane.heading);
    }
}
