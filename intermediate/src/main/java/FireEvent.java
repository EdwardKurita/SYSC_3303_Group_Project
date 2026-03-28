// This class represents a fire event detected by the system
public class FireEvent {
    private String time; // HH:MM:SS
    private int zoneId;
    private String eventType; // "FIRE_DETECTED" or "DRONE_REQUEST"
    private String severity; // "High" or "Moderate" or "Low"
    private String faultType;
    // Constructor
    public FireEvent (String time, int zoneId, String eventType, String severity, String faultType) {
        // Store the time, zoneId, event type and severity
        this.time = time;
        this.zoneId = zoneId;
        this.eventType = eventType;
        this.severity = severity;
        this.faultType = faultType != null ? faultType : "NONE";

    }

    // Getter method
    public String getTime() {return time;}
    public int getZoneId() {return zoneId;}
    public String getEventType() {return eventType;}
    public String getSeverity() {return severity;}
    public String getFaultType() { return faultType; }

    // Coverts HH:MM:SS time to total seconds for calculations
    public int getTimeInSeconds() {
        String[] parts = time.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2]);
        return hours * 3600 + minutes * 60 + seconds;
    }

    // Determines how much water is needed based on fire severity
    public double getWaterNeeded() {
        return switch (severity.toUpperCase()) {
            case "HIGH" -> 30.0;
            case "MODERATE" -> 20.0;
            case "LOW" -> 10.0;
            default ->  10.0;
        };
    }

    // Creates a readable string representation of the fire event
    @Override
    public String toString() {
        // Example output: "[14:03:15] Zone 3: FIRE_DETECTED (High)"
        return String.format ("[%s] Zone %d: %s (%s) [fault=%s]", time, zoneId, eventType, severity, faultType);
    }
}
