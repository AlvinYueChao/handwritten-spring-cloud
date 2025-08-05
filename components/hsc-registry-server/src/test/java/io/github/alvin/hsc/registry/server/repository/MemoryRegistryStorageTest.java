package io.github.alvin.hsc.registry.server.repository;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.model.HealthCheckConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryRegistryStorage
 * 
 * @author Alvin
 */
class MemoryRegistryStorageTest {
    
    private MemoryRegistryStorage storage;
    
    @BeforeEach
    void setUp() {
        storage = new MemoryRegistryStorage();
    }
    
    @AfterEach
    void tearDown() {
        storage.shutdown();
    }
    
    @Test
    void testRegisterServiceInstance() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        
        // When
        ServiceInstance registered = storage.register(instance);
        
        // Then
        assertNotNull(registered);
        assertEquals("test-service", registered.getServiceId());
        assertEquals("instance-1", registered.getInstanceId());
        assertNotNull(registered.getRegistrationTime());
        assertNotNull(registered.getLastHeartbeat());
    }
    
    @Test
    void testRegisterNullInstance() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> storage.register(null));
    }
    
    @Test
    void testRegisterInstanceWithNullServiceId() {
        // Given
        ServiceInstance instance = createTestInstance(null, "instance-1", "localhost", 8080);
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> storage.register(instance));
    }
    
    @Test
    void testRegisterInstanceWithEmptyServiceId() {
        // Given
        ServiceInstance instance = createTestInstance("", "instance-1", "localhost", 8080);
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> storage.register(instance));
    }
    
    @Test
    void testRegisterInstanceWithNullInstanceId() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", null, "localhost", 8080);
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> storage.register(instance));
    }
    
    @Test
    void testUpdateExistingInstance() {
        // Given
        ServiceInstance instance1 = createTestInstance("test-service", "instance-1", "localhost", 8080);
        ServiceInstance instance2 = createTestInstance("test-service", "instance-1", "localhost", 8081);
        
        // When
        storage.register(instance1);
        ServiceInstance updated = storage.register(instance2);
        
        // Then
        assertEquals(8081, updated.getPort());
        
        List<ServiceInstance> instances = storage.getInstances("test-service");
        assertEquals(1, instances.size());
        assertEquals(8081, instances.get(0).getPort());
    }
    
    @Test
    void testDeregisterServiceInstance() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        storage.register(instance);
        
        // When
        ServiceInstance deregistered = storage.deregister("test-service", "instance-1");
        
        // Then
        assertNotNull(deregistered);
        assertEquals("instance-1", deregistered.getInstanceId());
        
        List<ServiceInstance> instances = storage.getInstances("test-service");
        assertTrue(instances.isEmpty());
    }
    
    @Test
    void testDeregisterNonExistentInstance() {
        // When
        ServiceInstance deregistered = storage.deregister("non-existent", "instance-1");
        
        // Then
        assertNull(deregistered);
    }
    
    @Test
    void testDeregisterWithNullParameters() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
            () -> storage.deregister(null, "instance-1"));
        assertThrows(IllegalArgumentException.class, 
            () -> storage.deregister("service-1", null));
    }
    
    @Test
    void testRenewHeartbeat() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        storage.register(instance);
        Instant originalHeartbeat = instance.getLastHeartbeat();
        
        // Wait a bit to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        ServiceInstance renewed = storage.renew("test-service", "instance-1");
        
        // Then
        assertNotNull(renewed);
        assertTrue(renewed.getLastHeartbeat().isAfter(originalHeartbeat));
    }
    
    @Test
    void testRenewNonExistentInstance() {
        // When
        ServiceInstance renewed = storage.renew("non-existent", "instance-1");
        
        // Then
        assertNull(renewed);
    }
    
    @Test
    void testRenewWithNullParameters() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
            () -> storage.renew(null, "instance-1"));
        assertThrows(IllegalArgumentException.class, 
            () -> storage.renew("service-1", null));
    }
    
    @Test
    void testGetInstances() {
        // Given
        ServiceInstance instance1 = createTestInstance("test-service", "instance-1", "localhost", 8080);
        ServiceInstance instance2 = createTestInstance("test-service", "instance-2", "localhost", 8081);
        ServiceInstance instance3 = createTestInstance("other-service", "instance-3", "localhost", 8082);
        
        storage.register(instance1);
        storage.register(instance2);
        storage.register(instance3);
        
        // When
        List<ServiceInstance> testServiceInstances = storage.getInstances("test-service");
        List<ServiceInstance> otherServiceInstances = storage.getInstances("other-service");
        List<ServiceInstance> nonExistentInstances = storage.getInstances("non-existent");
        List<ServiceInstance> nullServiceInstances = storage.getInstances(null);
        
        // Then
        assertEquals(2, testServiceInstances.size());
        assertEquals(1, otherServiceInstances.size());
        assertTrue(nonExistentInstances.isEmpty());
        assertTrue(nullServiceInstances.isEmpty());
    }
    
    @Test
    void testGetHealthyInstances() {
        // Given
        ServiceInstance healthyInstance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        ServiceInstance unhealthyInstance = createTestInstance("test-service", "instance-2", "localhost", 8081);
        unhealthyInstance.setStatus(InstanceStatus.DOWN);
        
        storage.register(healthyInstance);
        storage.register(unhealthyInstance);
        
        // When
        List<ServiceInstance> healthyInstances = storage.getHealthyInstances("test-service");
        
        // Then
        assertEquals(1, healthyInstances.size());
        assertEquals("instance-1", healthyInstances.get(0).getInstanceId());
        assertEquals(InstanceStatus.UP, healthyInstances.get(0).getStatus());
    }
    
    @Test
    void testGetInstance() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        storage.register(instance);
        
        // When
        ServiceInstance found = storage.getInstance("test-service", "instance-1");
        ServiceInstance notFound = storage.getInstance("test-service", "instance-2");
        ServiceInstance nullService = storage.getInstance(null, "instance-1");
        ServiceInstance nullInstance = storage.getInstance("test-service", null);
        
        // Then
        assertNotNull(found);
        assertEquals("instance-1", found.getInstanceId());
        assertNull(notFound);
        assertNull(nullService);
        assertNull(nullInstance);
    }
    
    @Test
    void testGetServices() {
        // Given
        storage.register(createTestInstance("service-1", "instance-1", "localhost", 8080));
        storage.register(createTestInstance("service-2", "instance-2", "localhost", 8081));
        storage.register(createTestInstance("service-1", "instance-3", "localhost", 8082));
        
        // When
        Set<String> services = storage.getServices();
        
        // Then
        assertEquals(2, services.size());
        assertTrue(services.contains("service-1"));
        assertTrue(services.contains("service-2"));
    }
    
    @Test
    void testGetAllInstances() {
        // Given
        storage.register(createTestInstance("service-1", "instance-1", "localhost", 8080));
        storage.register(createTestInstance("service-1", "instance-2", "localhost", 8081));
        storage.register(createTestInstance("service-2", "instance-3", "localhost", 8082));
        
        // When
        Map<String, List<ServiceInstance>> allInstances = storage.getAllInstances();
        
        // Then
        assertEquals(2, allInstances.size());
        assertEquals(2, allInstances.get("service-1").size());
        assertEquals(1, allInstances.get("service-2").size());
    }
    
    @Test
    void testUpdateInstanceStatus() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        storage.register(instance);
        
        // When
        ServiceInstance updated = storage.updateInstanceStatus("test-service", "instance-1", InstanceStatus.DOWN);
        
        // Then
        assertNotNull(updated);
        assertEquals(InstanceStatus.DOWN, updated.getStatus());
    }
    
    @Test
    void testUpdateInstanceStatusWithNullParameters() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
            () -> storage.updateInstanceStatus(null, "instance-1", InstanceStatus.UP));
        assertThrows(IllegalArgumentException.class, 
            () -> storage.updateInstanceStatus("service-1", null, InstanceStatus.UP));
        assertThrows(IllegalArgumentException.class, 
            () -> storage.updateInstanceStatus("service-1", "instance-1", null));
    }
    
    @Test
    void testUpdateNonExistentInstanceStatus() {
        // When
        ServiceInstance updated = storage.updateInstanceStatus("non-existent", "instance-1", InstanceStatus.DOWN);
        
        // Then
        assertNull(updated);
    }
    
    @Test
    void testGetStatistics() {
        // Given
        ServiceInstance healthyInstance = createTestInstance("service-1", "instance-1", "localhost", 8080);
        ServiceInstance unhealthyInstance = createTestInstance("service-1", "instance-2", "localhost", 8081);
        unhealthyInstance.setStatus(InstanceStatus.DOWN);
        ServiceInstance anotherHealthyInstance = createTestInstance("service-2", "instance-3", "localhost", 8082);
        
        storage.register(healthyInstance);
        storage.register(unhealthyInstance);
        storage.register(anotherHealthyInstance);
        
        // When
        Map<String, Object> stats = storage.getStatistics();
        
        // Then
        assertEquals(2, stats.get("totalServices"));
        assertEquals(3, stats.get("totalInstances"));
        assertEquals(2L, stats.get("healthyInstances"));
        assertEquals(1L, stats.get("unhealthyInstances"));
        assertEquals("memory", stats.get("storageType"));
        assertEquals(true, stats.get("healthy"));
    }
    
    @Test
    void testIsHealthy() {
        // When & Then
        assertTrue(storage.isHealthy());
    }
    
    @Test
    void testIsHealthyAfterShutdown() {
        // Given
        storage.shutdown();
        
        // When & Then
        assertFalse(storage.isHealthy());
    }
    
    @Test
    void testCleanupExpiredInstances() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        storage.register(instance);
        
        // When
        int cleanedUp = storage.cleanupExpiredInstances();
        
        // Then
        assertEquals(0, cleanedUp); // Instance should not be expired yet
        
        List<ServiceInstance> instances = storage.getInstances("test-service");
        assertEquals(1, instances.size());
    }
    
    @Test
    void testClear() {
        // Given
        storage.register(createTestInstance("service-1", "instance-1", "localhost", 8080));
        storage.register(createTestInstance("service-2", "instance-2", "localhost", 8081));
        
        // When
        storage.clear();
        
        // Then
        assertTrue(storage.getServices().isEmpty());
        assertTrue(storage.getAllInstances().isEmpty());
    }
    
    @Test
    void testShutdown() {
        // Given
        storage.register(createTestInstance("service-1", "instance-1", "localhost", 8080));
        
        // When
        storage.shutdown();
        
        // Then
        assertFalse(storage.isHealthy());
        
        // Operations after shutdown should throw exception
        assertThrows(IllegalStateException.class, 
            () -> storage.register(createTestInstance("service-2", "instance-2", "localhost", 8081)));
    }
    
    @Test
    void testConcurrentOperations() throws InterruptedException {
        // Given
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // When - Concurrent registrations
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String instanceId = "instance-" + threadId + "-" + j;
                        ServiceInstance instance = createTestInstance("test-service", instanceId, "localhost", 8080 + j);
                        storage.register(instance);
                        
                        // Perform some operations
                        storage.renew("test-service", instanceId);
                        storage.getInstance("test-service", instanceId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        
        List<ServiceInstance> instances = storage.getInstances("test-service");
        assertEquals(threadCount * operationsPerThread, instances.size());
        
        executor.shutdown();
    }
    
    private ServiceInstance createTestInstance(String serviceId, String instanceId, String host, int port) {
        ServiceInstance instance = new ServiceInstance(serviceId, instanceId, host, port);
        instance.setStatus(InstanceStatus.UP);
        
        // Add some metadata
        instance.setMetadata(Map.of(
            "version", "1.0.0",
            "zone", "test-zone"
        ));
        
        // Add health check config
        HealthCheckConfig healthCheck = new HealthCheckConfig();
        healthCheck.setEnabled(true);
        healthCheck.setPath("/health");
        healthCheck.setInterval(Duration.ofSeconds(30));
        instance.setHealthCheck(healthCheck);
        
        return instance;
    }
}