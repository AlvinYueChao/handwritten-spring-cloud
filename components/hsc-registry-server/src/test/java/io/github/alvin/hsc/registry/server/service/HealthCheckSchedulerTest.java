package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.config.HealthCheckProperties;
import io.github.alvin.hsc.registry.server.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Health Check Scheduler Test
 * 健康检查调度器测试
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class HealthCheckSchedulerTest {

    @Mock
    private RegistryService registryService;

    @Mock
    private HealthCheckService healthCheckService;

    private HealthCheckProperties properties;
    private HealthCheckScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new HealthCheckProperties();
        properties.setEnabled(true);
        properties.setInstanceExpirationThreshold(Duration.ofSeconds(90));
        properties.setExpirationCheckInterval(Duration.ofSeconds(30));
        properties.setTaskSyncInterval(Duration.ofSeconds(60));
        
        scheduler = new HealthCheckScheduler(registryService, healthCheckService, properties);
    }

    @Test
    void testStartHealthCheckForInstance_HealthCheckEnabled() {
        // Given
        ServiceInstance instance = createTestInstance();
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(true);
        instance.setHealthCheck(config);

        // When
        scheduler.startHealthCheckForInstance(instance);

        // Then
        verify(healthCheckService).scheduleHealthCheck(instance);
    }

    @Test
    void testStartHealthCheckForInstance_HealthCheckDisabled() {
        // Given
        ServiceInstance instance = createTestInstance();
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(false);
        instance.setHealthCheck(config);

        // When
        scheduler.startHealthCheckForInstance(instance);

        // Then
        verify(healthCheckService, never()).scheduleHealthCheck(any());
    }

    @Test
    void testStartHealthCheckForInstance_NoHealthCheckConfig() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setHealthCheck(null);

        // When
        scheduler.startHealthCheckForInstance(instance);

        // Then
        verify(healthCheckService, never()).scheduleHealthCheck(any());
    }

    @Test
    void testStopHealthCheckForInstance() {
        // Given
        String instanceId = "test-instance-001";

        // When
        scheduler.stopHealthCheckForInstance(instanceId);

        // Then
        verify(healthCheckService).cancelHealthCheck(instanceId);
    }

    @Test
    void testStart_HealthCheckDisabled() {
        // Given
        properties.setEnabled(false);
        HealthCheckScheduler disabledScheduler = new HealthCheckScheduler(
                registryService, healthCheckService, properties);

        // When & Then
        assertDoesNotThrow(disabledScheduler::start);
    }

    @Test
    void testStart_HealthCheckEnabled() {
        // When & Then
        assertDoesNotThrow(scheduler::start);
    }

    @Test
    void testStop() {
        // Given
        scheduler.start();

        // When & Then
        assertDoesNotThrow(scheduler::stop);
    }

    @Test
    void testCheckExpiredInstances() {
        // When
        scheduler.start();
        
        // Then
        // Note: This test mainly verifies that the scheduler can be started without errors
        // The actual scheduled task execution is tested through integration tests
        assertDoesNotThrow(() -> scheduler.stop());
    }

    @Test
    void testSyncHealthCheckTasks() {
        // When
        scheduler.start();
        
        // Then
        // Note: This test mainly verifies that the scheduler can be started without errors
        // The actual scheduled task execution is tested through integration tests
        assertDoesNotThrow(() -> scheduler.stop());
    }

    private ServiceInstance createTestInstance() {
        ServiceInstance instance = new ServiceInstance();
        instance.setServiceId("test-service");
        instance.setInstanceId("test-instance-001");
        instance.setHost("localhost");
        instance.setPort(8080);
        instance.setStatus(InstanceStatus.UP);
        instance.setLastHeartbeat(Instant.now()); // Recent heartbeat
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        instance.setMetadata(metadata);
        
        return instance;
    }

    private ServiceInstance createExpiredInstance() {
        ServiceInstance instance = new ServiceInstance();
        instance.setServiceId("test-service");
        instance.setInstanceId("expired-instance-001");
        instance.setHost("localhost");
        instance.setPort(8081);
        instance.setStatus(InstanceStatus.UP);
        // Set heartbeat to 2 minutes ago (expired)
        instance.setLastHeartbeat(Instant.now().minus(Duration.ofMinutes(2)));
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        instance.setMetadata(metadata);
        
        return instance;
    }
}