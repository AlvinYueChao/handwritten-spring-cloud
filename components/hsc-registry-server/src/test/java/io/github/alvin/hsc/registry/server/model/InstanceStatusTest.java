package io.github.alvin.hsc.registry.server.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InstanceStatus enum
 * 
 * @author Alvin
 */
class InstanceStatusTest {

    @Test
    void testValidTransitionsFromStarting() {
        InstanceStatus starting = InstanceStatus.STARTING;
        
        assertTrue(starting.canTransitionTo(InstanceStatus.UP));
        assertTrue(starting.canTransitionTo(InstanceStatus.DOWN));
        assertTrue(starting.canTransitionTo(InstanceStatus.UNKNOWN));
        assertTrue(starting.canTransitionTo(InstanceStatus.OUT_OF_SERVICE));
        assertTrue(starting.canTransitionTo(InstanceStatus.STARTING)); // self transition
        
        Set<InstanceStatus> validTransitions = starting.getValidTransitions();
        assertEquals(4, validTransitions.size());
        assertTrue(validTransitions.contains(InstanceStatus.UP));
        assertTrue(validTransitions.contains(InstanceStatus.DOWN));
        assertTrue(validTransitions.contains(InstanceStatus.UNKNOWN));
        assertTrue(validTransitions.contains(InstanceStatus.OUT_OF_SERVICE));
    }

    @Test
    void testValidTransitionsFromUp() {
        InstanceStatus up = InstanceStatus.UP;
        
        assertTrue(up.canTransitionTo(InstanceStatus.DOWN));
        assertTrue(up.canTransitionTo(InstanceStatus.OUT_OF_SERVICE));
        assertTrue(up.canTransitionTo(InstanceStatus.UNKNOWN));
        assertTrue(up.canTransitionTo(InstanceStatus.UP)); // self transition
        assertFalse(up.canTransitionTo(InstanceStatus.STARTING));
        
        Set<InstanceStatus> validTransitions = up.getValidTransitions();
        assertEquals(3, validTransitions.size());
        assertTrue(validTransitions.contains(InstanceStatus.DOWN));
        assertTrue(validTransitions.contains(InstanceStatus.OUT_OF_SERVICE));
        assertTrue(validTransitions.contains(InstanceStatus.UNKNOWN));
    }

    @Test
    void testValidTransitionsFromDown() {
        InstanceStatus down = InstanceStatus.DOWN;
        
        assertTrue(down.canTransitionTo(InstanceStatus.UP));
        assertTrue(down.canTransitionTo(InstanceStatus.STARTING));
        assertTrue(down.canTransitionTo(InstanceStatus.OUT_OF_SERVICE));
        assertTrue(down.canTransitionTo(InstanceStatus.UNKNOWN));
        assertTrue(down.canTransitionTo(InstanceStatus.DOWN)); // self transition
        
        Set<InstanceStatus> validTransitions = down.getValidTransitions();
        assertEquals(4, validTransitions.size());
        assertTrue(validTransitions.contains(InstanceStatus.UP));
        assertTrue(validTransitions.contains(InstanceStatus.STARTING));
        assertTrue(validTransitions.contains(InstanceStatus.OUT_OF_SERVICE));
        assertTrue(validTransitions.contains(InstanceStatus.UNKNOWN));
    }

    @Test
    void testValidTransitionsFromOutOfService() {
        InstanceStatus outOfService = InstanceStatus.OUT_OF_SERVICE;
        
        assertTrue(outOfService.canTransitionTo(InstanceStatus.UP));
        assertTrue(outOfService.canTransitionTo(InstanceStatus.DOWN));
        assertTrue(outOfService.canTransitionTo(InstanceStatus.STARTING));
        assertTrue(outOfService.canTransitionTo(InstanceStatus.UNKNOWN));
        assertTrue(outOfService.canTransitionTo(InstanceStatus.OUT_OF_SERVICE)); // self transition
        
        Set<InstanceStatus> validTransitions = outOfService.getValidTransitions();
        assertEquals(4, validTransitions.size());
        assertTrue(validTransitions.contains(InstanceStatus.UP));
        assertTrue(validTransitions.contains(InstanceStatus.DOWN));
        assertTrue(validTransitions.contains(InstanceStatus.STARTING));
        assertTrue(validTransitions.contains(InstanceStatus.UNKNOWN));
    }

    @Test
    void testValidTransitionsFromUnknown() {
        InstanceStatus unknown = InstanceStatus.UNKNOWN;
        
        // UNKNOWN can transition to any status
        assertTrue(unknown.canTransitionTo(InstanceStatus.UP));
        assertTrue(unknown.canTransitionTo(InstanceStatus.DOWN));
        assertTrue(unknown.canTransitionTo(InstanceStatus.STARTING));
        assertTrue(unknown.canTransitionTo(InstanceStatus.OUT_OF_SERVICE));
        assertTrue(unknown.canTransitionTo(InstanceStatus.UNKNOWN)); // self transition
        
        Set<InstanceStatus> validTransitions = unknown.getValidTransitions();
        assertEquals(5, validTransitions.size()); // All statuses
    }

    @Test
    void testIsHealthy() {
        assertTrue(InstanceStatus.UP.isHealthy());
        assertFalse(InstanceStatus.DOWN.isHealthy());
        assertFalse(InstanceStatus.STARTING.isHealthy());
        assertFalse(InstanceStatus.UNKNOWN.isHealthy());
        assertFalse(InstanceStatus.OUT_OF_SERVICE.isHealthy());
    }

    @Test
    void testIsAvailable() {
        assertTrue(InstanceStatus.UP.isAvailable());
        assertFalse(InstanceStatus.DOWN.isAvailable());
        assertFalse(InstanceStatus.STARTING.isAvailable());
        assertFalse(InstanceStatus.UNKNOWN.isAvailable());
        assertFalse(InstanceStatus.OUT_OF_SERVICE.isAvailable());
    }

    @Test
    void testIsTerminal() {
        assertTrue(InstanceStatus.OUT_OF_SERVICE.isTerminal());
        assertFalse(InstanceStatus.UP.isTerminal());
        assertFalse(InstanceStatus.DOWN.isTerminal());
        assertFalse(InstanceStatus.STARTING.isTerminal());
        assertFalse(InstanceStatus.UNKNOWN.isTerminal());
    }

    @Test
    void testSelfTransitions() {
        for (InstanceStatus status : InstanceStatus.values()) {
            assertTrue(status.canTransitionTo(status), 
                    "Status " + status + " should allow self transition");
        }
    }
}