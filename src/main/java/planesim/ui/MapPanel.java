package planesim.ui;

import planesim.geo.GeoMath;
import planesim.geo.Vector2;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * A simple, test-only "map": rather than a real world map (which would render any single
 * formation as sub-pixel — these are meter/km-scale, not continent-scale), this auto-fits to
 * whichever planes are currently tracked, using the same local-meter projection the simulation
 * itself uses ({@link GeoMath}), so circles stay circular and lines stay straight at any zoom
 * level. A light distance grid stands in for lat/lon graticules.
 *
 * <p>Objects are keyed by a caller-supplied String id (e.g. {@code scenarioId + "#" + index})
 * rather than object identity, since object data now arrives over HTTP as parsed JSON with no
 * shared object identity across the process boundary. Every object (plane, radar, ...) is drawn
 * with the same icon — this is a generic tracked-object view, not a plane-specific one.
 */
final class MapPanel extends JPanel {

    private static final Color BACKGROUND = new Color(235, 242, 247);
    private static final Color GRID_LINE = new Color(203, 213, 221);
    private static final Color AXIS_LINE = new Color(140, 155, 168);
    private static final Color PLANE_COLOR = new Color(196, 30, 58);
    private static final double MIN_SPAN_M = 500.0;
    private static final double PADDING_FACTOR = 1.3;

    // "Nice" grid step sizes in meters, ascending.
    private static final double[] GRID_STEPS_M = {
            1, 2, 5, 10, 20, 50, 100, 200, 500,
            1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000,
            200_000, 500_000, 1_000_000, 2_000_000, 5_000_000
    };

    private static final Path2D.Double PLANE_SHAPE = buildPlaneShape();

    private volatile Map<String, PlaneSnapshot> planesById = Map.of();

