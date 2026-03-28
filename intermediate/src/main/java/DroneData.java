import main.java.DroneState;

public class DroneData{
    private final int droneId;
    private DroneState state;
    private double currentWater;
    private int currentZone;
    private FireEvent currentMission;
    private double posX, posY;
    private String faultType = "NONE";
    private long lastSeenMs  = System.currentTimeMillis();

    private static final double TANK_CAPACITY = 15.0;

    //constructor
    public DroneData(int droneId) {
        this.droneId = droneId;
        this.state = DroneState.IDLE;
        this.currentWater = TANK_CAPACITY;
        this.currentZone = 0;
        this.currentMission = null;
        this.posX = 0;
        this.posY = 0;
    }

    //getters and setters
    public int getDroneId() {return droneId;}

    public DroneState getState() {return state;}
    public void setState(DroneState state) {this.state = state;}

    public double getCurrentWater() {return currentWater;}

    public int getCurrentZone() {return currentZone;}

    public FireEvent getCurrentMission() {return currentMission;}
    public void setCurrentMission(FireEvent currentMission) {
        this.currentMission = currentMission;
    }

    public double getPosX() {return posX;}
    public double getPosY() {return posY;}

    public String getFaultType() { return faultType; }
    public void setFaultType(String faultType) {
        this.faultType = faultType != null ? faultType : "NONE";
    }
    public long getLastSeenMs() { return lastSeenMs; }
    public void updateLastSeen() {
        this.lastSeenMs = System.currentTimeMillis();
    }
    public boolean isHardFault() {
        return "NOZZLE_JAMMED".equals(faultType);
    }
    public boolean isSoftFault() {
        return "DRONE_STUCK".equals(faultType) || "PACKET_LOSS".equals(faultType);
    }

    //HELPER FUNCTIONS

    //check if the drone is available for new assignment
    public boolean isAvailable() {
        return state == DroneState.IDLE;
    }

    //check if the drone has enough water
    public boolean hasEnoughWater(double waterNeeded) {
        return currentWater >= waterNeeded;
    }

    //refills the drone water
    public void refill() {
        this.currentWater = TANK_CAPACITY;
    }

    //uses the water to extinguish fire
    public void useWater(double amount) {
        this.currentWater -= amount;
        if (this.currentWater < 0) {
            this.currentWater = 0;
        }
    }

    //resets the drone to base
    public void returnToBase() {
        this.state = DroneState.IDLE;
        this.currentZone = 0;
        this.currentMission = null;
        this.faultType = "NONE"; // clear any fault so drone re-enters fleet cleanly
        refill();
    }


    //updates the drones data from DroneResponse message
    // now response is parsed from a packet, so instead of putting it into a response object it's just an array
    // [droneId, zoneId, status, message, waterUsed, posX, posY]
    public void updateFromResponse(String[] fields) {
        posX =  Double.parseDouble(fields[6]);
        posY =  Double.parseDouble(fields[7]);

        if (fields.length > 8) {
            setFaultType(fields[8]);
        }
        updateLastSeen();

        switch (fields[2]) {
            case "EN_ROUTE":
                this.state = DroneState.EN_ROUTE;
                this.currentZone = Integer.parseInt(fields[1]);
                break;

            case "ARRIVED":
                this.state = DroneState.ARRIVED;
                break;

            case "EXTINGUISHING":
                this.state = DroneState.DROPPING_AGENT;
                break;

            case "COMPLETED":
                this.state = DroneState.COMPLETED;
                useWater(Double.parseDouble(fields[4]));
                break;

            case "PARTIAL":
                this.state = DroneState.PARTIAL;
                useWater(Double.parseDouble(fields[4]));
                break;

            case "RETURNING":
                this.state = DroneState.RETURNING;
                break;

            case "RETURNED":
                returnToBase();
                break;

            case "FAULTED":
            case "DRONE_STUCK":
            case "NOZZLE_JAMMED":
                this.state = DroneState.FAULTED;
                break;

            case "ERROR":
                System.out.println("ERROR HAS OCCURRED WITH droneData");
                break;

        }
    }

    @Override
    public String toString() {
        return String.format("Drone %d: %s, WaterAmount: %.1fL, Zone: %d, Fault: %s", droneId, state, currentWater, currentZone, faultType);
    }

}