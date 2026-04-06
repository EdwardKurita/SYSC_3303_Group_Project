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

---

## UML Diagrams

### Class Diagram
![UML Class Diagram](UMLs/3303UML1.png)

### Sequence Diagram
![Sequence Diagram](UMLs/3303Sequence1.png)

## Iteration 2

**Group 12:** Jiayi Han, Declan Koster, Shael Kotecha, Edward Kurita

**Team Responsibilities:**
- **Shael Kotecha:** Documentation and implementation
- **Declan Koster:** Implementation — DroneData, DroneState, DroneSubsystem, Scheduler, GUI, Main updates
- **Jiayi Han:** Testing — JUnit 5 tests for all classes and integration tests
- **Edward Kurita:** UML sequence and class diagrams

---

### Overview

Iteration 2 adds state machines for the Scheduler and Drone subsystems. The system operates with a single drone to simplify debugging, while being designed with future multi-drone coordination in mind. The drone notifies the scheduler upon arriving at a zone and upon mission completion, at which point the scheduler checks for queued events before the drone returns to base.

---

### What's New in Iteration 2

- Core scheduling logic to decide which drone handles a new fire
- Drone state transitions: `IDLE → EN_ROUTE → DROPPING_AGENT → RETURNING`
- Drone communicates status updates back to the scheduler throughout its mission
- Once a mission is complete, drone checks for queued events before returning to base
- On return to base, drone is assumed to be fully recharged and refilled
- Each zone has at most one fire at a time
- GUI updated to track drone states and number of active fires

---

### File Structure
```
root/
│
├── Main.java                          ← UPDATED
├── FireIncidentSubsystem.java
├── FireIncidentZone.java
├── DroneSubsystem.java                ← UPDATED
├── DroneData.java                     ← NEW
├── DroneState.java                    ← NEW
├── Scheduler.java                     ← UPDATED
├── SharedBuffer.java
├── FireDroneGUI.java                  ← UPDATED
├── FireEvent.java
├── DroneResponse.java
├── Zone.java
├── DroneDataTest.java                 ← NEW
├── DroneSubsystemTest.java            ← UPDATED
├── SchedulerTest.java                 ← NEW
├── Iteration2IntegrationTest.java     ← NEW
│
└── data/
    ├── events.csv
    └── zones.csv
```

#### DroneData.java ← NEW
- Holds all data for an individual drone
- Tracks agent level, battery, and current zone assignment

#### DroneState.java ← NEW
- Enum defining all drone state machine states
- States: `IDLE`, `EN_ROUTE`, `DROPPING_AGENT`, `RETURNING`

#### DroneSubsystem.java ← UPDATED
- Implements drone state machine
- Handles all state transitions internally
- Sends status updates back to the scheduler at each stage

#### Scheduler.java ← UPDATED
- Implements scheduler state machine
- Assigns incoming fire events to the drone
- Processes drone responses and queues new tasks

#### FireDroneGUI.java ← UPDATED
- Now tracks and displays drone state
- Displays number of active fires in real time

---

### Setup and Run

1) Clone the Repository
2) Open in IntelliJ
3) Run the project from Main within IntelliJ

---

### UML Diagrams

### Class Diagram
![UML_Diagram_iter_2](UMLs/UML_iter_2.drawio.png)

### Sequence Diagram
![Sequence_Diagram_Iter_2](UMLs/sequence_diagram_iter_2.drawio.png)

### State Machine Diagrams
#### Drone State Machine Diagram
![Drone_State_Machine_Diagram](UMLs/state_machine.png)

#### Scheduler State Machine Diagram
![Scheduler_State_Machine_Diagram](UMLs/scheduler_state_machine.drawio.png)


## Iteration 3

**Group 12:** Jiayi Han, Declan Koster, Shael Kotecha, Edward Kurita

**Team Responsibilities:**
- **Shael Kotecha:** Diagrams - UML class, sequence, state machine, and Scheduler State Machine Diagramdiagrams for Iteration 3
- **Declan Koster:** Testing - JUnit 5 tests for all classes and integration tests
- **Jiayi Han:** Documentation & Implementation - README, project documentation, and implementation support
- **Edward Kurita:** Implementation - Core system implementation, UDP communication, state machines

---
### Overview

