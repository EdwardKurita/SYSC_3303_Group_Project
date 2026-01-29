import java.util.LinkedList;
import java.util.Queue;

// SharedBuffer acts as a synchronized communication channel between threads
public class SharedBuffer {
    private Queue<FireEvent> fireEventQueue = new LinkedList<>();
    private DroneResponse droneResponse = null;
    private boolean droneResponseAvailable = false;

    // Add a FireEvent to the queue and notifies waiting threads
    public synchronized void putFireEvent(FireEvent event) throws InterruptedException {
        fireEventQueue.add(event);
        notifyAll(); // Wake up threads waiting for events
    }

    // Removes and returns the next FireEvent from the queue
    public synchronized FireEvent takeFireEvent() throws InterruptedException {
        while (fireEventQueue.isEmpty()) {
            wait(); // Wait until an event is available
        }

        FireEvent event = fireEventQueue.poll();
        notifyAll(); // Notify other threads about state change
        return event;
    }

    // Check if there are any FireEvent in the queue
    public synchronized boolean hasFireEvent() {
        return !fireEventQueue.isEmpty();
    }

    // Drone response methods
    // Stores a DroneResponse in the buffer
    // Wait if a response is already present (only one at a time)
    public synchronized void putDroneResponse(DroneResponse response) throws InterruptedException {
        while (droneResponseAvailable) {
            wait(); // Wait until the previous response is consumed
        }

        this.droneResponse = response;
        droneResponseAvailable = true;
        notifyAll(); // Notify threads waiting for a response
    }

    // Retrieves the stored DroneResponse and clears the buffer
    // Waits if no response is available
    public synchronized DroneResponse takeDroneResponse() throws InterruptedException {
        while (!droneResponseAvailable) {
            wait(); // Wait until a response is available
        }

        DroneResponse response = this.droneResponse;
        this.droneResponse = null;
        droneResponseAvailable = false;
        notifyAll(); // notify threads that the buffer is empty
        return response;
    }

    // Check is a DroneResponse is currently available
    public synchronized boolean hasDroneResponse() {
        return droneResponseAvailable;
    }
}