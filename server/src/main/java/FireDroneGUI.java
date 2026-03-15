import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class FireDroneGUI extends JFrame {
    private JTextArea  logArea;
    private JTextArea  eventListArea;
    private JLabel     serverStatusLabel;
    private JLabel     activeFireLabel;
    private DefaultListModel<String> zonesModel;
    private ZoneMapPanel mapPanel;

    private final Queue<String> logQueue = new LinkedList<>();
    private final Object logLock = new Object();

    private final Map<Integer, DroneMarker> droneMarkers = new ConcurrentHashMap<>();
    private final Map<Integer, String> activeFireSeverity = new ConcurrentHashMap<>();

    private int activeFireCount = 0;

    public FireDroneGUI(List<FireIncidentZone> zones) {
        setTitle("Firefighting Drone System – Fire Incident Server");
        setSize(1200, 950);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setupGUI();
        loadZones(zones);
        startLogProcessor();
        setVisible(true);
    }

    private void setupGUI() {
        // North: status bar
        JPanel statusPanel = new JPanel(new GridLayout(1, 3));
        JLabel titleLabel = new JLabel("Fire Incident Subsystem (Server)", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        serverStatusLabel = new JLabel("Server: STARTING", JLabel.CENTER);
        serverStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        activeFireLabel = new JLabel("Active Fires: 0", JLabel.CENTER);
        activeFireLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusPanel.add(titleLabel);
        statusPanel.add(serverStatusLabel);
        statusPanel.add(activeFireLabel);
        add(statusPanel, BorderLayout.NORTH);

        // Center: log + fire events
        JPanel centerPanel = new JPanel(new GridLayout(1, 2));

        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("System Log"));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel eventPanel = new JPanel(new BorderLayout());
        eventPanel.setBorder(BorderFactory.createTitledBorder("Fire Events Sent"));
        eventListArea = new JTextArea();
        eventListArea.setEditable(false);
        eventListArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        eventPanel.add(new JScrollPane(eventListArea), BorderLayout.CENTER);

        centerPanel.add(logPanel);
        centerPanel.add(eventPanel);
        add(centerPanel, BorderLayout.CENTER);

        // South: zone list + map
        JPanel bottomPanel = new JPanel(new GridLayout(1, 2));
        bottomPanel.setPreferredSize(new Dimension(1200, 360));

        JPanel zonesPanel = new JPanel(new BorderLayout());
        zonesPanel.setBorder(BorderFactory.createTitledBorder("Zones"));
        zonesModel = new DefaultListModel<>();
        zonesPanel.add(new JScrollPane(new JList<>(zonesModel)), BorderLayout.CENTER);

        mapPanel = new ZoneMapPanel();
        mapPanel.setBorder(BorderFactory.createTitledBorder(
                "Zone Map"));

        bottomPanel.add(zonesPanel);
        bottomPanel.add(mapPanel);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadZones(List<FireIncidentZone> zones) {
        for (FireIncidentZone z : zones) zonesModel.addElement(z.toString());
        mapPanel.setZones(zones);
    }

    private void startLogProcessor() {
        Thread t = new Thread(() -> {
            while (true) {
                String msg = null;
                synchronized (logLock) {
                    while (logQueue.isEmpty()) {
                        try { logLock.wait(); } catch (InterruptedException e) { return; }
                    }
                    msg = logQueue.poll();
                }
                String finalMsg = msg;
                SwingUtilities.invokeLater(() -> {
                    logArea.append(finalMsg + "\n");
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public void log(String message) {
        synchronized (logLock) {
            logQueue.offer(message);
            logLock.notifyAll();
        }
    }

    public void logError(String message) { log("[ERROR] " + message); }

    public void updateEventList(String event) {
        SwingUtilities.invokeLater(() -> eventListArea.append(event + "\n"));
    }

    public void updateServerStatus(String status) {
        SwingUtilities.invokeLater(() -> serverStatusLabel.setText("Server: " + status));
    }

    public void incrementActiveFires() {
        SwingUtilities.invokeLater(() -> {
            activeFireCount++;
            activeFireLabel.setText("Active Fires: " + activeFireCount);
        });
    }

    public void decrementActiveFires() {
        SwingUtilities.invokeLater(() -> {
            if (activeFireCount > 0) activeFireCount--;
            activeFireLabel.setText("Active Fires: " + activeFireCount);
        });
    }

    public int getActiveFireCount() {
        return activeFireCount;
    }

    public void updateDroneMarker(int droneId, String status, double posX, double posY, double waterRemaining, int zoneId, String severity) {
        droneMarkers.put(droneId, new DroneMarker(droneId, status, posX, posY, waterRemaining, zoneId, severity));

        // Track active fire severity per zone
        if (zoneId > 0) {
            if ("NONE".equals(severity)) {
                activeFireSeverity.remove(zoneId);
            } else {
                activeFireSeverity.put(zoneId, severity);
            }
        }

        SwingUtilities.invokeLater(() -> {
            mapPanel.setDroneMarkers(droneMarkers);
            mapPanel.setActiveFireSeverity(activeFireSeverity);
            log(String.format("[GUI] Drone %d : %s  pos=(%.0f,%.0f)  water=%.1fL  zone=%d  sev=%s",
                    droneId, status, posX, posY, waterRemaining, zoneId, severity));
            if ("COMPLETED".equals(status)) decrementActiveFires();
        });
    }

    public static class DroneMarker {
        public final int droneId;
        public final String status, severity;
        public final double posX, posY, waterRemaining;
        public final int zoneId;

        DroneMarker(int droneId, String status, double posX, double posY, double waterRemaining, int zoneId, String severity) {
            this.droneId = droneId; this.status = status;
            this.posX = posX; this.posY = posY;
            this.waterRemaining = waterRemaining;
            this.zoneId = zoneId; this.severity = severity;
        }
    }

    class ZoneMapPanel extends JPanel {
        private List<FireIncidentZone> zones = new ArrayList<>();
        private Map<Integer, DroneMarker> markers = new HashMap<>();
        private Map<Integer, String> fireSeverity = new HashMap<>();
        private static final int PAD   = 30;
        private static final int WORLD = 2000;

        void setZones(List<FireIncidentZone> z)                { this.zones = z; repaint(); }
        void setDroneMarkers(Map<Integer, DroneMarker> m)      { this.markers = new HashMap<>(m); repaint(); }
        void setActiveFireSeverity(Map<Integer, String> s)     { this.fireSeverity = new HashMap<>(s); repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth()  - 2 * PAD;
            int h = getHeight() - 2 * PAD;

            // Grid
            g2.setColor(new Color(180, 180, 180));
            for (int i = 0; i <= 8; i++) {
                g2.drawLine(PAD + i * w / 8, PAD, PAD + i * w / 8, PAD + h);
                g2.drawLine(PAD, PAD + i * h / 8, PAD + w, PAD + i * h / 8);
            }

            // Zones — fill colour depends on active fire severity
            for (FireIncidentZone z : zones) {
                int px1 = PAD + z.getX1() * w / WORLD, py1 = PAD + z.getY1() * h / WORLD;
                int px2 = PAD + z.getX2() * w / WORLD, py2 = PAD + z.getY2() * h / WORLD;
                int rx = Math.min(px1, px2), ry = Math.min(py1, py2);
                int rw = Math.abs(px2 - px1), rh = Math.abs(py2 - py1);

                // Fill by severity
                String sev = fireSeverity.get(z.getZoneId());
                Color fill = severityFill(sev);
                g2.setColor(fill);
                g2.fillRect(rx, ry, rw, rh);

                // Border
                g2.setColor(Color.DARK_GRAY);
                g2.setStroke(new BasicStroke(sev != null ? 2.5f : 1.5f));
                g2.drawRect(rx, ry, rw, rh);

                // Zone ID
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Arial", Font.BOLD, 11));
                g2.drawString("Z" + z.getZoneId(), rx + 4, ry + 14);

            }

            // Drone markers (triangles)
            for (DroneMarker m : markers.values()) {
                int dx = PAD + (int)(m.posX * w / WORLD);
                int dy = PAD + (int)(m.posY * h / WORLD);

                // Triangle
                int[] xs = { dx, dx - 8, dx + 8 };
                int[] ys = { dy - 10, dy + 7, dy + 7 };
                g2.fillPolygon(xs, ys, 3);
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(1));
                g2.drawPolygon(xs, ys, 3);
            }
        }

        // live update the colour of the zone based on the fire (or none)
        private Color severityFill(String severity) {
            if (severity == null) return new Color(173, 216, 230);
            return switch (severity.toUpperCase()) {
                case "HIGH"     -> new Color(255, 100, 100);
                case "MODERATE" -> new Color(255, 180,  60);
                case "LOW"      -> new Color(255, 255, 180);
                default         -> new Color(173, 216, 230);
            };
        }
    }

    public void updateZoneFire(int zoneId, String severity) {
        SwingUtilities.invokeLater(() -> {
            activeFireSeverity.put(zoneId, severity);
            mapPanel.setActiveFireSeverity(activeFireSeverity);
        });
    }

}