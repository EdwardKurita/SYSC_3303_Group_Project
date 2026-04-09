import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.text.SimpleDateFormat;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * This class tracks all the performance metrics for our drone system
 * I made this to calculate response times and drone utilization for the final report
 *
 * @author Group 12 - Jiayi, Declan, Shael, Edward
 */
public class PerformanceMetrics {
    // Formatter for timestamps so logs look consistent
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss.SSS");

    // Maps to store when fires were detected vs extinguished
    // Used ConcurrentHashMap to let multiple threads access this
    private final Map<Integer, Long> fireDetectedTime = new ConcurrentHashMap<>();
    private final Map<Integer, Long> fireExtinguishedTime = new ConcurrentHashMap<>();

    // Track dispatch and return times for each drone
    private final Map<Integer, List<Long>> droneDispatchTimes = new ConcurrentHashMap<>();
    private final Map<Integer, List<Long>> droneReturnTimes = new ConcurrentHashMap<>();

    // Flight and idle time tracking
    private final Map<Integer, Long> droneTotalFlightTime = new ConcurrentHashMap<>();
    private final Map<Integer, Long> droneTotalIdleTime = new ConcurrentHashMap<>();
    private final Map<Integer, Long> droneLastActiveTime = new ConcurrentHashMap<>();

    // Overall simulation timing
    private long firstFireTime = Long.MAX_VALUE;  // Start with max value so first fire updates it
    private long lastFireExtinguishedTime = 0;

    // Store which zone each fire belongs to - needed for calculating response times
    private final Map<Long, Integer> timeToZoneMap = new ConcurrentHashMap<>();

