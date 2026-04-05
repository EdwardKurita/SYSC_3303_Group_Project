import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.text.SimpleDateFormat;

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

        System.out.println("[METRICS] Fire detected at Zone " + zoneId +
                " (" + severity + ") at " + formatTime(now));
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
            System.out.println("[METRICS] Fire at Zone " + zoneId +
                    " extinguished! Response time: " + responseTime + "ms");
        } else {
            System.out.println("[METRICS] Fire at Zone " + zoneId +
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

        System.out.println("[METRICS] Drone " + droneId +
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

        System.out.println("[METRICS] Drone " + droneId + " returned to base at " + formatTime(now));
    }

    /**
     * Called when drone becomes idle and is waiting
     */
    public void logDroneIdle(int droneId) {
        droneLastActiveTime.put(droneId, System.currentTimeMillis());
        System.out.println("[METRICS] Drone " + droneId + " is now IDLE");
    }

    /**
     * Prints formatted report of all metrics at the end of simulation
     */
    public void printFinalReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("PERFORMANCE METRICS REPORT");
        System.out.println("=".repeat(60));

        // Overall timing metrics
        long totalSimulationTime = lastFireExtinguishedTime - firstFireTime;
        System.out.println("\n--- OVERALL STATISTICS ---");
        System.out.println("First fire detected: " + formatTime(firstFireTime));
        System.out.println("Last fire extinguished: " + formatTime(lastFireExtinguishedTime));
        System.out.println("Total simulation time: " + totalSimulationTime + "ms");
        System.out.println("Total fires extinguished: " + fireExtinguishedTime.size());

        // Per-drone metrics
        System.out.println("\n--- DRONE PERFORMANCE ---");

        // If no drones have flight time recorded, show message
        if (droneTotalFlightTime.isEmpty()) {
            System.out.println("No drone flight data recorded yet.");
        } else {
            for (int droneId : droneTotalFlightTime.keySet()) {
                long flightTime = droneTotalFlightTime.getOrDefault(droneId, 0L);
                long idleTime = droneTotalIdleTime.getOrDefault(droneId, 0L);
                int missions = droneDispatchTimes.getOrDefault(droneId, new ArrayList<>()).size();

                System.out.println("\nDrone " + droneId + ":");
                System.out.println("  Missions completed: " + missions);
                System.out.println("  Total flight time: " + flightTime + "ms");
                System.out.println("  Total idle time: " + idleTime + "ms");

                // Calculate utilization percentage (how busy the drone was)
                long totalTime = flightTime + idleTime;
                if (totalTime > 0) {
                    double utilization = (double) flightTime / totalTime * 100;
                    System.out.printf("  Utilization: %.1f%%\n", utilization);
                } else {
                    System.out.println("  Utilization: N/A (no time data)");
                }
            }
        }

        // Fire response times
        System.out.println("\n--- FIRE RESPONSE TIMES ---");
        if (fireExtinguishedTime.isEmpty()) {
            System.out.println("No fires were extinguished.");
        } else {
            long totalResponseTime = 0;
            int fireCount = 0;

            for (Map.Entry<Integer, Long> entry : fireExtinguishedTime.entrySet()) {
                int zoneId = entry.getKey();
                Long detectedTime = fireDetectedTime.get(zoneId);
                if (detectedTime != null) {
                    long responseTime = entry.getValue() - detectedTime;
                    System.out.println("Zone " + zoneId + ": " + responseTime + "ms");
                    totalResponseTime += responseTime;
                    fireCount++;
                } else {
                    System.out.println("Zone " + zoneId + ": detection time missing");
                }
            }

            if (fireCount > 0) {
                double avgResponseTime = (double) totalResponseTime / fireCount;
                System.out.printf("\nAverage response time: %.2fms\n", avgResponseTime);
            }
        }

        System.out.println("=".repeat(60));
    }

    public Map<Integer, Double> getUtilization(){
        Map<Integer, Double> result = new HashMap<>();
        for (int droneId : droneTotalFlightTime.keySet()) {
            long flightTime = droneTotalFlightTime.getOrDefault(droneId, 0L);
            long idleTime = droneTotalIdleTime.getOrDefault(droneId, 0L);
            long total = flightTime + idleTime;
            double utilization = (total > 0) ? (double) flightTime/total*100.0 : 0.0;
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
