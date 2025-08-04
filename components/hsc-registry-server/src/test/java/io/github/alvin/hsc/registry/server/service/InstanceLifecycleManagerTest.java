package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InstanceLifecycleManager
 * 
 * @author Alvin
 */
class InstanceLifecycleManagerTest {

    private InstanceLifecycleManager lifecycleManager;
    private ServiceInstance testInstance;

    @BeforeEach
    void setUp() {
        lifecycleManager = new InstanceLifecycleManager();
        testInstance = new ServiceInstance("test-service", "instance-1", "localhost", 8080);
    }

    @Test
    void testUpdateInstanceStatusSuccess() {
        testInstance.setStatus(InstanceStatus.STARTING);
        
        boolean result = lifecycleManager.updateInstanceStatus(testInstance, InstanceStatus.UP, "Health check passed");
        
        assertTrue(result);
        assertEquals(InstanceStatus.UP, testInstance.getStatus());
        
        // Check status history
        InstanceLifecycleManager.InstanceStatusHistory history = 
                lifecycleManager.getInstanceStatusHistory(testInstance);
        assertNotNull(history);
        assertEquals("instance-1", history.getInstanceId());
        assertFalse(history.getStatusChanges().isEmpty());
    }

    @Test
    void testUpdateInstanceStatusInvalidTransition() {
        testInstance.setStatus(InstanceStatus.UP);
        
        boolean result = lifecycleManager.updateInstanceStatus(testInstance, InstanceStatus.STARTING, "Invalid transition");
        
        assertFalse(result);
        assertEquals(InstanceStatus.UP, testInstance.getStatus()); // Status should remain unchanged
    }

    @Test
    void testUpdateInstanceStatusWithNullParameters() {
        assertFalse(lifecycleManager.updateInstanceStatus(null, InstanceStatus.UP));
        assertFalse(lifecycleManager.updateInstanceStatus(testInstance, null));
        assertFalse(lifecycleManager.updateInstanceStatus(null, null));
    }

    @Test
    void testHandleInstanceRegistration() {
        testInstance.setStatus(null); // Simulate new instance without status
        
        lifecycleManager.handleInstanceRegistration(testInstance);
        
        assertEquals(InstanceStatus.STARTING, testInstance.getStatus());
        
        // Check status history
        InstanceLifecycleManager.InstanceStatusHistory history = 
                lifecycleManager.getInstanceStatusHistory(testInstance);
        assertNotNull(history);
        assertFalse(history.getStatusChanges().isEmpty());
    }

    @Test
    void testHandleInstanceRegistrationWithExistingStatus() {
        testInstance.setStatus(InstanceStatus.DOWN);
        
        lifecycleManager.handleInstanceRegistration(testInstance);
        
        assertEquals(InstanceStatus.STARTING, testInstance.getStatus());
    }

    @Test
    void testHandleInstanceHeartbeat() {
        testInstance.setStatus(InstanceStatus.STARTING);
        Instant beforeHeartbeat = testInstance.getLastHeartbeat();
        
        // Wait a bit to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        lifecycleManager.handleInstanceHeartbeat(testInstance);
        
        assertEquals(InstanceStatus.UP, testInstance.getStatus());
        assertTrue(testInstance.getLastHeartbeat().isAfter(beforeHeartbeat));
    }

    @Test
    void testHandleInstanceHeartbeatWhenUp() {
        testInstance.setStatus(InstanceStatus.UP);
        Instant beforeHeartbeat = testInstance.getLastHeartbeat();
        
        // Wait a bit to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        lifecycleManager.handleInstanceHeartbeat(testInstance);
        
        assertEquals(InstanceStatus.UP, testInstance.getStatus()); // Should remain UP
        assertTrue(testInstance.getLastHeartbeat().isAfter(beforeHeartbeat));
    }

    @Test
    void testHandleInstanceHeartbeatWhenOutOfService() {
        testInstance.setStatus(InstanceStatus.OUT_OF_SERVICE);
        
        lifecycleManager.handleInstanceHeartbeat(testInstance);
        
        assertEquals(InstanceStatus.OUT_OF_SERVICE, testInstance.getStatus()); // Should remain OUT_OF_SERVICE
    }

    @Test
    void testHandleInstanceDeregistration() {
        testInstance.setStatus(InstanceStatus.UP);
        
        lifecycleManager.handleInstanceDeregistration(testInstance);
        
        assertEquals(InstanceStatus.OUT_OF_SERVICE, testInstance.getStatus());
        
        // Status history should be cleaned up
        InstanceLifecycleManager.InstanceStatusHistory history = 
                lifecycleManager.getInstanceStatusHistory(testInstance);
        assertNull(history);
    }

    @Test
    void testIsHeartbeatTimeoutWithRecentHeartbeat() {
        testInstance.updateHeartbeat(); // Set current time as last heartbeat
        
        boolean isTimeout = lifecycleManager.isHeartbeatTimeout(testInstance);
        
        assertFalse(isTimeout);
    }

    @Test
    void testIsHeartbeatTimeoutWithOldHeartbeat() {
        // Set last heartbeat to 2 minutes ago
        testInstance.setLastHeartbeat(Instant.now().minus(Duration.ofMinutes(2)));
        
        boolean isTimeout = lifecycleManager.isHeartbeatTimeout(testInstance);
        
        assertTrue(isTimeout);
    }