    // File logging — writer is opened once and reused for all log calls
    private final BufferedWriter logWriter;
    private final String logFilePath;
    /**
     * Constructor that accepts an explicit log file path.
     * Useful for tests or when you want a fixed filename.
     */
    public PerformanceMetrics() {
        this.logFilePath = "metrics_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".log";

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(logFilePath, true)); // append=true so reruns add to same file
            String header = "=".repeat(60) + "\n"
                    + "METRICS LOG STARTED: " + formatTime(System.currentTimeMillis()) + "\n"
                    + "=".repeat(60);
            writer.write(header);
            writer.newLine();
            writer.flush();
            System.out.println("[METRICS] Logging to file: " + logFilePath);
        } catch (IOException e) {
            System.err.println("[METRICS] WARNING: Could not open log file '" + logFilePath + "': " + e.getMessage());
        }
        this.logWriter = writer;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Writes a line to both stdout and the log file.
     * Thread-safe: synchronized on logWriter.
     */
    private void log(String message) {
        System.out.println(message);
        if (logWriter != null) {
            synchronized (logWriter) {
                try {
                    logWriter.write(message);
                    logWriter.newLine();
                    logWriter.flush();
                } catch (IOException e) {
                    System.err.println("[METRICS] Log write failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Closes the log file. Call this once at the end of the simulation
     * (typically right after printFinalReport()).
     */
    public void closeLog() {
        if (logWriter != null) {
            synchronized (logWriter) {
                try {
                    logWriter.write("=".repeat(60));
                    logWriter.newLine();
                    logWriter.write("METRICS LOG CLOSED: " + formatTime(System.currentTimeMillis()));
                    logWriter.newLine();
                    logWriter.write("=".repeat(60));
                    logWriter.newLine();
                    logWriter.flush();
                    logWriter.close();
                    System.out.println("[METRICS] Log file closed: " + logFilePath);
                } catch (IOException e) {
                    System.err.println("[METRICS] Could not close log file: " + e.getMessage());
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Public event methods (unchanged API, now log to file as well)
    // ------------------------------------------------------------------

    /**
     * Called when a fire is first detected by the system
     * Records the timestamp so we can calculate response time later
     */
    public void logFireDetected(int zoneId, String severity) {
        long now = System.currentTimeMillis();
        fireDetectedTime.put(zoneId, now);
        timeToZoneMap.put(now, zoneId);

        if (now < firstFireTime) {
            firstFireTime = now;
        }

        log("[METRICS] Fire detected at Zone " + zoneId + " (" + severity + ") at " + formatTime(now));
    }

    /**
     * Called when a fire is completely extinguished
     * Calculates how long it took from detection to extinguishing
     */
    public void logFireExtinguished(int zoneId) {
        long now = System.currentTimeMillis();
        fireExtinguishedTime.put(zoneId, now);

        if (now > lastFireExtinguishedTime) {
            lastFireExtinguishedTime = now;
        }

        Long detectedTime = fireDetectedTime.get(zoneId);
        if (detectedTime != null) {
            long responseTime = now - detectedTime;
            log("[METRICS] Fire at Zone " + zoneId +
                    " extinguished! Response time: " + responseTime + "ms");
        } else {
            log("[METRICS] Fire at Zone " + zoneId +
                    " extinguished! (detection time not recorded)");
        }
    }

    /**
     * Called when scheduler dispatches a drone to a fire
     * Helps track how many missions each drone does
     */
    public void logDroneDispatched(int droneId, int zoneId) {
        long now = System.currentTimeMillis();

        // Add this dispatch time to the list for this drone
        droneDispatchTimes.computeIfAbsent(droneId, k -> new ArrayList<>()).add(now);

        // Update when this drone was last active
        droneLastActiveTime.put(droneId, now);

        log("[METRICS] Drone " + droneId +
                " dispatched to Zone " + zoneId + " at " + formatTime(now));
    }

    /**
     * Called when drone returns to base
     * Calculates flight time for the mission and idle time before next mission
     */
    public void logDroneReturned(int droneId) {
        long now = System.currentTimeMillis();

        // Record return time
        droneReturnTimes.computeIfAbsent(droneId, k -> new ArrayList<>()).add(now);

        // Calculate flight time for the most recent mission
        List<Long> dispatchList = droneDispatchTimes.get(droneId);
        if (dispatchList != null && !dispatchList.isEmpty()) {
            long lastDispatch = dispatchList.get(dispatchList.size() - 1);
            long flightTime = now - lastDispatch;
            droneTotalFlightTime.merge(droneId, flightTime, Long::sum);
        }

        // Calculate how long this drone was idle before this mission
        Long lastActive = droneLastActiveTime.get(droneId);
        if (lastActive != null && lastActive < now) {
            long idleTime = now - lastActive;
            if (idleTime > 0) {
                droneTotalIdleTime.merge(droneId, idleTime, Long::sum);
            }
        }

        // Update last active time to now (drone is back at base)
        droneLastActiveTime.put(droneId, now);

        log("[METRICS] Drone " + droneId + " returned to base at " + formatTime(now));
    }

    /**
     * Called when drone becomes idle and is waiting
     */
    public void logDroneIdle(int droneId) {
        droneLastActiveTime.put(droneId, System.currentTimeMillis());
        log("[METRICS] Drone " + droneId + " is now IDLE");
    }

    /**
     * Prints formatted report of all metrics at the end of simulation.
     * Also writes the full report to the log file.
     * Call closeLog() after this to flush and close the file.
     */
    public void printFinalReport() {
        log("\n" + "=".repeat(60));
        log("PERFORMANCE METRICS REPORT");
        log("=".repeat(60));

        // Overall timing metrics
        long totalSimulationTime = lastFireExtinguishedTime - firstFireTime;
        log("\n--- OVERALL STATISTICS ---");
        log("First fire detected: " + formatTime(firstFireTime));
        log("Last fire extinguished: " + formatTime(lastFireExtinguishedTime));
        log("Total simulation time: " + totalSimulationTime + "ms");
        log("Total fires extinguished: " + fireExtinguishedTime.size());

        // Per-drone metrics
        log("\n--- DRONE PERFORMANCE ---");

        // If no drones have flight time recorded, show message
        if (droneTotalFlightTime.isEmpty()) {
            log("No drone flight data recorded yet.");
        } else {
            for (int droneId : droneTotalFlightTime.keySet()) {
                long flightTime = droneTotalFlightTime.getOrDefault(droneId, 0L);
                long idleTime = droneTotalIdleTime.getOrDefault(droneId, 0L);
                int missions = droneDispatchTimes.getOrDefault(droneId, new ArrayList<>()).size();

                log("\nDrone " + droneId + ":");
                log("  Missions completed: " + missions);
                log("  Total flight time: " + flightTime + "ms");
                log("  Total idle time: " + idleTime + "ms");

                // Calculate utilization percentage (how busy the drone was)
                long totalTime = flightTime + idleTime;
                if (totalTime > 0) {
                    double utilization = (double) flightTime / totalTime * 100;
                    log(String.format("  Utilization: %.1f%%", utilization));
                } else {
                    log("  Utilization: N/A (no time data)");
                }
            }
        }

        // Fire response times
        log("\n--- FIRE RESPONSE TIMES ---");
        if (fireExtinguishedTime.isEmpty()) {
            log("No fires were extinguished.");
        } else {
            long totalResponseTime = 0;
            int fireCount = 0;

            for (Map.Entry<Integer, Long> entry : fireExtinguishedTime.entrySet()) {
                int zoneId = entry.getKey();
                Long detectedTime = fireDetectedTime.get(zoneId);
                if (detectedTime != null) {
                    long responseTime = entry.getValue() - detectedTime;
                    log("Zone " + zoneId + ": " + responseTime + "ms");
                    totalResponseTime += responseTime;
                    fireCount++;
                } else {
                    log("Zone " + zoneId + ": detection time missing");
                }
            }

            if (fireCount > 0) {
                double avgResponseTime = (double) totalResponseTime / fireCount;
                log(String.format("\nAverage response time: %.2fms", avgResponseTime));
            }
        }

        log("=".repeat(60));

        // Remind the caller to close the log
        closeLog();
    }

    public Map<Integer, Double> getUtilization() {
        Map<Integer, Double> result = new HashMap<>();
        for (int droneId : droneTotalFlightTime.keySet()) {
            long flightTime = droneTotalFlightTime.getOrDefault(droneId, 0L);
            long idleTime = droneTotalIdleTime.getOrDefault(droneId, 0L);
            long total = flightTime + idleTime;
            double utilization = (total > 0) ? (double) flightTime / total * 100.0 : 0.0;
            result.put(droneId, utilization);
        }
        return result;
    }

    /**
     * Helper method to format milliseconds into readable time
     */
    private String formatTime(long timeMs) {
        return TS.format(new Date(timeMs));
    }
}