Iteration 3 transforms our firefighting drone system from a single JVM application into a fully distributed system with three independent processes communicating via UDP sockets. This architecture enables multiple drones to run as separate processes, with real-time position tracking, dynamic mission rerouting, and fault handling.

---

### What's New in Iteration 3

- Communication - UDP DatagramSockets over network
- Processes - Three separate programs (Fire, Scheduler, Drone clients)
- Drones - Multiple drones (each as separate process)
- Architecture - Fully distributed
- Position Tracking	- Real-time coordinates (posX, posY) with animation
- Scheduler Logic - Formal state machine with 4 states
- Drone States - 8 states including PARTIAL and FAULTED
- Rerouting - Dynamic rerouting based on drone position
- Fault Handling - FAULTED state and automatic drone removal
- GUI Updates - Network UDP updates to separate GUI process
  
---

### File Structure
```
SYSC3303_Group_Project/
│
├── 📂 clients/                          # NEW - DRONE CLIENTS (separate process)
│   ├── DroneSubsystem.java              # Drone logic with UDP receive
│   ├── FireEvent.java                    # Fire event data model
│   ├── DroneState.java                   # Drone state enum
│   └── Main.java                          # Drone client entry point
│
├── 📂 intermediate/                      # UPDATED - SCHEDULER (separate process)
│   ├── Scheduler.java                     # Main scheduler with UDP & state machine
│   ├── SchedulerState.java                 # NEW - Scheduler state enum
│   ├── DroneData.java                      # Updated with position tracking
│   ├── FireEvent.java                       # Fire event data
│   ├── DroneState.java                       # Drone state enum
│   └── Main.java                              # Scheduler entry point
│
├── 📂 server/                             # UPDATED - FIRE INCIDENT SUBSYSTEM
│   ├── FireIncidentSubsystem.java          # Updated with UDP send
│   ├── FireDroneGUI.java                    # GUI with live updates
│   ├── GuiUpdateReceiver.java                # NEW - UDP listener for GUI
│   ├── FireIncidentZone.java                  # Zone management
│   ├── FireEvent.java                           # Fire event data
│   ├── Zone.java                                # Zone coordinates
│   └── Main.java                                  # Fire server entry point
│
├── 📂 data/                                # UNCHANGED - CONFIGURATION FILES
│   ├── events.csv                           # Fire event schedule
│   └── zones.csv                             # Zone coordinates
│
├── 📂 test/                                 # UPDATED - TEST FILES
│   ├── clients/
│   │   ├── DroneSubsystemTest.java           # Drone unit tests
│   │   ├── FireEventTest.java                 # Event tests
│   │   └── Iter3DroneSubsystemTest.java       # NEW - Integration tests
│   ├── intermediate/
│   │   ├── DroneDataTest.java                 # DroneData tests
│   │   ├── FireEventTest.java                  # Event tests
│   │   ├── Iter3SchedulerTest.java              # NEW - Scheduler integration tests
│   │   └── SchedulerTest.java                    # Scheduler unit tests
│   └── server/
│       ├── FireDroneGUITest.java                # GUI tests
│       ├── FireEventTest.java                    # Event tests
│       ├── FireIncidentSubsystemTest.java         # Fire subsystem tests
│       └── Iter3FireIncidentSubsystemTest.java     # NEW - Fire integration tests
│
└── 📂 uml/                                 # NEW - UML DIAGRAMS
    ├── UML_Class_Iter3.png
    ├── Sequence_Diagram_Iter3.png
    ├── Drone_State_Machine_Diagram.png
    └── Scheduler_State_Machine_Diagram.png
```

---

### Setup and Run

1) Clone the Repository
2) Open in IntelliJ
3) Terminal 1 - Start Fire Server (GUI)
4) Terminal 2 - Start Scheduler
5) Terminal 3 - Start Drone 1
6) Terminal 4 - Start Drone 2
7) Shutdown - Press Ctrl+C in each terminal window

---

### Iteration 3 UML
### Class Diagram
![UML_Class iter_3](UMLs/UMLCLassIter3.drawio.png)

### Sequence Diagram
![Sequence_Diagram_Iter_3](UMLs/SequenceDiagramIter3.png)

### State Machine Diagrams
#### Drone State Machine Diagram
![Drone_State_Machine_Diagram](UMLs/DroneStateiter3.drawio.png)

#### Scheduler State Machine Diagram
![Scheduler_State_Machine_Diagram](UMLs/StateMachineIter3.drawio.png)