    @Test
    void testIsHeartbeatTimeoutWithCustomTimeout() {
        testInstance.setLastHeartbeat(Instant.now().minus(Duration.ofSeconds(30)));
        
        boolean isTimeout = lifecycleManager.isHeartbeatTimeout(testInstance, Duration.ofSeconds(10));
        
        assertTrue(isTimeout);
    }

    @Test
    void testIsHeartbeatTimeoutWithNullHeartbeat() {
        testInstance.setLastHeartbeat(null);
        
        boolean isTimeout = lifecycleManager.isHeartbeatTimeout(testInstance);
        
        assertTrue(isTimeout);
    }

    @Test
    void testIsHeartbeatTimeoutWithNullInstance() {
        boolean isTimeout = lifecycleManager.isHeartbeatTimeout(null);
        
        assertTrue(isTimeout);
    }

    @Test
    void testHandleHeartbeatTimeoutFromUp() {
        testInstance.setStatus(InstanceStatus.UP);
        testInstance.setLastHeartbeat(Instant.now().minus(Duration.ofMinutes(2)));
        
        lifecycleManager.handleHeartbeatTimeout(testInstance);
        
        assertEquals(InstanceStatus.DOWN, testInstance.getStatus());
    }

    @Test
    void testHandleHeartbeatTimeoutFromDown() {
        testInstance.setStatus(InstanceStatus.DOWN);
        testInstance.setLastHeartbeat(Instant.now().minus(Duration.ofMinutes(5))); // Long time ago
        
        lifecycleManager.handleHeartbeatTimeout(testInstance);
        
        assertEquals(InstanceStatus.UNKNOWN, testInstance.getStatus());
    }

    @Test
    void testHandleHeartbeatTimeoutFromDownRecent() {
        testInstance.setStatus(InstanceStatus.DOWN);
        testInstance.setLastHeartbeat(Instant.now().minus(Duration.ofMinutes(2))); // Not too long ago
        
        lifecycleManager.handleHeartbeatTimeout(testInstance);
        
        assertEquals(InstanceStatus.DOWN, testInstance.getStatus()); // Should remain DOWN
    }

    @Test
    void testGetInstanceStatusHistoryForNonExistentInstance() {
        ServiceInstance anotherInstance = new ServiceInstance("another-service", "instance-2", "localhost", 8081);
        
        InstanceLifecycleManager.InstanceStatusHistory history = 
                lifecycleManager.getInstanceStatusHistory(anotherInstance);
        
        assertNull(history);
    }

    @Test
    void testStatusChangeRecord() {
        InstanceStatus fromStatus = InstanceStatus.STARTING;
        InstanceStatus toStatus = InstanceStatus.UP;
        String reason = "Health check passed";
        Instant timestamp = Instant.now();
        
        InstanceLifecycleManager.StatusChange change = 
                new InstanceLifecycleManager.StatusChange(fromStatus, toStatus, reason, timestamp);
        
        assertEquals(fromStatus, change.getFromStatus());
        assertEquals(toStatus, change.getToStatus());
        assertEquals(reason, change.getReason());
        assertEquals(timestamp, change.getTimestamp());
        
        String toString = change.toString();
        assertTrue(toString.contains("STARTING"));
        assertTrue(toString.contains("UP"));
        assertTrue(toString.contains(reason));
    }

    @Test
    void testInstanceStatusHistory() {
        String instanceId = "test-instance";
        InstanceLifecycleManager.InstanceStatusHistory history = 
                new InstanceLifecycleManager.InstanceStatusHistory(instanceId);
        
        assertEquals(instanceId, history.getInstanceId());
        assertTrue(history.getStatusChanges().isEmpty());
        
        history.addStatusChange(InstanceStatus.STARTING, InstanceStatus.UP, "Health check passed");
        
        assertEquals(1, history.getStatusChanges().size());
    }

    @Test
    void testCompleteLifecycle() {
        // Reset status to null to simulate a fresh instance
        testInstance.setStatus(null);
        
        // Registration
        lifecycleManager.handleInstanceRegistration(testInstance);
        assertEquals(InstanceStatus.STARTING, testInstance.getStatus());
        
        // First heartbeat - should transition to UP
        lifecycleManager.handleInstanceHeartbeat(testInstance);
        assertEquals(InstanceStatus.UP, testInstance.getStatus());
        
        // Heartbeat timeout - should transition to DOWN
        testInstance.setLastHeartbeat(Instant.now().minus(Duration.ofMinutes(2)));
        lifecycleManager.handleHeartbeatTimeout(testInstance);
        assertEquals(InstanceStatus.DOWN, testInstance.getStatus());
        
        // Recovery heartbeat - should transition back to UP
        lifecycleManager.handleInstanceHeartbeat(testInstance);
        assertEquals(InstanceStatus.UP, testInstance.getStatus());
        
        // Deregistration - should transition to OUT_OF_SERVICE
        lifecycleManager.handleInstanceDeregistration(testInstance);
        assertEquals(InstanceStatus.OUT_OF_SERVICE, testInstance.getStatus());
        
        // Verify status history was recorded
        InstanceLifecycleManager.InstanceStatusHistory history = 
                lifecycleManager.getInstanceStatusHistory(testInstance);
        assertNull(history); // Should be cleaned up after deregistration
    }
}