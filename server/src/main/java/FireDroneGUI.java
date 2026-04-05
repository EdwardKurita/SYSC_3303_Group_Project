import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class FireDroneGUI extends JFrame {
    private JTextArea  logArea;          // Top-left: system log
    private JTextArea  faultLogArea;     // Top-right: fault log (replaces fire events sent)
    private JLabel     serverStatusLabel;
    private JLabel     activeFireLabel;
    private ZoneMapPanel mapPanel;       // Bottom: map full width

    private final Queue<String> logQueue = new LinkedList<>();
    private final Object logLock = new Object();

    private final Map<Integer, DroneMarker> droneMarkers = new ConcurrentHashMap<>();
    private final Map<Integer, String> activeFireSeverity = new ConcurrentHashMap<>();

    private int activeFireCount = 0;

    private JTextArea metricsArea;           // Panel to show live metrics
    private JPanel metricsPanel;              // Container for metrics
    private int completedFires = 0;           // Counter for completed fires

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
        JPanel statusPanel = new JPanel(new GridLayout(1, 4));// Changed from 3 to 4 columns
        JLabel titleLabel = new JLabel("Fire Incident Subsystem (Server)", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        serverStatusLabel = new JLabel("Server: STARTING", JLabel.CENTER);
        serverStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        activeFireLabel = new JLabel("Active Fires: 0", JLabel.CENTER);
        activeFireLabel.setFont(new Font("Arial", Font.BOLD, 14));

        // Add completed fires label
        JLabel completedLabel = new JLabel("Completed: 0", JLabel.CENTER);
        completedLabel.setFont(new Font("Arial", Font.BOLD, 14));
        completedLabel.setName("completedLabel");  // So we can update it later

        statusPanel.add(titleLabel);
        statusPanel.add(serverStatusLabel);
        statusPanel.add(activeFireLabel);
        statusPanel.add(completedLabel);
        add(statusPanel, BorderLayout.NORTH);

        // Center: system log (left) + fault log (right)
        // iter5: system log (left) + fault log (right) + metrics

        JPanel centerPanel = new JPanel(new GridLayout(1, 3));// Changed from 2 to 3 columns

        // Left: System Log
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("System Log"));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Right: Fault Log
        JPanel faultPanel = new JPanel(new BorderLayout());
        faultPanel.setBorder(BorderFactory.createTitledBorder("Fault Log"));
        faultLogArea = new JTextArea();
        faultLogArea.setEditable(false);
        faultLogArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        faultPanel.add(new JScrollPane(faultLogArea), BorderLayout.CENTER);

        // Metrics Panel on the right
        JPanel metricsPanelContainer = new JPanel(new BorderLayout());
        metricsPanelContainer.setBorder(BorderFactory.createTitledBorder("Live Metrics"));
        metricsArea = new JTextArea(8, 25);
        metricsArea.setEditable(false);
        metricsArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        metricsArea.setText("Waiting for data...\n");
        metricsPanelContainer.add(new JScrollPane(metricsArea), BorderLayout.CENTER);

        centerPanel.add(logPanel);
        centerPanel.add(faultPanel);
        centerPanel.add(metricsPanelContainer);
        add(centerPanel, BorderLayout.CENTER);

        // South: map full width (removed zone list panel)
        JPanel bottomPanel = new JPanel(new GridLayout(1, 1));
        bottomPanel.setPreferredSize(new Dimension(1200, 360));

        mapPanel = new ZoneMapPanel();
        mapPanel.setBorder(BorderFactory.createTitledBorder("Zone Map"));
        bottomPanel.add(mapPanel);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadZones(List<FireIncidentZone> zones) {
        // only set zones on map panel, no list needed
        mapPanel.setZones(zones);
    }

    private void startLogProcessor() {
        Thread t = new Thread(() -> {
            while (true) {
                String msg;
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

    // ===== LOGGING =====
    public void log(String message) {
        synchronized (logLock) {
            logQueue.offer(message);
            logLock.notifyAll();
        }
    }

    public void logError(String message) { log("[ERROR] " + message); }

    public void updateServerStatus(String status) {
        SwingUtilities.invokeLater(() -> serverStatusLabel.setText("Server: " + status));
    }

    // ===== ACTIVE FIRE COUNT =====
    public void incrementActiveFires() {
        SwingUtilities.invokeLater(() -> {
            activeFireCount++;
            activeFireLabel.setText("Active Fires: " + activeFireCount);
        });
    }

    public void decrementActiveFires() {
        SwingUtilities.invokeLater(() -> {
            if (activeFireCount > 0) {
                activeFireCount--;
                completedFires++;
            }
            activeFireLabel.setText("Active Fires: " + activeFireCount);

            // Find the completed label and update its text
            Component[] components = ((JPanel)getContentPane().getComponent(0)).getComponents();
            for (Component comp : components) {
                if (comp instanceof JLabel && "completedLabel".equals(comp.getName())) {
                    ((JLabel) comp).setText("Completed: " + completedFires);
                    break;
                }
            }
        });
    }

    /**
     * Updates the metrics display panel with current system stats
     * Called periodically by scheduler or when drone status changes
     */
    public void updateMetricsDisplay(int activeFires, Map<Integer, Double> droneUtilization) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== SYSTEM STATUS ===\n");
            sb.append("Active Fires: ").append(activeFires).append("\n");
            sb.append("Completed Fires: ").append(completedFires).append("\n");
            sb.append("\n=== DRONE UTILIZATION ===\n");

            if (droneUtilization.isEmpty()) {
                sb.append("No drone data yet\n");
            } else {
                for (Map.Entry<Integer, Double> entry : droneUtilization.entrySet()) {
                    String bar = createProgressBar(entry.getValue());
                    sb.append(String.format("Drone %d: %5.1f%% %s\n",
                            entry.getKey(), entry.getValue(), bar));
                }
            }

            sb.append("\n=== LEGEND ===\n");
            sb.append("Blue: Normal drone\n");
            sb.append("Yellow: Stuck drone\n");
            sb.append("Red: Nozzle jammed\n");
            sb.append("Orange: Packet loss\n");

            metricsArea.setText(sb.toString());
        });
    }

    /**
     * Helper method to create a text-based progress bar
     */
    private String createProgressBar(double percent) {
        int barLength = 20;
        int filled = (int)(percent / 100 * barLength);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                bar.append("=");
            } else if (i == filled) {
                bar.append(">");
            } else {
                bar.append(" ");
            }
        }
        bar.append("]");
        return bar.toString();
    }

    public int getActiveFireCount() { return activeFireCount; }

    // ===== DRONE UPDATES =====
    public void updateDroneMarker(int droneId, String status, double posX, double posY,
                                  double waterRemaining, int zoneId, String severity,
                                  String faultType) {

        droneMarkers.put(droneId, new DroneMarker(droneId, status, posX, posY,
                waterRemaining, zoneId, severity, faultType));

        // Track active fire severity per zone
        if (zoneId > 0) {
            if ("COMPLETED".equals(status)) {
                // only clear zone colour on COMPLETED
                activeFireSeverity.remove(zoneId);
            } else if (!"RETURNING".equals(status)
                    && !"RETURNED".equals(status)
                    && !"NONE".equals(severity)) {
                activeFireSeverity.put(zoneId, severity);
            }
        }

        SwingUtilities.invokeLater(() -> {
            mapPanel.setDroneMarkers(droneMarkers);
            mapPanel.setActiveFireSeverity(activeFireSeverity);

            log(String.format("[GUI] Drone %d : %s pos=(%.0f,%.0f) water=%.1fL zone=%d sev=%s fault=%s",
                    droneId, status, posX, posY, waterRemaining, zoneId, severity, faultType));

            // log faults to fault log panel
            if (!"NONE".equals(faultType)) {
                String timestamp = new java.text.SimpleDateFormat("HH:mm:ss")
                        .format(new java.util.Date());
                faultLogArea.append(String.format("[%s] Drone %d — %s (%s)\n",
                        timestamp, droneId, faultType, status));
                faultLogArea.setCaretPosition(faultLogArea.getDocument().getLength());
            }

            if ("COMPLETED".equals(status)) decrementActiveFires();
        });
    }

    // ===== DRONE MARKER CLASS =====
    public static class DroneMarker {
        public final int droneId;
        public final String status, severity, faultType;
        public final double posX, posY, waterRemaining;
        public final int zoneId;

        DroneMarker(int droneId, String status, double posX, double posY,
                    double waterRemaining, int zoneId, String severity, String faultType) {
            this.droneId        = droneId;
            this.status         = status;
            this.posX           = posX;
            this.posY           = posY;
            this.waterRemaining = waterRemaining;
            this.zoneId         = zoneId;
            this.severity       = severity;
            this.faultType      = faultType;
        }
    }

    // ===== ZONE MAP PANEL =====
    class ZoneMapPanel extends JPanel {
        private List<FireIncidentZone> zones = new ArrayList<>();
        private Map<Integer, DroneMarker> markers = new HashMap<>();
        private Map<Integer, String> fireSeverity = new HashMap<>();
        private static final int PAD   = 30;
        private static final int WORLD = 2000;

        void setZones(List<FireIncidentZone> z)                { zones = z; repaint(); }
        void setDroneMarkers(Map<Integer, DroneMarker> m)      { markers = new HashMap<>(m); repaint(); }
        void setActiveFireSeverity(Map<Integer, String> s)     { fireSeverity = new HashMap<>(s); repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth()  - 2 * PAD;
            int h = getHeight() - 2 * PAD;

            // Draw grid
            g2.setColor(new Color(180, 180, 180));
            for (int i = 0; i <= 8; i++) {
                g2.drawLine(PAD + i * w / 8, PAD, PAD + i * w / 8, PAD + h);
                g2.drawLine(PAD, PAD + i * h / 8, PAD + w, PAD + i * h / 8);
            }

            // Draw zones — fill colour depends on active fire severity
            for (FireIncidentZone z : zones) {
                int px1 = PAD + z.getX1() * w / WORLD, py1 = PAD + z.getY1() * h / WORLD;
                int px2 = PAD + z.getX2() * w / WORLD, py2 = PAD + z.getY2() * h / WORLD;
                int rx = Math.min(px1, px2), ry = Math.min(py1, py2);
                int rw = Math.abs(px2 - px1),  rh = Math.abs(py2 - py1);

                // Fill by severity
                String sev = fireSeverity.get(z.getZoneId());
                g2.setColor(severityFill(sev));
                g2.fillRect(rx, ry, rw, rh);

                // Border
                g2.setColor(Color.DARK_GRAY);
                g2.setStroke(new BasicStroke(sev != null ? 2.5f : 1.5f));
                g2.drawRect(rx, ry, rw, rh);

                // Zone ID label
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Arial", Font.BOLD, 11));
                g2.drawString("Z" + z.getZoneId(), rx + 4, ry + 14);
            }

            // Draw drones — colour by fault type
            for (DroneMarker m : markers.values()) {
                int dx = PAD + (int)(m.posX * w / WORLD);
                int dy = PAD + (int)(m.posY * h / WORLD);

                // Fill triangle with fault colour
                g2.setColor(droneColor(m.faultType));
                int[] xs = { dx, dx - 8, dx + 8 };
                int[] ys = { dy - 10, dy + 7, dy + 7 };
                g2.fillPolygon(xs, ys, 3);

                // Black outline
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(1));
                g2.drawPolygon(xs, ys, 3);

                // Drone ID label
                g2.setFont(new Font("Arial", Font.BOLD, 10));
                g2.drawString("D" + m.droneId, dx + 10, dy);
            }
        }

        // Zone fill colour based on fire severity
        private Color severityFill(String severity) {
            if (severity == null) return new Color(173, 216, 230);
            return switch (severity.toUpperCase()) {
                case "HIGH"     -> new Color(255, 100, 100);
                case "MODERATE" -> new Color(255, 180,  60);
                case "LOW"      -> new Color(255, 255, 180);
                default         -> new Color(173, 216, 230);
            };
        }

        // Drone colour based on fault type
        private Color droneColor(String faultType) {
            if (faultType == null || "NONE".equals(faultType))
                return new Color(30, 144, 255);     // normal — blue
            return switch (faultType.toUpperCase()) {
                case "DRONE_STUCK"   -> new Color(255, 180, 0);   // amber
                case "NOZZLE_JAMMED" -> new Color(220, 50, 50);   // red
                case "PACKET_LOSS"   -> new Color(255, 120, 0);   // orange
                default              -> new Color(128, 0, 128);   // purple fallback
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
