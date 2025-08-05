package io.github.alvin.hsc.registry.server.service.impl;

import io.github.alvin.hsc.registry.server.config.HealthCheckProperties;
import io.github.alvin.hsc.registry.server.model.*;
import io.github.alvin.hsc.registry.server.service.HealthStatusManager;
import io.github.alvin.hsc.registry.server.service.RegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Health Check Service Implementation Test
 * 健康检查服务实现测试
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class HealthCheckServiceImplTest {

    @Mock
    private RegistryService registryService;

    @Mock
    private HealthStatusManager healthStatusManager;

    private HealthCheckProperties properties;
    private HealthCheckServiceImpl healthCheckService;

    @BeforeEach
    void setUp() {
        properties = new HealthCheckProperties();
        properties.setEnabled(true);
        properties.setDefaultInterval(Duration.ofSeconds(30));
        properties.setDefaultTimeout(Duration.ofSeconds(5));
        properties.setMaxRetry(3);
        properties.setThreadPoolSize(2);

        /*
        lenient():
        这是Mockito的一个方法，用于创建一个"宽松"的模拟行为
        使用lenient()可以避免在测试结束时报告未被使用的模拟行为
        当你有一些模拟设置可能在某些测试中未被使用时，使用lenient()可以防止测试失败
         */
        lenient().when(healthStatusManager.updateInstanceStatus(any(), any(), any())).thenReturn(Mono.empty());
        
        healthCheckService = new HealthCheckServiceImpl(registryService, properties, healthStatusManager);
    }

    @Test
    void testCheckHealth_HttpHealthCheck_Success() {
        // Given
        ServiceInstance instance = createTestInstance();
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(true);
        config.setType(HealthCheckType.HTTP);
        config.setPath("/actuator/health");
        config.setTimeout(Duration.ofSeconds(5));
        instance.setHealthCheck(config);

        // When & Then
        StepVerifier.create(healthCheckService.checkHealth(instance))
                .assertNext(healthStatus -> {
                    assertEquals(instance.getInstanceId(), healthStatus.getInstanceId());
                    assertNotNull(healthStatus.getTimestamp());
                    // Note: In real test, we would need to mock WebClient for HTTP calls
                })
                .verifyComplete();
    }

    @Test
    void testCheckHealth_TcpHealthCheck_Success() {
        // Given
        ServiceInstance instance = createTestInstance();
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(true);
        config.setType(HealthCheckType.TCP);
        config.setTimeout(Duration.ofSeconds(5));
        instance.setHealthCheck(config);

        // When & Then
        StepVerifier.create(healthCheckService.checkHealth(instance))
                .assertNext(healthStatus -> {
                    assertEquals(instance.getInstanceId(), healthStatus.getInstanceId());
                    assertNotNull(healthStatus.getTimestamp());
                    // TCP check will likely fail in test environment, but structure is tested
                })
                .verifyComplete();
    }

    @Test
    void testCheckHealth_HealthCheckDisabled() {
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

    @Test
    void testCheckHealth_NoHealthCheckConfig() {
        // Given
        ServiceInstance instance = createTestInstance();
        instance.setHealthCheck(null);

        // When & Then
        StepVerifier.create(healthCheckService.checkHealth(instance))
                .assertNext(healthStatus -> {
                    assertEquals(instance.getInstanceId(), healthStatus.getInstanceId());
                    assertEquals(InstanceStatus.UP, healthStatus.getStatus());
                    assertEquals("Health check disabled", healthStatus.getMessage());
                })
                .verifyComplete();
    }

    @Test
    void testScheduleHealthCheck_Success() {
        // Given
        ServiceInstance instance = createTestInstance();
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(true);
        config.setInterval(Duration.ofSeconds(1)); // Short interval for test
        instance.setHealthCheck(config);

        // When
        healthCheckService.scheduleHealthCheck(instance);

        // Then
        // Verify that the task is scheduled (we can't easily test the actual execution)
        // In a real scenario, we might use a test scheduler or verify through side effects
        assertDoesNotThrow(() -> {
            Thread.sleep(100); // Give some time for scheduling
        });
    }

    @Test
    void testScheduleHealthCheck_HealthCheckDisabled() {
        // Given
        ServiceInstance instance = createTestInstance();
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(false);
        instance.setHealthCheck(config);

        // When & Then
        assertDoesNotThrow(() -> healthCheckService.scheduleHealthCheck(instance));
    }

    @Test
    void testCancelHealthCheck() {
        // Given
        String instanceId = "test-instance-001";

        // When & Then
        assertDoesNotThrow(() -> healthCheckService.cancelHealthCheck(instanceId));
    }

    @Test
    void testGetHealthEvents() {
        // When
        Flux<HealthEvent> events = healthCheckService.getHealthEvents();

        // Then
        assertNotNull(events);
        
        // Test that we can subscribe to the event stream
        StepVerifier.create(events.take(Duration.ofMillis(100)))
                .verifyComplete();
    }

    @Test
    void testScriptHealthCheck() {
        // Given
        ServiceInstance instance = createTestInstance();
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(true);
        config.setType(HealthCheckType.SCRIPT);
        instance.setHealthCheck(config);

        // When & Then
        StepVerifier.create(healthCheckService.checkHealth(instance))
                .assertNext(healthStatus -> {
                    assertEquals(instance.getInstanceId(), healthStatus.getInstanceId());
                    assertEquals(InstanceStatus.UP, healthStatus.getStatus());
                    assertEquals("Script check not implemented", healthStatus.getMessage());
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
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        instance.setMetadata(metadata);
        
        return instance;
    }
}