---

## Iteration 4

**Group 12:** Jiayi Han, Declan Koster, Shael Kotecha, Edward Kurita

**Team Responsibilities:**
- **Shael Kotecha:** Implementation - Core system implementation, UDP communication, state machines
- **Declan Koster:** Documentation & Implementation - README, project documentation, and implementation support
- **Jiayi Han:** Diagrams - Fault Handling, Normal Operation, Partial Completion timing Diagrams  
- **Edward Kurita:** Testing - JUnit 5 tests for all classes and integration tests

---
### Overview

Iteration 4 Added 2 different types of fault handling - Soft and Hard - which either stops the drone process and returns it to base (soft) or completely shuts down the drone and turns it off (hard)

---

### What's New in Iteration 3

- State Machine - Now handles Faults 
- GUI Update - fixed error with the wrong colour Fire Intensity 
---

### File Structure
```
SYSC3303_Group_Project/
│
├── 📂 clients/                          # DRONE CLIENTS (separate process)
│   ├── DroneSubsystem.java              # UPDATED - Drone logic with UDP receive
│   ├── FireEvent.java                    # Fire event data model
│   ├── DroneState.java                   # Drone state enum
│   └── Main.java                          # Drone client entry point
│
├── 📂 intermediate/                      # UPDATED - SCHEDULER (separate process)
│   ├── Scheduler.java                     # Main scheduler with UDP & state machine
│   ├── SchedulerState.java                 # Scheduler state enum
│   ├── DroneData.java                      # Updated with position tracking
│   ├── FireEvent.java                       # Fire event data
│   ├── DroneState.java                       # Drone state enum
│   └── Main.java                              # Scheduler entry point
│
├── 📂 server/                             # UPDATED - FIRE INCIDENT SUBSYSTEM
│   ├── FireIncidentSubsystem.java          # Updated with UDP send
│   ├── FireDroneGUI.java                    # GUI with live updates
│   ├── GuiUpdateReceiver.java                # NEW - UDP listener for GUI
│   ├── FireIncidentZone.java                  # Zone management
│   ├── FireEvent.java                           # Fire event data
│   ├── Zone.java                                # Zone coordinates
│   └── Main.java                                  # Fire server entry point
│
├── 📂 data/                                # UPDATED - CONFIGURATION FILES
│   ├── events.csv                           # Fire event schedule
│   └── zones.csv                             # Zone coordinates
│
├── 📂 test/                                 # UPDATED - TEST FILES
│   ├── clients/
│   │   ├── DroneSubsystemTest.java           # Drone unit tests
│   │   ├── FireEventTest.java                 # Event tests
│   │   ├── Iter3DroneSubsystemTest.java       # Integration tests
│   │   └── Iter4Test.java                     # NEW - Fault Test
│   ├── intermediate/
│   │   ├── DroneDataTest.java                 # DroneData tests
│   │   ├── FireEventTest.java                  # Event tests
│   │   ├── Iter3SchedulerTest.java              # Scheduler integration tests
│   │   ├── SchedulerTest.java                    # Scheduler unit tests
│   │   └── Iter4Test.java                        # NEW - Fault Test
│   └── server/
│       ├── FireDroneGUITest.java                # GUI tests
│       ├── FireEventTest.java                    # Event tests
│       ├── FireIncidentSubsystemTest.java         # Fire subsystem tests
│       ├── Iter3FireIncidentSubsystemTest.java     #  Fire integration tests
│       └── Iter4Test.java                          # NEW - Fault Test
│
└── 📂 uml/                                 # NEW - UML DIAGRAMS
    ├── Fault Handling Timing Diagram.JPG
    ├── Normal Operation Timing Diagram.JPG
    └── Partial Completion Timing Diagram.JPG
```

---

### Setup and Run

1) Clone the Repository
2) Open in IntelliJ
3) Terminal 1 - Start Fire Server (GUI)
4) Terminal 2 - Start Scheduler
5) Terminal 3 - Start Drone 1
6) Terminal 4 - Start Drone 2
7) Shutdown - Press Ctrl+C in each terminal window

---

### Iteration 4 Timing Diagrams
### Fault Handing 
![Fault Handling Timing Diagram](UMLs/Fault%20Handling%20Timing%20Diagram.JPG)

