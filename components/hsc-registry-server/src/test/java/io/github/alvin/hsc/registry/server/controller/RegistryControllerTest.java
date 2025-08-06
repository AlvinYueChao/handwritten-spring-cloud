package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.model.HealthCheckConfig;
import io.github.alvin.hsc.registry.server.model.HealthCheckType;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceRegistration;
import io.github.alvin.hsc.registry.server.service.RegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Registry Controller Integration Test
 * 服务注册控制器集成测试
 * 
 * @author Alvin
 */
@WebFluxTest(RegistryController.class)
class RegistryControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RegistryService registryService;

    private ServiceRegistration testRegistration;
    private ServiceInstance testInstance;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testRegistration = new ServiceRegistration();
        testRegistration.setServiceId("test-service");
        testRegistration.setInstanceId("test-instance-001");
        testRegistration.setHost("192.168.1.100");
        testRegistration.setPort(8080);
        testRegistration.setSecure(false);
        testRegistration.setMetadata(Map.of("version", "1.0.0", "zone", "us-east-1a"));
        
        HealthCheckConfig healthCheck = new HealthCheckConfig();
        healthCheck.setEnabled(true);
        healthCheck.setPath("/actuator/health");
        healthCheck.setInterval(Duration.ofSeconds(30));
        healthCheck.setTimeout(Duration.ofSeconds(5));
        healthCheck.setType(HealthCheckType.HTTP);
        testRegistration.setHealthCheck(healthCheck);

        testInstance = new ServiceInstance("test-service", "test-instance-001", "192.168.1.100", 8080);
        testInstance.setSecure(false);
        testInstance.setStatus(InstanceStatus.UP);
        testInstance.setMetadata(Map.of("version", "1.0.0", "zone", "us-east-1a"));
        testInstance.setHealthCheck(healthCheck);
        testInstance.setRegistrationTime(Instant.now());
        testInstance.setLastHeartbeat(Instant.now());
    }

    @Test
    void testRegisterInstance_Success() {
        // Given
        when(registryService.register(any(ServiceRegistration.class)))
                .thenReturn(Mono.just(testInstance));

        // When & Then
        webTestClient.post()
                .uri("/api/v1/registry/services/test-service/instances")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testRegistration)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceInstance.class)
                .value(instance -> {
                    assert instance.getServiceId().equals("test-service");
                    assert instance.getInstanceId().equals("test-instance-001");
                    assert instance.getHost().equals("192.168.1.100");
                    assert instance.getPort() == 8080;
                    assert instance.getStatus() == InstanceStatus.UP;
                });
    }

    @Test
    void testRegisterInstance_ValidationError() {
        // Given - 无效的注册信息（缺少必填字段）
        ServiceRegistration invalidRegistration = new ServiceRegistration();
        invalidRegistration.setServiceId("test-service");
        // 缺少 instanceId, host, port

        // When & Then
        webTestClient.post()
                .uri("/api/v1/registry/services/test-service/instances")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRegistration)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_001")
                .jsonPath("$.message").isEqualTo("Request parameter validation failed");
    }

    @Test
    void testRegisterInstance_ServiceError() {
        // Given
        when(registryService.register(any(ServiceRegistration.class)))
                .thenReturn(Mono.error(new RuntimeException("Service registration failed")));

        // When & Then
        webTestClient.post()
                .uri("/api/v1/registry/services/test-service/instances")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testRegistration)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void testDeregisterInstance_Success() {
        // Given
        when(registryService.deregister(eq("test-service"), eq("test-instance-001")))
                .thenReturn(Mono.empty());

        // When & Then
        webTestClient.delete()
                .uri("/api/v1/registry/services/test-service/instances/test-instance-001")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void testDeregisterInstance_ServiceError() {
        // Given
        when(registryService.deregister(eq("test-service"), eq("test-instance-001")))
                .thenReturn(Mono.error(new RuntimeException("Deregistration failed")));

        // When & Then
        webTestClient.delete()
                .uri("/api/v1/registry/services/test-service/instances/test-instance-001")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void testRenewInstance_Success() {
        // Given
        when(registryService.renew(eq("test-service"), eq("test-instance-001")))
                .thenReturn(Mono.just(testInstance));

        // When & Then
        webTestClient.put()
                .uri("/api/v1/registry/services/test-service/instances/test-instance-001/heartbeat")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceInstance.class)
                .value(instance -> {
                    assert instance.getServiceId().equals("test-service");
                    assert instance.getInstanceId().equals("test-instance-001");
                });
    }

    @Test
    void testRenewInstance_NotFound() {
        // Given
        when(registryService.renew(eq("test-service"), eq("non-existent-instance")))
                .thenReturn(Mono.error(new RuntimeException("Instance not found")));

        // When & Then
        webTestClient.put()
                .uri("/api/v1/registry/services/test-service/instances/non-existent-instance/heartbeat")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testGetServiceInstances_Success() {
        // Given
        ServiceInstance instance2 = new ServiceInstance("test-service", "test-instance-002", "192.168.1.101", 8080);
        when(registryService.getInstances(eq("test-service")))
                .thenReturn(Flux.just(testInstance, instance2));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/registry/services/test-service/instances")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceInstance.class)
                .hasSize(2)
                .value(instances -> {
                    assert instances.get(0).getServiceId().equals("test-service");
                    assert instances.get(1).getServiceId().equals("test-service");
                });
    }

    @Test
    void testGetServiceInstances_EmptyResult() {
        // Given
        when(registryService.getInstances(eq("non-existent-service")))
                .thenReturn(Flux.empty());

        // When & Then
        webTestClient.get()
                .uri("/api/v1/registry/services/non-existent-service/instances")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceInstance.class)
                .hasSize(0);
    }

    @Test
    void testGetServiceInstances_ServiceError() {
        // Given
        when(registryService.getInstances(eq("test-service")))
                .thenReturn(Flux.error(new RuntimeException("Service error")));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/registry/services/test-service/instances")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceInstance.class)
                .hasSize(0);
    }

    @Test
    void testGetServices_Success() {
        // Given
        when(registryService.getServices())
                .thenReturn(Flux.just("service-a", "service-b", "test-service"));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/registry/services")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0]").isEqualTo("service-a")
                .jsonPath("$[1]").isEqualTo("service-b")
                .jsonPath("$[2]").isEqualTo("test-service");
    }

    @Test
    void testGetServices_EmptyResult() {
        // Given
        when(registryService.getServices())
                .thenReturn(Flux.empty());

        // When & Then
        webTestClient.get()
                .uri("/api/v1/registry/services")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void testGetServices_ServiceError() {
        // Given
        when(registryService.getServices())
                .thenReturn(Flux.error(new RuntimeException("Service error")));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/registry/services")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void testRegisterInstance_WithComplexMetadata() {
        // Given - 包含复杂元数据的注册信息
        ServiceRegistration complexRegistration = new ServiceRegistration();
        complexRegistration.setServiceId("complex-service");
        complexRegistration.setInstanceId("complex-instance-001");
        complexRegistration.setHost("10.0.0.100");
        complexRegistration.setPort(9090);
        complexRegistration.setSecure(true);
        complexRegistration.setMetadata(Map.of(
                "version", "2.1.0",
                "zone", "us-west-2b",
                "datacenter", "dc1",
                "environment", "production"
        ));

        ServiceInstance complexInstance = new ServiceInstance("complex-service", "complex-instance-001", "10.0.0.100", 9090);
        complexInstance.setSecure(true);
        complexInstance.setMetadata(complexRegistration.getMetadata());

        when(registryService.register(any(ServiceRegistration.class)))
                .thenReturn(Mono.just(complexInstance));

        // When & Then
        webTestClient.post()
                .uri("/api/v1/registry/services/complex-service/instances")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(complexRegistration)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceInstance.class)
                .value(instance -> {
                    assert instance.getServiceId().equals("complex-service");
                    assert instance.isSecure();
                    assert instance.getMetadata().get("environment").equals("production");
                });
    }
}