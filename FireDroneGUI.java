import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.Queue;

//FireDroneGUI provides a thread-safe GUI for the firefighting drone system
//Uses synchronized methods and wait/notify for thread-safe logging, demonstrating synchronization concepts
public class FireDroneGUI extends JFrame {
    // GUI Components
    private JTextArea logArea; // Display system message
    private JTextArea eventListArea; // Shows fire events
    private JLabel droneStatusLabel; // Shows drone status
    private JLabel schedulerStatusLabel; // Shows scheduler status
    private JList<String> activeZonesList; // Shows active zone
    private DefaultListModel<String> zonesModel; // Data model for list

    // Thread-safe logging queue using synchronized methods
    private Queue<String> logQueue;
    private final Object logLock = new Object(); // Lock object for synchronization

    //Iteration 2
    private JLabel activeFireLabel;
    private int activeFireCount = 0;

    // Constructor (sets up the GUI) window
    public FireDroneGUI() {
        setTitle("Firefighting Drone System - Iteration 1");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        logQueue = new LinkedList<>(); // Regular queue with synchronized access
        setupGUI();
        startLogProcessor();
        setVisible(true);
    }

    // Creates and arrange all GUI components
    private void setupGUI() {
        // Top panel: status labels
        JPanel statusPanel = new JPanel(new GridLayout(2, 1));
        droneStatusLabel = new JLabel("Drone Status: IDLE", JLabel.CENTER);
        droneStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        schedulerStatusLabel = new JLabel("Scheduler Status: Ready", JLabel.CENTER);
        schedulerStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        //Iteration 2
        activeFireLabel = new JLabel("Active Fires: 0", JLabel.CENTER);
        activeFireLabel.setFont(new Font("Arial", Font.BOLD, 14));

        statusPanel.add(droneStatusLabel);
        statusPanel.add(schedulerStatusLabel);
        statusPanel.add(activeFireLabel);
        add(statusPanel, BorderLayout.NORTH);

        // Center panel: log and events
        JPanel centerPanel = new JPanel(new GridLayout(1, 2));

        // Left side: System log
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("System Log"));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Right side: fire events
        JPanel eventPanel = new JPanel(new BorderLayout());
        eventPanel.setBorder(BorderFactory.createTitledBorder("Fire Events"));
        eventListArea = new JTextArea();
        eventListArea.setEditable(false);
        eventPanel.add(new JScrollPane(eventListArea), BorderLayout.CENTER);

        // Add both panels to center
        centerPanel.add(logPanel);
        centerPanel.add(eventPanel);
        add(centerPanel, BorderLayout.CENTER);

        // Bottom panel: active zones
        JPanel zonesPanel = new JPanel(new BorderLayout());
        zonesPanel.setBorder(BorderFactory.createTitledBorder("Active Zones"));
        zonesModel = new DefaultListModel<>();
        activeZonesList = new JList<>(zonesModel);
        zonesPanel.add(new JScrollPane(activeZonesList), BorderLayout.CENTER);
        add(zonesPanel, BorderLayout.SOUTH);
    }

    //Starts background thread to process log messages
    private void startLogProcessor() {
        Thread logProcessor = new Thread(() -> {
            while (true) {
                String message = null;

                // Synchronized block to safely take from queue
                synchronized (logLock) {
                    // Wait while queue is empty (busy-wait prevention)
                    while (logQueue.isEmpty()) {
                        try {
                            logLock.wait(); // Releases lock and waits for notification
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return; // Exit thread if interrupted
                        }
                    }
                    // Queue is not empty, retrieve message
                    message = logQueue.poll();
                }

                // Update GUI on Event Dispatch Thread (EDT)
                if (message != null) {
                    String finalMessage = message;
                    SwingUtilities.invokeLater(() -> {
                        logArea.append(finalMessage + "\n");
                        // Auto-scroll to bottom
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    });
                }
            }
        });

        logProcessor.setDaemon(true); // Thread stops when program ends
        logProcessor.start();
    }

    // All public methods for other classed to update GUI
    //Add a message to the system log (thread-safe).
    public void log(String message) {
        synchronized (logLock) {
            logQueue.offer(message); // Add message to queue
            logLock.notifyAll(); // Wake up the log processor thread
        }
    }

    // Adds an error message to the log
    public void logError(String message) {
        log("[ERROR] " + message);
    }

    // Updates the fire events list (thread-safe GUI update)
    public void updateEventList(String event) {
        SwingUtilities.invokeLater(() -> {
            eventListArea.append(event + "\n");
        });
    }

    // Updates the drone status label
    public void updateDroneStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            droneStatusLabel.setText("Drone Status: " + status);
        });
    }

    // Update drone status from a DroneResponse obj
    public void updateDroneStatus(DroneResponse response) {
        SwingUtilities.invokeLater(() -> {
            droneStatusLabel.setText("Drone: " + response.getStatus() + " - Zone " + response.getZoneId());
        });
    }

    // Updates the scheduler status lable
    public void updateSchedulerStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            schedulerStatusLabel.setText("Scheduler: " + status);
        });
    }

    // Add a zone to the active zones list
    public void addActiveZone(String zoneInfo) {
        SwingUtilities.invokeLater(() -> {
            zonesModel.addElement(zoneInfo);
        });
    }

    // Remove a zone from the active zones list
    public void removeActiveZone(String zoneInfo) {
        SwingUtilities.invokeLater(() -> {
            zonesModel.removeElement(zoneInfo);
        });
    }

    public void incrementActiveFires() {
        SwingUtilities.invokeLater(() -> {
            activeFireCount++;
            activeFireLabel.setText("Active Fire: " + activeFireCount);
        });
    }

    public void decrementActiveFires() {
        SwingUtilities.invokeLater(() -> {
            activeFireCount--;
            activeFireLabel.setText("Active Fire: " + activeFireCount);
        });
    }
}