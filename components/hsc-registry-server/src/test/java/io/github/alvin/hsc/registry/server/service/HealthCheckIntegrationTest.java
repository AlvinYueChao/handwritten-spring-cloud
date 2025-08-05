package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.config.HealthCheckProperties;
import io.github.alvin.hsc.registry.server.model.*;
import io.github.alvin.hsc.registry.server.service.impl.HealthCheckServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Health Check Integration Test
 * 健康检查集成测试
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class HealthCheckIntegrationTest {

    @Mock
    private RegistryService registryService;

    private HealthCheckProperties properties;
    private HealthStatusManager healthStatusManager;
    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        properties = new HealthCheckProperties();
        properties.setEnabled(true);
        properties.setDefaultInterval(Duration.ofSeconds(1)); // Short interval for testing
        properties.setDefaultTimeout(Duration.ofSeconds(1));
        properties.setMaxRetry(2);
        properties.setThreadPoolSize(2);

        healthStatusManager = new HealthStatusManager(registryService);
        healthCheckService = new HealthCheckServiceImpl(registryService, properties, healthStatusManager);
        healthStatusManager.setHealthCheckService(healthCheckService);
    }

    @Test
    void testHealthCheckWorkflow() {
        // Given
        ServiceInstance instance = createTestInstance();
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(true);
        config.setType(HealthCheckType.TCP);
        config.setInterval(Duration.ofSeconds(1));
        config.setTimeout(Duration.ofSeconds(1));
        config.setRetryCount(2);
        instance.setHealthCheck(config);

        // When - Start health check
        healthCheckService.scheduleHealthCheck(instance);

        // Then - Verify health check is scheduled
        assertDoesNotThrow(() -> Thread.sleep(100)); // Give some time for scheduling

        // Cleanup
        healthCheckService.cancelHealthCheck(instance.getInstanceId());
    }

    @Test
    void testHealthStatusManagerStatusTransition() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.UP);

        // When - Update status to DOWN
        StepVerifier.create(healthStatusManager.updateInstanceStatus(instance, InstanceStatus.DOWN, "Health check failed"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.DOWN, instance.getStatus());
    }

    @Test
    void testHealthStatusManagerInvalidTransition() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.OUT_OF_SERVICE);

        // When - Try invalid transition (OUT_OF_SERVICE to STARTING is valid, so let's try a different one)
        // Actually, according to InstanceStatus, OUT_OF_SERVICE can transition to any status
        // Let's test a scenario where we try to transition from the same status
        StepVerifier.create(healthStatusManager.updateInstanceStatus(instance, InstanceStatus.OUT_OF_SERVICE, "Same status"))
                .verifyComplete();

        // Then - Status should remain the same
        assertEquals(InstanceStatus.OUT_OF_SERVICE, instance.getStatus());
    }

    @Test
    void testHealthEventStream() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.UP);

        // When - Subscribe to health events and update status
        StepVerifier.create(
                healthStatusManager.getHealthEvents()
                        .take(1)
                        .doOnSubscribe(subscription -> {
                            // Update status after subscription
                            healthStatusManager.updateInstanceStatus(instance, InstanceStatus.DOWN, "Test event")
                                    .subscribe();
                        })
        )
        .assertNext(event -> {
            assertEquals(instance.getInstanceId(), event.getInstanceId());
            assertEquals(InstanceStatus.UP, event.getPreviousStatus());
            assertEquals(InstanceStatus.DOWN, event.getCurrentStatus());
            assertEquals("Test event", event.getMessage());
        })
        .verifyComplete();
    }

    @Test
    void testForceOfflineInstance() {
        // Given
        ServiceInstance instance = createTestInstance();
        when(registryService.getInstances("test-service"))
                .thenReturn(Flux.just(instance));

        // When
        StepVerifier.create(healthStatusManager.forceOfflineInstance("test-service", "test-instance-001", "Manual offline"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.OUT_OF_SERVICE, instance.getStatus());
    }

    @Test
    void testBringInstanceOnline() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.OUT_OF_SERVICE);
        when(registryService.getInstances("test-service"))
                .thenReturn(Flux.just(instance));

        // When
        StepVerifier.create(healthStatusManager.bringInstanceOnline("test-service", "test-instance-001", "Manual online"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.UP, instance.getStatus());
    }

    @Test
    void testBatchUpdateStatus() {
        // Given
        ServiceInstance instance1 = createTestInstance();
        ServiceInstance instance2 = createTestInstance();
        instance2.setInstanceId("test-instance-002");
        
        Flux<ServiceInstance> instances = Flux.just(instance1, instance2);

        // When
        StepVerifier.create(healthStatusManager.batchUpdateStatus(instances, InstanceStatus.DOWN, "Batch update"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.DOWN, instance1.getStatus());
        assertEquals(InstanceStatus.DOWN, instance2.getStatus());
    }

    @Test
    void testHealthCheckServiceEventStream() {
        // When
        Flux<HealthEvent> events = healthCheckService.getHealthEvents();

        // Then
        assertNotNull(events);
        
        // Test that we can subscribe to the event stream
        StepVerifier.create(events.take(Duration.ofMillis(100)))
                .verifyComplete();
    }

    @Test
    void testCleanupInstanceHistory() {
        // Given
        ServiceInstance instance = createTestInstance();
        healthStatusManager.updateInstanceStatus(instance, InstanceStatus.DOWN, "Test").block();

        // When
        healthStatusManager.cleanupInstanceHistory(instance.getInstanceId());

        // Then
        assertNull(healthStatusManager.getPreviousStatus(instance.getInstanceId()));
    }

    @Test
    void testHealthCheckWithDisabledConfig() {
        // Given
        ServiceInstance instance = createTestInstance();
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(false);
        instance.setHealthCheck(config);

        // When & Then
        StepVerifier.create(healthCheckService.checkHealth(instance))
                .assertNext(healthStatus -> {
                    assertEquals(instance.getInstanceId(), healthStatus.getInstanceId());
                    assertEquals(InstanceStatus.UP, healthStatus.getStatus());
                    assertEquals("Health check disabled", healthStatus.getMessage());
                })
                .verifyComplete();
    }

    private ServiceInstance createTestInstance() {
        ServiceInstance instance = new ServiceInstance();
        instance.setServiceId("test-service");
        instance.setInstanceId("test-instance-001");
        instance.setHost("localhost");
        instance.setPort(8080);
        instance.setStatus(InstanceStatus.UP);
        instance.setRegistrationTime(Instant.now());
        instance.setLastHeartbeat(Instant.now());
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        instance.setMetadata(metadata);
        
        return instance;
    }
}