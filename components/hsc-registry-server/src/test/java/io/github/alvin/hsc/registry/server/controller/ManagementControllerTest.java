package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.config.ControllerTestConfig;
import io.github.alvin.hsc.registry.server.model.*;
import io.github.alvin.hsc.registry.server.service.RegistryService;
import io.github.alvin.hsc.registry.server.service.DiscoveryService;
import io.github.alvin.hsc.registry.server.service.HealthCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Management Controller Test
 * 管理控制器测试
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
@Import(ControllerTestConfig.class)
class ManagementControllerTest {

    @Mock
    private RegistryService registryService;

    @Mock
    private DiscoveryService discoveryService;

    @Mock
    private HealthCheckService healthCheckService;

    private WebTestClient webTestClient;
    private ServiceInstance testInstance;
    private ServiceCatalog testCatalog;

    @BeforeEach
    void setUp() {
        ManagementController controller = new ManagementController(
            registryService, discoveryService, healthCheckService);
        
        webTestClient = WebTestClient.bindToController(controller)
            .configureClient()
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        // 创建测试数据
        testInstance = new ServiceInstance();
        testInstance.setServiceId("test-service");
        testInstance.setInstanceId("test-instance-1");
        testInstance.setHost("localhost");
        testInstance.setPort(8080);
        testInstance.setStatus(InstanceStatus.UP);
        testInstance.setRegistrationTime(Instant.now());
        testInstance.setLastHeartbeat(Instant.now());

        testCatalog = new ServiceCatalog();
        testCatalog.setServices(Map.of("test-service", List.of(testInstance)));
    }

    @Test
    void testGetRegistryStatus() {
        when(discoveryService.getCatalog()).thenReturn(Mono.just(testCatalog));

        webTestClient.get()
            .uri("/api/v1/management/status")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.totalServices").isEqualTo(1)
            .jsonPath("$.totalInstances").isEqualTo(1)
            .jsonPath("$.healthyInstances").isEqualTo(1)
            .jsonPath("$.unhealthyInstances").isEqualTo(0);
    }

    @Test
    void testGetAllServices() {
        when(discoveryService.getCatalog()).thenReturn(Mono.just(testCatalog));

        webTestClient.get()
            .uri("/api/v1/management/services")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.services").exists()
            .jsonPath("$.services['test-service']").isArray()
            .jsonPath("$.services['test-service'][0].instanceId").isEqualTo("test-instance-1");
    }

    @Test
    void testGetServiceDetails() {
        when(registryService.getInstances("test-service"))
            .thenReturn(Flux.just(testInstance));

        webTestClient.get()
            .uri("/api/v1/management/services/test-service")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ServiceInstance.class)
            .hasSize(1);
    }

    @Test
    void testOnlineInstance() {
        when(registryService.getInstances("test-service"))
            .thenReturn(Flux.just(testInstance));

        webTestClient.put()
            .uri("/api/v1/management/services/test-service/instances/test-instance-1/online")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.serviceId").isEqualTo("test-service")
            .jsonPath("$.instanceId").isEqualTo("test-instance-1")
            .jsonPath("$.status").isEqualTo("ONLINE")
            .jsonPath("$.message").isEqualTo("Instance has been manually brought online");
    }

    @Test
    void testOfflineInstance() {
        when(registryService.getInstances("test-service"))
            .thenReturn(Flux.just(testInstance));

        webTestClient.put()
            .uri("/api/v1/management/services/test-service/instances/test-instance-1/offline")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.serviceId").isEqualTo("test-service")
            .jsonPath("$.instanceId").isEqualTo("test-instance-1")
            .jsonPath("$.status").isEqualTo("OFFLINE")
            .jsonPath("$.message").isEqualTo("Instance has been manually taken offline");
    }

    @Test
    void testForceHealthCheck() {
        HealthStatus healthStatus = new HealthStatus();
        healthStatus.setStatus(InstanceStatus.UP);
        
        when(registryService.getInstances("test-service"))
            .thenReturn(Flux.just(testInstance));
        when(healthCheckService.checkHealth(any(ServiceInstance.class)))
            .thenReturn(Mono.just(healthStatus));

        webTestClient.post()
            .uri("/api/v1/management/services/test-service/instances/test-instance-1/health-check")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.serviceId").isEqualTo("test-service")
            .jsonPath("$.instanceId").isEqualTo("test-instance-1")
            .jsonPath("$.healthStatus").isEqualTo("UP")
            .jsonPath("$.message").isEqualTo("Health check completed");
    }

    @Test
    void testGetStatistics() {
        when(discoveryService.getCatalog()).thenReturn(Mono.just(testCatalog));

        webTestClient.get()
            .uri("/api/v1/management/statistics")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.serviceInstanceCounts").exists()
            .jsonPath("$.instanceStatusCounts").exists()
            .jsonPath("$.serviceInstanceCounts['test-service']").isEqualTo(1);
    }

    @Test
    void testCleanupExpiredInstances() {
        webTestClient.post()
            .uri("/api/v1/management/cleanup")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("SUCCESS")
            .jsonPath("$.message").isEqualTo("Cleanup operation initiated");
    }

    @Test
    void testInstanceNotFound() {
        when(registryService.getInstances("nonexistent-service"))
            .thenReturn(Flux.empty());

        webTestClient.put()
            .uri("/api/v1/management/services/nonexistent-service/instances/nonexistent-instance/online")
            .exchange()
            .expectStatus().isNotFound();
    }
}