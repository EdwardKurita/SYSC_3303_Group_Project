public class DroneData{
    private int droneId;
    private DroneState state;
    private double currentWater;
    private int currentZone;
    private FireEvent currentMission;


    private static final double TANK_CAPACITY = 15.0;

    //constructor
    public DroneData(int droneId) {
        this.droneId = droneId;
        this.state = DroneState.IDLE;
        this.currentWater = TANK_CAPACITY;
        this.currentZone = 0;
        this.currentMission = null;

    }

    //getters and setters
    public int getDroneId() {return droneId;}

    public DroneState getState() {return state;}
    public void setState(DroneState state) {this.state = state;}

    public double getCurrentWater() {return currentWater;}
    public void setCurrentWater(double currentWater) {this.currentWater = currentWater;}

    public int getCurrentZone() {return currentZone;}
    public void setCurrentZone(int currentZone) {this.currentZone = currentZone;}

    public FireEvent getCurrentMission() {return currentMission;}
    public void setCurrentMission(FireEvent currentMission) {
        this.currentMission = currentMission;
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
        refill();
    }


    //updates the drones data from DroneResponse message
    public void updateFromResponse(DroneResponse response) {
        switch (response.getStatus()) {
            case "EN_ROUTE":
                this.state = DroneState.EN_ROUTE;
                this.currentZone = response.getZoneId();
                break;

            case "ARRIVED":
                this.state = DroneState.ARRIVED;
                break;

            case "EXTINGUISHING":
                this.state = DroneState.DROPPING_AGENT;
                break;

            case "COMPLETED":
            case "PARTIAL":
                this.state = DroneState.COMPLETED;
                useWater(response.getWaterUsed());
                break;

            case "RETURNING":
                this.state = DroneState.RETURNING;
                break;

            case "RETURNED":
                returnToBase();
                break;

            case "ERROR":
                System.out.println("ERROR HAS OCCURRED WITH droneData");
                break;

        }
    }
    @Override
    public String toString() {
        return String.format("Drone %d: %s, WaterAmount: %.1fL, Zone: %d", droneId, state, currentWater, currentZone);
    }

}
