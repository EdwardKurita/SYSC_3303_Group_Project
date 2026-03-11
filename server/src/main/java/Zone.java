// This class represents a geographical zone where files can occur
public class Zone {
    private int zoneId;
    private int startX, startY;
    private int endX, endY;

    // Constructors: Creates a new zone with boundaries
    public Zone (int zoneId, int startX, int startY, int endX, int endY) {
        this.zoneId = zoneId;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    // Getter methods
    public int getZoneId() { return zoneId;}
    public int getStartX() { return startX;}
    public int getStartY() { return startY;}
    public int getEndX() { return endX;}
    public int getEndY() { return endY;}

    // Calculates the center point of the zone (where drone needs to go)
    public int getCenterX() { return (startX + endX) / 2;}
    public int getCenterY() { return (startY + endY) / 2;}

    // Calculates straight-line distance from base station (0,0) t0 zone center
    public double getDistanceFromBase() {
        int centerX = getCenterX();
        int centerY = getCenterY();
        return Math.sqrt(centerX * centerX + centerY * centerY);
    }

    // Creates readable string representation
    @Override
    public String toString() {
        // Example: "Zone 3: Center(1100,300) Distance: 1140.2m"
        return String.format("Zone %d: Center(%d, %d) Distance: %.1fm", zoneId, getCenterX(), getCenterY(), getDistanceFromBase());
    }
}
