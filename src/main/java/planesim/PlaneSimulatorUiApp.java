package planesim;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * Standalone Swing test harness: configure a formation, hit Start, and watch the planes move on
 * a local map — no real network API needed yet. When your real NetworkApi is ready, swap
 * {@link UiNetworkApi} for it in {@link #onStart()}; everything else (config, engine, formation
 * logic) stays exactly as-is.
 */
public final class PlaneSimulatorUiApp extends JFrame {

    private final MapPanel mapPanel = new MapPanel();

    private final JComboBox<String> formationTypeCombo = new JComboBox<>(new String[]{"Line", "Circle"});
    private final JSpinner planeCountSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
    private final JTextField speedField = new JTextField("230", 5);
    private final JTextField altitudeField = new JTextField("10000", 6);
    private final JTextField publishIntervalField = new JTextField("500", 5);
    private final JTextField originLatField = new JTextField("20.48", 6);
    private final JTextField originLonField = new JTextField("56.36", 6);
    private final JTextField destLatField = new JTextField("24.60", 6);
    private final JTextField destLonField = new JTextField("60.16", 6);
    private final JTextField spacingField = new JTextField("2000", 5);
    private final JTextField radiusField = new JTextField("5000", 5);
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");

    private SimulationEngine currentEngine;

    private PlaneSimulatorUiApp() {
        super("Plane Simulator - Test UI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildControlPanel(), BorderLayout.NORTH);
        add(mapPanel, BorderLayout.CENTER);

        stopButton.setEnabled(false);
        startButton.addActionListener(e -> onStart());
        stopButton.addActionListener(e -> onStop());

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        panel.add(new JLabel("Formation:"));
        panel.add(formationTypeCombo);
        panel.add(new JLabel("Planes:"));
        panel.add(planeCountSpinner);
        panel.add(new JLabel("Speed m/s:"));
        panel.add(speedField);
        panel.add(new JLabel("Altitude m:"));
        panel.add(altitudeField);
        panel.add(new JLabel("Publish ms:"));
        panel.add(publishIntervalField);
        panel.add(new JLabel("Origin lat/lon (deg):"));
        panel.add(originLatField);
        panel.add(originLonField);
        panel.add(new JLabel("[Line] Dest lat/lon (deg):"));
        panel.add(destLatField);
        panel.add(destLonField);
        panel.add(new JLabel("[Line] Spacing m:"));
        panel.add(spacingField);
        panel.add(new JLabel("[Circle] Radius m:"));
        panel.add(radiusField);
        panel.add(startButton);
        panel.add(stopButton);
        return panel;
    }

    private void onStart() {
        SimulationConfig config;
        try {
            config = buildConfig();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this,
                    "Please check your inputs - " + e.getMessage(),
                    "Invalid input", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (currentEngine != null) {
            currentEngine.stop();
        }
        mapPanel.clear();

        UiNetworkApi networkApi = new UiNetworkApi(mapPanel);
        currentEngine = SimulationEngine.create(config, networkApi, Plane::new);
        currentEngine.start();

        startButton.setEnabled(false);
        stopButton.setEnabled(true);
    }

    private void onStop() {
        if (currentEngine != null) {
            currentEngine.stop();
            currentEngine = null;
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private SimulationConfig buildConfig() {
        double originLatRad = Math.toRadians(parse(originLatField, "origin latitude"));
        double originLonRad = Math.toRadians(parse(originLonField, "origin longitude"));
        int planeCount = (Integer) planeCountSpinner.getValue();
        double speedMps = parse(speedField, "speed");
        double altitudeMeters = parse(altitudeField, "altitude");
        long publishIntervalMs = (long) parse(publishIntervalField, "publish interval");

        FormationSpec formation;
        if ("Circle".equals(formationTypeCombo.getSelectedItem())) {
            formation = new CircleFormation(parse(radiusField, "radius"));
        } else {
            double destLatRad = Math.toRadians(parse(destLatField, "destination latitude"));
            double destLonRad = Math.toRadians(parse(destLonField, "destination longitude"));
            formation = new LineFormation(destLatRad, destLonRad, parse(spacingField, "spacing"));
        }

        return new SimulationConfig(originLatRad, originLonRad, planeCount, speedMps, altitudeMeters,
                publishIntervalMs, formation);
    }

    private static double parse(JTextField field, String fieldName) {
        try {
            return Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(fieldName + " must be a number");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PlaneSimulatorUiApp().setVisible(true));
    }
}
