// This class represent message sent from the drone back to the scheduler
public class DroneResponse {
    private int zoneId;
    private String status; // "EN_ROUTE", "ARRIVED", "EXTINGUISHING", "COMPLETED"
    private String message;
    private String timestamp;
    private double waterUsed;

    // Constructor
    public DroneResponse (int zoneId, String status, String message, String timestamp, double waterUsed) {
        this.zoneId = zoneId;
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
        this.waterUsed = waterUsed;
    }

    // Getter method
    public int getZoneId() { return zoneId;}
    public String getStatus() { return status;}
    public String getMessage() { return message;}
    public String getTimestamp() { return timestamp;}
    public double getWaterUsed() { return waterUsed;}

    // Creates a readable string for logging
    @Override
    public String toString() {
        // Example: "[14:03:15] Zone 3: EN_ROUTE - Traveling to Zone 3 (1140.2m) (Water: 0.0L)"
        return String.format("[%s] Zone %d: %s - %s (Water: %.1fL)", timestamp, zoneId, status, message, waterUsed);
    }
}