### Normal Operation
![Normal Operation Timing Diagram](UMLs/Normal%20Operation%20Timing%20Diagram.JPG)

### Partial Completion
![Partial Completion Timing Diagram](UMLs/Partial%20Completion%20Timing%20Diagram.JPG)

## Iteration 5

**Group 12:** Jiayi Han, Declan Koster, Shael Kotecha, Edward Kurita

**Team Responsibilities:**
- **Shael Kotecha:** TESTING
- **Declan Koster:** DIAGRAMS AND IMPLEMENTATION ASSISTANCE
- **Jiayi Han:** IMPLEMENTATION LEAD
- **Edward Kurita:** DOCUMENTATION

---

### Overview

Iteration 5 adds live performance metrics to the existing architecture. Metrics are tracked and visible through the GUI.

---

### What's New?

- Performance metrics to track fire and drone statuses with time stamps.
- Live panel: included with the performance metrics

---

### File Structure

```
SYSC3303_Group_Project/
│
├── 📂 clients/                          # DRONE CLIENTS (separate process)
│   ├── DroneSubsystem.java              # Drone logic with UDP receive
│   ├── FireEvent.java                    # Fire event data model
│   ├── DroneState.java                   # Drone state enum (8 states)
│   └── Main.java                          # Drone client entry point
│
├── 📂 intermediate/                      # UPDATED - SCHEDULER (separate process)
│   ├── Scheduler.java                     # Scheduler with metrics integration
│   ├── SchedulerState.java                 # UPDATED - FAULT_HANDLING state added
│   ├── DroneData.java                      # Drone tracking with fault helpers
│   ├── PerformanceMetrics.java             # NEW - flight/idle/response time tracking
│   ├── FireEvent.java                       # Fire event data
│   ├── DroneState.java                       # Drone state enum
│   └── Main.java                              # Scheduler entry point with shutdown hook
│
├── 📂 server/                             # UPDATED - FIRE INCIDENT SUBSYSTEM
│   ├── FireIncidentSubsystem.java          # Reads fault type from events.csv (5th column)
│   ├── FireDroneGUI.java                    # UPDATED - metrics panel, fault log, completed counter
│   ├── GuiUpdateReceiver.java                # UPDATED - parses utilization (field 9) from packets
│   ├── FireIncidentZone.java                  # Zone management
│   ├── FireEvent.java                           # Fire event data
│   ├── Zone.java                                # Zone coordinates
│   └── Main.java                                  # Fire server entry point
│
├── 📂 data/                                # UPDATED - CONFIGURATION FILES
│   ├── events.csv                           # UPDATED - FaultType column added
│   └── zones.csv                             # Zone coordinates (unchanged)
│
└── 📂 test/                                 # UPDATED - TEST FILES
    ├── clients/
    │   ├── DroneSubsystemTest.java
    │   ├── FireEventTest.java
    │   ├── Iter3DroneSubsystemTest.java
    │   └── Iter4Test.java
    ├── intermediate/
    │   ├── DroneDataTest.java
    │   ├── FireEventTest.java
    │   ├── Iter3SchedulerTest.java
    │   ├── SchedulerTest.java
    │   ├── Iter4Test.java
    │   └── Iter5Test.java    # NEW - PerformanceMetrics unit tests
    └── server/
        ├── FireDroneGUITest.java
        ├── FireEventTest.java
        ├── FireIncidentSubsystemTest.java
        ├── Iter3FireIncidentSubsystemTest.java
        └── Iter4Test.java
```

---

### Setup and Run

1) Clone the Repository
2) Open in IntelliJ
3) Terminal 1 - Start Fire Server (GUI)
4) Terminal 2 - Start Scheduler
5) Terminal 3 - Start Drone 1
6) Terminal 4 - Start Drone 2
7) Shutdown - Press Ctrl+C in each terminal window

---

### UML Diagrams

## Class Diagram

![UDP Class Diagram](UMLs/Class%20Diagram%20D5.png)

## State Diagrams

![Scheduler State](UMLs/Scheduler%20States%20D5.png)
![FireIncidentSubsystem State](UMLs/FireIncidentSubsystem%20States%20D5.png)
![DroneSubsystem state](UMLs/DroneSubsystem%20State%20D5.png)

## Sequence Diagram

![Sequence Diagram](UMLs/Sequence%20Diagram%20D5.png)
