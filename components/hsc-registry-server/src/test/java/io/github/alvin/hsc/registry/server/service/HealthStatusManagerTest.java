package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.*;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Health Status Manager Test
 * 健康状态管理器测试
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class HealthStatusManagerTest {

    @Mock
    private RegistryService registryService;

    @Mock
    private HealthCheckService healthCheckService;

    private HealthStatusManager healthStatusManager;

    @BeforeEach
    void setUp() {
        healthStatusManager = new HealthStatusManager(registryService);
        healthStatusManager.setHealthCheckService(healthCheckService);
    }

    @Test
    void testUpdateInstanceStatus_ValidTransition() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.UP);

        // When
        StepVerifier.create(healthStatusManager.updateInstanceStatus(instance, InstanceStatus.DOWN, "Health check failed"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.DOWN, instance.getStatus());
        assertEquals(InstanceStatus.UP, healthStatusManager.getPreviousStatus(instance.getInstanceId()));
    }

    @Test
    void testUpdateInstanceStatus_SameStatus() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.UP);

        // When
        StepVerifier.create(healthStatusManager.updateInstanceStatus(instance, InstanceStatus.UP, "Same status"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.UP, instance.getStatus());
    }

    @Test
    void testUpdateInstanceStatus_ToUp() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.DOWN);
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(true);
        instance.setHealthCheck(config);

        // When
        StepVerifier.create(healthStatusManager.updateInstanceStatus(instance, InstanceStatus.UP, "Health recovered"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.UP, instance.getStatus());
        verify(healthCheckService).scheduleHealthCheck(instance);
    }

    @Test
    void testUpdateInstanceStatus_ToDown_ShouldNotAutoDeregister() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.UP);

        // When
        StepVerifier.create(healthStatusManager.updateInstanceStatus(instance, InstanceStatus.DOWN, "Health check failed"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.DOWN, instance.getStatus());
        verify(registryService, never()).deregister(anyString(), anyString());
    }

    @Test
    void testUpdateInstanceStatus_ToDown_ShouldAutoDeregister() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.UP);
        when(registryService.deregister(anyString(), anyString())).thenReturn(Mono.empty());

        // When - Use "expired" in reason to trigger auto-deregistration
        StepVerifier.create(healthStatusManager.updateInstanceStatus(instance, InstanceStatus.DOWN, "Instance expired"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.DOWN, instance.getStatus());
        verify(registryService).deregister(instance.getServiceId(), instance.getInstanceId());
    }

    @Test
    void testUpdateInstanceStatus_ToOutOfService() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.UP);

        // When
        StepVerifier.create(healthStatusManager.updateInstanceStatus(instance, InstanceStatus.OUT_OF_SERVICE, "Manual offline"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.OUT_OF_SERVICE, instance.getStatus());
        verify(healthCheckService).cancelHealthCheck(instance.getInstanceId());
    }

    @Test
    void testUpdateInstanceStatus_ToUnknown() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.UP);
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(true);
        instance.setHealthCheck(config);

        // When
        StepVerifier.create(healthStatusManager.updateInstanceStatus(instance, InstanceStatus.UNKNOWN, "Network issue"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.UNKNOWN, instance.getStatus());
        verify(healthCheckService).scheduleHealthCheck(instance);
    }

    @Test
    void testUpdateInstanceStatus_ToStarting() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.DOWN);
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(true);
        instance.setHealthCheck(config);

        // When
        StepVerifier.create(healthStatusManager.updateInstanceStatus(instance, InstanceStatus.STARTING, "Instance restarting"))
                .verifyComplete();

        // Then
        assertEquals(InstanceStatus.STARTING, instance.getStatus());
        verify(healthCheckService).scheduleHealthCheck(instance);
    }

    @Test
    void testGetHealthEvents() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.UP);

        // When - Subscribe to events and trigger a status change
        StepVerifier.create(
                healthStatusManager.getHealthEvents()
                        .take(1)
                        .doOnSubscribe(subscription -> {
                            healthStatusManager.updateInstanceStatus(instance, InstanceStatus.DOWN, "Test event")
                                    .subscribe();
                        })
        )
        .assertNext(event -> {
            assertEquals(instance.getInstanceId(), event.getInstanceId());
            assertEquals(InstanceStatus.UP, event.getPreviousStatus());
            assertEquals(InstanceStatus.DOWN, event.getCurrentStatus());
            assertEquals("Test event", event.getMessage());
            assertTrue(event.isStatusChanged());
        })
        .verifyComplete();
    }

    @Test
    void testForceOfflineInstance() {
        // Given
        ServiceInstance instance = createTestInstance();
        when(registryService.getInstances("test-service")).thenReturn(Flux.just(instance));

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
        when(registryService.getInstances("test-service")).thenReturn(Flux.just(instance));

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
    void testGetPreviousStatus() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setStatus(InstanceStatus.UP);

        // When
        healthStatusManager.updateInstanceStatus(instance, InstanceStatus.DOWN, "Test").block();

        // Then
        assertEquals(InstanceStatus.UP, healthStatusManager.getPreviousStatus(instance.getInstanceId()));
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