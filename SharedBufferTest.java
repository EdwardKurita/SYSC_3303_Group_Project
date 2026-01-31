import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JUnit 5 test cases for SharedBuffer class
 */
class SharedBufferTest {

    private SharedBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new SharedBuffer();
    }

    @Test
    @DisplayName("PutFireEvent and takeFireEvent should work correctly")
    void testPutAndTakeFireEvent() throws Exception {
        FireEvent event = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");

        buffer.putFireEvent(event);
        assertTrue(buffer.hasFireEvent());

        FireEvent retrieved = buffer.takeFireEvent();
        assertEquals(event, retrieved);
        assertFalse(buffer.hasFireEvent());
    }

    @Test
    @DisplayName("Multiple fire events should be queued in FIFO order")
    void testMultipleFireEvents() throws Exception {
        FireEvent event1 = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");
        FireEvent event2 = new FireEvent("10:05:00", 2, "FIRE_DETECTED", "Moderate");
        FireEvent event3 = new FireEvent("10:10:00", 3, "FIRE_DETECTED", "Low");

        buffer.putFireEvent(event1);
        buffer.putFireEvent(event2);
        buffer.putFireEvent(event3);

        assertTrue(buffer.hasFireEvent());

        assertEquals(event1, buffer.takeFireEvent());
        assertEquals(event2, buffer.takeFireEvent());
        assertEquals(event3, buffer.takeFireEvent());

        assertFalse(buffer.hasFireEvent());
    }

    @Test
    @DisplayName("PutDroneResponse and takeDroneResponse should work correctly")
    void testPutAndTakeDroneResponse() throws Exception {
        DroneResponse response = new DroneResponse(1, "EN_ROUTE", "Traveling", "10:00:00", 0.0);

        assertFalse(buffer.hasDroneResponse());

        buffer.putDroneResponse(response);
        assertTrue(buffer.hasDroneResponse());

        DroneResponse retrieved = buffer.takeDroneResponse();
        assertEquals(response, retrieved);
        assertFalse(buffer.hasDroneResponse());
    }

    @Test
    @DisplayName("TakeFireEvent should block when buffer is empty")
    void testTakeFireEventBlocks() throws Exception {
        AtomicBoolean eventRetrieved = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        // Thread that tries to take from empty buffer
        Thread consumer = new Thread(() -> {
            try {
                latch.countDown(); // Signal that thread started
                buffer.takeFireEvent(); // This should block
                eventRetrieved.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        latch.await(); // Wait for consumer to start
        Thread.sleep(200); // Give time to verify it's blocked

        assertFalse(eventRetrieved.get()); // Should still be waiting

        // Now add an event
        FireEvent event = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");
        buffer.putFireEvent(event);

        consumer.join(1000); // Wait for consumer to finish
        assertTrue(eventRetrieved.get()); // Should have retrieved the event
    }

    @Test
    @DisplayName("TakeDroneResponse should block when no response available")
    void testTakeDroneResponseBlocks() throws Exception {
        AtomicBoolean responseRetrieved = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                latch.countDown();
                buffer.takeDroneResponse(); // This should block
                responseRetrieved.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        latch.await();
        Thread.sleep(200);

        assertFalse(responseRetrieved.get()); // Should still be waiting

        // Now add a response
        DroneResponse response = new DroneResponse(1, "COMPLETED", "Done", "10:00:00", 15.0);
        buffer.putDroneResponse(response);

        consumer.join(1000);
        assertTrue(responseRetrieved.get());
    }

    @Test
    @DisplayName("PutDroneResponse should block when response already available")
    void testPutDroneResponseBlocks() throws Exception {
        DroneResponse response1 = new DroneResponse(1, "EN_ROUTE", "Traveling", "10:00:00", 0.0);
        DroneResponse response2 = new DroneResponse(2, "ARRIVED", "Arrived", "10:05:00", 0.0);

        buffer.putDroneResponse(response1);

        AtomicBoolean secondPutCompleted = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        // Thread that tries to put second response
        Thread producer = new Thread(() -> {
            try {
                latch.countDown();
                buffer.putDroneResponse(response2); // Should block until first is taken
                secondPutCompleted.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        latch.await();
        Thread.sleep(200);

        assertFalse(secondPutCompleted.get()); // Should still be blocked

        // Take the first response
        buffer.takeDroneResponse();

        producer.join(1000);
        assertTrue(secondPutCompleted.get()); // Second put should complete
        assertTrue(buffer.hasDroneResponse()); // Second response should be available
    }

    @Test
    @DisplayName("Thread-safe concurrent access to fire events")
    void testConcurrentFireEvents() throws Exception {
        int numProducers = 3;
        int eventsPerProducer = 5;
        CountDownLatch startLatch = new CountDownLatch(numProducers);
        CountDownLatch doneLatch = new CountDownLatch(numProducers);

        // Multiple producers
        for (int i = 0; i < numProducers; i++) {
            final int producerId = i;
            new Thread(() -> {
                try {
                    startLatch.countDown();
                    startLatch.await(); // Wait for all to start together

                    for (int j = 0; j < eventsPerProducer; j++) {
                        FireEvent event = new FireEvent("10:00:00", producerId * 10 + j,
                                "FIRE_DETECTED", "High");
                        buffer.putFireEvent(event);
                    }
                    doneLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }

        doneLatch.await(5, TimeUnit.SECONDS);

        // Verify all events were added
        int totalEvents = numProducers * eventsPerProducer;
        for (int i = 0; i < totalEvents; i++) {
            assertTrue(buffer.hasFireEvent());
            assertNotNull(buffer.takeFireEvent());
        }

        assertFalse(buffer.hasFireEvent());
    }

    @Test
    @DisplayName("HasFireEvent should return correct state")
    void testHasFireEvent() throws Exception {
        assertFalse(buffer.hasFireEvent());

        FireEvent event = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");
        buffer.putFireEvent(event);

        assertTrue(buffer.hasFireEvent());

        buffer.takeFireEvent();

        assertFalse(buffer.hasFireEvent());
    }
}