    MapPanel() {
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(900, 600));
    }

    /** Called once per poll cycle with the complete current set of planes across all scenarios. */
    void replaceAll(Map<String, PlaneSnapshot> snapshots) {
        this.planesById = Map.copyOf(snapshots);
        repaint();
    }

    /** Clears all tracked planes. */
    void clear() {
        replaceAll(Map.of());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        render(g2, getWidth(), getHeight());
        g2.dispose();
    }

    /** Drawing logic factored out of paintComponent so it can also be exercised off-screen (e.g. in tests). */
    void render(Graphics2D g2, int width, int height) {
        List<PlaneSnapshot> snapshot = List.copyOf(planesById.values());

        if (snapshot.isEmpty()) {
            drawEmptyState(g2, width, height);
            return;
        }

        double originLatRad = average(snapshot, PlaneSnapshot::latRad);
        double originLonRad = average(snapshot, PlaneSnapshot::lonRad);

        List<Vector2> localPositions = new ArrayList<>(snapshot.size());
        for (PlaneSnapshot p : snapshot) {
            localPositions.add(GeoMath.toLocal(p.latRad(), p.lonRad(), originLatRad, originLonRad));
        }

        double minX = localPositions.stream().mapToDouble(Vector2::x).min().orElse(0);
        double maxX = localPositions.stream().mapToDouble(Vector2::x).max().orElse(0);
        double minY = localPositions.stream().mapToDouble(Vector2::y).min().orElse(0);
        double maxY = localPositions.stream().mapToDouble(Vector2::y).max().orElse(0);

        double spanX = Math.max(maxX - minX, MIN_SPAN_M);
        double spanY = Math.max(maxY - minY, MIN_SPAN_M);
        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double halfSpanX = spanX / 2.0 * PADDING_FACTOR;
        double halfSpanY = spanY / 2.0 * PADDING_FACTOR;

        int margin = 48;
        double scale = Math.min(
                (width - 2.0 * margin) / (2 * halfSpanX),
                (height - 2.0 * margin) / (2 * halfSpanY)
        );

        double gridStepM = chooseGridStep(2 * Math.max(halfSpanX, halfSpanY));

        drawGrid(g2, width, height, centerX, centerY, scale, gridStepM);

        g2.setColor(PLANE_COLOR);
        for (int i = 0; i < snapshot.size(); i++) {
            Vector2 local = localPositions.get(i);
            double screenX = width / 2.0 + (local.x() - centerX) * scale;
            double screenY = height / 2.0 - (local.y() - centerY) * scale;
            drawPlaneIcon(g2, screenX, screenY, snapshot.get(i).headingDeg());
        }

        drawLegend(g2, width, height, gridStepM, snapshot.size(), originLatRad, originLonRad);
    }

    private void drawGrid(Graphics2D g2, int width, int height, double centerX, double centerY,
                           double scale, double stepM) {
        double visibleHalfWidthM = width / 2.0 / scale;
        double visibleHalfHeightM = height / 2.0 / scale;

        double startX = Math.floor((centerX - visibleHalfWidthM) / stepM) * stepM;
        double endX = centerX + visibleHalfWidthM;
        for (double x = startX; x <= endX; x += stepM) {
            int screenX = (int) Math.round(width / 2.0 + (x - centerX) * scale);
            g2.setColor(Math.abs(x) < stepM / 2.0 ? AXIS_LINE : GRID_LINE);
            g2.drawLine(screenX, 0, screenX, height);
        }

        double startY = Math.floor((centerY - visibleHalfHeightM) / stepM) * stepM;
        double endY = centerY + visibleHalfHeightM;
        for (double y = startY; y <= endY; y += stepM) {
            int screenY = (int) Math.round(height / 2.0 - (y - centerY) * scale);
            g2.setColor(Math.abs(y) < stepM / 2.0 ? AXIS_LINE : GRID_LINE);
            g2.drawLine(0, screenY, width, screenY);
        }
    }

    private void drawLegend(Graphics2D g2, int width, int height, double gridStepM, int objectCount,
                             double originLatRad, double originLonRad) {
        String text = String.format(Locale.US,
                "objects: %d   grid: %s   view centered near lat %.4f%s, lon %.4f%s (local projection - not a real map)",
                objectCount, formatDistance(gridStepM),
                Math.toDegrees(originLatRad), "\u00B0", Math.toDegrees(originLonRad), "\u00B0");
        g2.setColor(AXIS_LINE);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
        g2.drawString(text, 10, height - 10);
    }

    private void drawEmptyState(Graphics2D g2, int width, int height) {
        g2.setColor(AXIS_LINE);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 14f));
        String msg = "No running scenarios";
        FontMetrics fm = g2.getFontMetrics();
        int x = (width - fm.stringWidth(msg)) / 2;
        int y = height / 2;
        g2.drawString(msg, x, y);
    }

    private static void drawPlaneIcon(Graphics2D g2, double x, double y, double headingDeg) {
        AffineTransform original = g2.getTransform();
        g2.translate(x, y);
        g2.rotate(Math.toRadians(headingDeg));
        g2.fill(PLANE_SHAPE);
        g2.setTransform(original);
    }

    /** A small, simple dart-like airplane silhouette, nose pointing "up" (north) before rotation. */
    private static Path2D.Double buildPlaneShape() {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0, -9);
        path.lineTo(2, -3);
        path.lineTo(8, 3);
        path.lineTo(2, 2);
        path.lineTo(2, 8);
        path.lineTo(0, 5);
        path.lineTo(-2, 8);
        path.lineTo(-2, 2);
        path.lineTo(-8, 3);
        path.lineTo(-2, -3);
        path.closePath();
        return path;
    }

    private static double chooseGridStep(double visibleSpanMeters) {
        for (double step : GRID_STEPS_M) {
            if (visibleSpanMeters / step <= 10) {
                return step;
            }
        }
        return GRID_STEPS_M[GRID_STEPS_M.length - 1];
    }

    private static String formatDistance(double meters) {
        if (meters >= 1000) {
            return String.format(Locale.US, "%.0f km", meters / 1000.0);
        }
        return String.format(Locale.US, "%.0f m", meters);
    }

    private static double average(List<PlaneSnapshot> list, ToDoubleFunction<PlaneSnapshot> extractor) {
        return list.stream().mapToDouble(extractor).average().orElse(0);
    }
}
