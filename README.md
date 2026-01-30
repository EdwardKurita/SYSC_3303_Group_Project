# Firefighting Drone Swarm - Iteration 1

**Group 12:** Jiayi Han, Declan Koster, Shael Kotecha, Edward Kurita

---

## Overview

This is the starting point for the Firefighting Drone Swarm system. This first iteration contains the most simplistic version
of this system. events and zones are read in from their respective csv files and a singular drone gets assigned by the 
scheduler to respond to the events in their respective zones.

The system contains the skeleton to support multiple drones and simulates the entire lifecycle of a single drone.

---

## File Structure

```
root/
│
├── Main.java
├── FireIncidentSubsystem.java
├── DroneSubsystem.java
├── Scheduler.java
├── SharedBuffer.java
├── FireDroneGUI.java
├── FireEvent.java
├── DroneResponse.java
├── Zone.java
│
└── data/
    ├── events.csv
    └── zones.csv
```

#### Main.java
- System entry point
- Initializes all threads
- Manages thread lifecycle
- Manages system timing and clean exit

#### FireIncidentSubsystem.java
- Loads zone coordinate data from CSV
- Reads fire events and adds them to the shared buffer from csv
- Runs as the producer thread

#### DroneSubsystem.java
- Simulates drone missions with math/data from iteration 0
- Simulates assigned fires through travel → fight → return cycle
- Runs as the consumer thread

#### Scheduler.java
- Coordinates between fire events and drone operations
- Reads events from buffer and assigns to drone
- Processes drone responses and updates GUI
- Runs as a coordinator thread

#### SharedBuffer.java
- Uses synchronized methods and wait/notify pattern
- Manages queues for fire events and drone responses

#### FireDroneGUI.java
- Swing-based graphical interface
- Displays logging for the drone, scheduler
- Shows active zones and system event log


Note: the gui currently does not have the actual map of drone progress, however the container for this map
exists and will be populated in future iterations.

#### Data Models
- **FireEvent.java**: Represents fire incidents with time, zone, severity
- **DroneResponse.java**: Contains each drones status updates and mission progress
- **Zone.java**: Geographic zone with coordinates and distance calculations

---

## Setup and Run

1) Clone the Repository
2) Open in IntelliJ
3) Run the project from main within IntelliJ