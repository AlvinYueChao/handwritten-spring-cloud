package io.github.alvin.hsc.registry.server.config;

import io.github.alvin.hsc.registry.server.model.ServiceCatalog;
import io.github.alvin.hsc.registry.server.service.DiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.boot.actuator.health.Status;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Actuator Config Test
 * Actuator 配置测试
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class ActuatorConfigTest {

    @Mock
    private DiscoveryService discoveryService;

    private ActuatorConfig actuatorConfig;

    @BeforeEach
    void setUp() {
        actuatorConfig = new ActuatorConfig(discoveryService);
    }

    @Test
    void testRegistryHealthIndicatorUp() {
        // 准备测试数据
        ServiceCatalog catalog = new ServiceCatalog();
        catalog.setServices(Map.of("test-service", List.of()));
        
        when(discoveryService.getCatalog()).thenReturn(Mono.just(catalog));

        // 获取健康检查指示器
        HealthIndicator healthIndicator = actuatorConfig.registryHealthIndicator();
        Health health = healthIndicator.health();

        // 验证结果
        assertEquals(Status.UP, health.getStatus());
        assertEquals("Registry is operational", health.getDetails().get("status"));
        assertEquals(1, health.getDetails().get("totalServices"));
        assertEquals(0, health.getDetails().get("totalInstances"));
        assertNotNull(health.getDetails().get("timestamp"));
    }

    @Test
    void testRegistryHealthIndicatorDown() {
        // 模拟服务异常
        when(discoveryService.getCatalog()).thenReturn(Mono.error(new RuntimeException("Service unavailable")));

        // 获取健康检查指示器
        HealthIndicator healthIndicator = actuatorConfig.registryHealthIndicator();
        Health health = healthIndicator.health();

        // 验证结果
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Registry service unavailable", health.getDetails().get("status"));
        assertEquals("Failed to access service catalog", health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("timestamp"));
    }

    @Test
    void testStorageHealthIndicatorUp() {
        // 准备测试数据
        ServiceCatalog catalog = new ServiceCatalog();
        catalog.setServices(Map.of());
        
        when(discoveryService.getCatalog()).thenReturn(Mono.just(catalog));

        // 获取存储健康检查指示器
        HealthIndicator healthIndicator = actuatorConfig.storageHealthIndicator();
        Health health = healthIndicator.health();

        // 验证结果
        assertEquals(Status.UP, health.getStatus());
        assertEquals("Storage is accessible", health.getDetails().get("status"));
        assertEquals("in-memory", health.getDetails().get("type"));
        assertNotNull(health.getDetails().get("timestamp"));
    }

    @Test
    void testStorageHealthIndicatorDown() {
        // 模拟存储异常
        when(discoveryService.getCatalog()).thenReturn(Mono.error(new RuntimeException("Storage error")));

        // 获取存储健康检查指示器
        HealthIndicator healthIndicator = actuatorConfig.storageHealthIndicator();
        Health health = healthIndicator.health();

        // 验证结果
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Storage is not accessible", health.getDetails().get("status"));
        assertEquals("Storage error", health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("timestamp"));
    }
}