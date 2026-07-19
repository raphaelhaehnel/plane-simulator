package planesim.ui;

import planesim.server.dto.ObjectStateDto;
import planesim.server.dto.ScenarioDto;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * View-only dashboard: polls {@code GET /getScenarios} on the running {@link
 * planesim.server.SimulationServerApp} and renders every scenario's objects (planes, radars, ...)
 * together on one map. There is no way to create/start/pause/delete a scenario from here — that's
 * the whole point of going through the HTTP API instead of driving a {@code SimulationEngine}
 * in-process, the way the old form-and-buttons version of this class used to.
 */
public final class PlaneSimulatorUiApp extends JFrame {

    private static final long POLL_INTERVAL_MS = 1000;

    private final MapPanel mapPanel = new MapPanel();
    private final JLabel statusLabel = new JLabel("Connecting...");
    private final ScenarioPollingClient pollingClient;
    private final String serverUrl;
    private final ScheduledExecutorService pollExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "scenario-poll");
        t.setDaemon(true);
        return t;
    });

    private boolean lastPollFailed;

    private PlaneSimulatorUiApp(String serverUrl) {
        super("Plane Simulator - Live View");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.serverUrl = serverUrl;
        this.pollingClient = new ScenarioPollingClient(serverUrl);

        setLayout(new BorderLayout());
        add(statusLabel, BorderLayout.NORTH);
        add(mapPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);

        pollExecutor.scheduleAtFixedRate(this::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void poll() {
        try {
            List<ScenarioDto> scenarios = pollingClient.fetchScenarios();
            Map<String, PlaneSnapshot> snapshots = new HashMap<>();
            for (ScenarioDto scenario : scenarios) {
                for (ObjectStateDto object : scenario.objects) {
                    snapshots.put(scenario.id + "#" + object.index,
                            new PlaneSnapshot(object.latRad, object.lonRad, object.headingDeg));
                }
            }
            String statusText = scenarios.size() + " scenarios, " + snapshots.size() + " objects";
            SwingUtilities.invokeLater(() -> {
                mapPanel.replaceAll(snapshots);
                statusLabel.setText(statusText);
            });
            lastPollFailed = false;
        } catch (ConnectException e) {
            // Expected whenever SimulationServerApp isn't up yet (or was restarted); don't spam a
            // stack trace once per poll interval, just surface it once and keep retrying silently.
            if (!lastPollFailed) {
                System.err.println("Cannot reach server at " + serverUrl + " (retrying every "
                        + POLL_INTERVAL_MS + "ms) - is SimulationServerApp running?");
            }
            lastPollFailed = true;
            SwingUtilities.invokeLater(() -> statusLabel.setText("Server not reachable at " + serverUrl));
        } catch (Exception e) {
            if (!lastPollFailed) {
                e.printStackTrace();
            }
            lastPollFailed = true;
            SwingUtilities.invokeLater(() -> statusLabel.setText("Poll failed: " + e.getMessage()));
        }
    }

    public static void main(String[] args) {
        String serverUrl = System.getProperty("serverUrl", "http://localhost:8080");
        SwingUtilities.invokeLater(() -> new PlaneSimulatorUiApp(serverUrl).setVisible(true));
    }
}
