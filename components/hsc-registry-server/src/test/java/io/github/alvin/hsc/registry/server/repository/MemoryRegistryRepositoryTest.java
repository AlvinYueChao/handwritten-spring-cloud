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
 * Unit tests for MemoryRegistryRepository
 * 
 * @author Alvin
 */
class MemoryRegistryRepositoryTest {
    
    private MemoryRegistryRepository repository;
    
    @BeforeEach
    void setUp() {
        repository = new MemoryRegistryRepository();
    }
    
    @AfterEach
    void tearDown() {
        repository.shutdown();
    }
    
    @Test
    void testRegisterServiceInstance() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        
        // When
        ServiceInstance registered = repository.register(instance);
        
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
        assertThrows(IllegalArgumentException.class, () -> repository.register(null));
    }
    
    @Test
    void testUpdateExistingInstance() {
        // Given
        ServiceInstance instance1 = createTestInstance("test-service", "instance-1", "localhost", 8080);
        ServiceInstance instance2 = createTestInstance("test-service", "instance-1", "localhost", 8081);
        
        // When
        repository.register(instance1);
        ServiceInstance updated = repository.register(instance2);
        
        // Then
        assertEquals(8081, updated.getPort());
        
        List<ServiceInstance> instances = repository.getInstances("test-service");
        assertEquals(1, instances.size());
        assertEquals(8081, instances.get(0).getPort());
    }
    
    @Test
    void testDeregisterServiceInstance() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        repository.register(instance);
        
        // When
        ServiceInstance deregistered = repository.deregister("test-service", "instance-1");
        
        // Then
        assertNotNull(deregistered);
        assertEquals("instance-1", deregistered.getInstanceId());
        
        List<ServiceInstance> instances = repository.getInstances("test-service");
        assertTrue(instances.isEmpty());
    }
    
    @Test
    void testDeregisterNonExistentInstance() {
        // When
        ServiceInstance deregistered = repository.deregister("non-existent", "instance-1");
        
        // Then
        assertNull(deregistered);
    }
    
    @Test
    void testDeregisterWithNullParameters() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
            () -> repository.deregister(null, "instance-1"));
        assertThrows(IllegalArgumentException.class, 
            () -> repository.deregister("service-1", null));
    }
    
    @Test
    void testRenewHeartbeat() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        repository.register(instance);
        Instant originalHeartbeat = instance.getLastHeartbeat();
        
        // Wait a bit to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        ServiceInstance renewed = repository.renew("test-service", "instance-1");
        
        // Then
        assertNotNull(renewed);
        assertTrue(renewed.getLastHeartbeat().isAfter(originalHeartbeat));
    }
    
    @Test
    void testRenewNonExistentInstance() {
        // When
        ServiceInstance renewed = repository.renew("non-existent", "instance-1");
        
        // Then
        assertNull(renewed);
    }
    
    @Test
    void testGetInstances() {
        // Given
        ServiceInstance instance1 = createTestInstance("test-service", "instance-1", "localhost", 8080);
        ServiceInstance instance2 = createTestInstance("test-service", "instance-2", "localhost", 8081);
        ServiceInstance instance3 = createTestInstance("other-service", "instance-3", "localhost", 8082);
        
        repository.register(instance1);
        repository.register(instance2);
        repository.register(instance3);
        
        // When
        List<ServiceInstance> testServiceInstances = repository.getInstances("test-service");
        List<ServiceInstance> otherServiceInstances = repository.getInstances("other-service");
        List<ServiceInstance> nonExistentInstances = repository.getInstances("non-existent");
        
        // Then
        assertEquals(2, testServiceInstances.size());
        assertEquals(1, otherServiceInstances.size());
        assertTrue(nonExistentInstances.isEmpty());
    }
    
    @Test
    void testGetHealthyInstances() {
        // Given
        ServiceInstance healthyInstance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        ServiceInstance unhealthyInstance = createTestInstance("test-service", "instance-2", "localhost", 8081);
        unhealthyInstance.setStatus(InstanceStatus.DOWN);
        
        repository.register(healthyInstance);
        repository.register(unhealthyInstance);
        
        // When
        List<ServiceInstance> healthyInstances = repository.getHealthyInstances("test-service");
        
        // Then
        assertEquals(1, healthyInstances.size());
        assertEquals("instance-1", healthyInstances.get(0).getInstanceId());
        assertEquals(InstanceStatus.UP, healthyInstances.get(0).getStatus());
    }
    
    @Test
    void testGetInstance() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        repository.register(instance);
        
        // When
        ServiceInstance found = repository.getInstance("test-service", "instance-1");
        ServiceInstance notFound = repository.getInstance("test-service", "instance-2");
        
        // Then
        assertNotNull(found);
        assertEquals("instance-1", found.getInstanceId());
        assertNull(notFound);
    }
    
    @Test
    void testGetServices() {
        // Given
        repository.register(createTestInstance("service-1", "instance-1", "localhost", 8080));
        repository.register(createTestInstance("service-2", "instance-2", "localhost", 8081));
        repository.register(createTestInstance("service-1", "instance-3", "localhost", 8082));
        
        // When
        Set<String> services = repository.getServices();
        
        // Then
        assertEquals(2, services.size());
        assertTrue(services.contains("service-1"));
        assertTrue(services.contains("service-2"));
    }
    
    @Test
    void testGetAllInstances() {
        // Given
        repository.register(createTestInstance("service-1", "instance-1", "localhost", 8080));
        repository.register(createTestInstance("service-1", "instance-2", "localhost", 8081));
        repository.register(createTestInstance("service-2", "instance-3", "localhost", 8082));
        
        // When
        Map<String, List<ServiceInstance>> allInstances = repository.getAllInstances();
        
        // Then
        assertEquals(2, allInstances.size());
        assertEquals(2, allInstances.get("service-1").size());
        assertEquals(1, allInstances.get("service-2").size());
    }
    
    @Test
    void testUpdateInstanceStatus() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        repository.register(instance);
        
        // When
        ServiceInstance updated = repository.updateInstanceStatus("test-service", "instance-1", InstanceStatus.DOWN);
        
        // Then
        assertNotNull(updated);
        assertEquals(InstanceStatus.DOWN, updated.getStatus());
    }
    
    @Test
    void testUpdateInstanceStatusInvalidTransition() {
        // Given
        ServiceInstance instance = createTestInstance("test-service", "instance-1", "localhost", 8080);
        instance.setStatus(InstanceStatus.OUT_OF_SERVICE);
        repository.register(instance);
        
        // When & Then - This should work as OUT_OF_SERVICE can transition to any state
        assertDoesNotThrow(() -> 
            repository.updateInstanceStatus("test-service", "instance-1", InstanceStatus.UP));
    }
    
    @Test
    void testUpdateInstanceStatusWithNullParameters() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
            () -> repository.updateInstanceStatus(null, "instance-1", InstanceStatus.UP));
        assertThrows(IllegalArgumentException.class, 
            () -> repository.updateInstanceStatus("service-1", null, InstanceStatus.UP));
        assertThrows(IllegalArgumentException.class, 
            () -> repository.updateInstanceStatus("service-1", "instance-1", null));
    }
    
    @Test
    void testGetStatistics() {
        // Given
        ServiceInstance healthyInstance = createTestInstance("service-1", "instance-1", "localhost", 8080);
        ServiceInstance unhealthyInstance = createTestInstance("service-1", "instance-2", "localhost", 8081);
        unhealthyInstance.setStatus(InstanceStatus.DOWN);
        ServiceInstance anotherHealthyInstance = createTestInstance("service-2", "instance-3", "localhost", 8082);
        
        repository.register(healthyInstance);
        repository.register(unhealthyInstance);
        repository.register(anotherHealthyInstance);
        
        // When
        Map<String, Object> stats = repository.getStatistics();
        
        // Then
        assertEquals(2, stats.get("totalServices"));
        assertEquals(3, stats.get("totalInstances"));
        assertEquals(2L, stats.get("healthyInstances"));
        assertEquals(1L, stats.get("unhealthyInstances"));
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
                        repository.register(instance);
                        
                        // Perform some operations
                        repository.renew("test-service", instanceId);
                        repository.getInstance("test-service", instanceId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        
        List<ServiceInstance> instances = repository.getInstances("test-service");
        assertEquals(threadCount * operationsPerThread, instances.size());
        
        executor.shutdown();
    }
    
    @Test
    void testClear() {
        // Given
        repository.register(createTestInstance("service-1", "instance-1", "localhost", 8080));
        repository.register(createTestInstance("service-2", "instance-2", "localhost", 8081));
        
        // When
        repository.clear();
        
        // Then
        assertTrue(repository.getServices().isEmpty());
        assertTrue(repository.getAllInstances().isEmpty());
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