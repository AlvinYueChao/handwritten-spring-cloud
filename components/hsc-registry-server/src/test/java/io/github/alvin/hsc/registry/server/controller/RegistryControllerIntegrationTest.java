package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.model.HealthCheckConfig;
import io.github.alvin.hsc.registry.server.model.HealthCheckType;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Registry Controller Full Integration Test
 * 服务注册控制器完整集成测试
 * 
 * 测试完整的应用上下文和端到端流程
 * 
 * @author Alvin
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RegistryControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;
    
    private static final String VALID_API_KEY = "test-integration-key-2024";
    private static final String API_KEY_HEADER = "X-Registry-API-Key";

    @Test
    void testCompleteRegistrationFlow() {
        String serviceId = "integration-test-service";
        String instanceId = "integration-test-instance-001";

        // 1. 准备注册信息
        ServiceRegistration registration = new ServiceRegistration();
        registration.setServiceId(serviceId);
        registration.setInstanceId(instanceId);
        registration.setHost("192.168.1.200");
        registration.setPort(8080);
        registration.setSecure(false);
        registration.setMetadata(Map.of(
                "version", "1.0.0",
                "zone", "test-zone",
                "environment", "integration-test"
        ));

        HealthCheckConfig healthCheck = new HealthCheckConfig();
        healthCheck.setEnabled(true);
        healthCheck.setPath("/actuator/health");
        healthCheck.setInterval(Duration.ofSeconds(30));
        healthCheck.setTimeout(Duration.ofSeconds(5));
        healthCheck.setType(HealthCheckType.HTTP);
        registration.setHealthCheck(healthCheck);

        // 2. 注册服务实例
        webTestClient.post()
                .uri("/api/v1/registry/services/{serviceId}/instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registration)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceInstance.class)
                .value(instance -> {
                    assert instance.getServiceId().equals(serviceId);
                    assert instance.getInstanceId().equals(instanceId);
                    assert instance.getStatus() == InstanceStatus.UP;
                    assert instance.getMetadata().get("environment").equals("integration-test");
                });

        // 3. 验证服务列表包含新注册的服务
        webTestClient.get()
                .uri("/api/v1/registry/services")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[*]").value(org.hamcrest.Matchers.hasItem(serviceId));

        // 4. 获取服务实例列表
        webTestClient.get()
                .uri("/api/v1/registry/services/{serviceId}/instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceInstance.class)
                .hasSize(1)
                .value(instances -> {
                    ServiceInstance instance = instances.get(0);
                    assert instance.getServiceId().equals(serviceId);
                    assert instance.getInstanceId().equals(instanceId);
                    assert instance.getHost().equals("192.168.1.200");
                    assert instance.getPort() == 8080;
                });

        // 5. 心跳续约
        webTestClient.put()
                .uri("/api/v1/registry/services/{serviceId}/instances/{instanceId}/heartbeat", serviceId, instanceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceInstance.class)
                .value(instance -> {
                    assert instance.getServiceId().equals(serviceId);
                    assert instance.getInstanceId().equals(instanceId);
                });

        // 6. 注销服务实例
        webTestClient.delete()
                .uri("/api/v1/registry/services/{serviceId}/instances/{instanceId}", serviceId, instanceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isNoContent();

        // 7. 验证服务实例已被移除
        webTestClient.get()
                .uri("/api/v1/registry/services/{serviceId}/instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceInstance.class)
                .hasSize(0);
    }

    @Test
    void testMultipleInstancesRegistration() {
        String serviceId = "multi-instance-service";

        // 注册多个实例
        for (int i = 1; i <= 3; i++) {
            ServiceRegistration registration = new ServiceRegistration();
            registration.setServiceId(serviceId);
            registration.setInstanceId("instance-" + String.format("%03d", i));
            registration.setHost("192.168.1." + (100 + i));
            registration.setPort(8080 + i);
            registration.setMetadata(Map.of("instance", String.valueOf(i)));

            webTestClient.post()
                    .uri("/api/v1/registry/services/{serviceId}/instances", serviceId)
                    .header(API_KEY_HEADER, VALID_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(registration)
                    .exchange()
                    .expectStatus().isCreated();
        }

        // 验证所有实例都已注册
        webTestClient.get()
                .uri("/api/v1/registry/services/{serviceId}/instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceInstance.class)
                .hasSize(3)
                .value(instances -> {
                    assert instances.stream().allMatch(instance -> instance.getServiceId().equals(serviceId));
                    assert instances.stream().anyMatch(instance -> instance.getInstanceId().equals("instance-001"));
                    assert instances.stream().anyMatch(instance -> instance.getInstanceId().equals("instance-002"));
                    assert instances.stream().anyMatch(instance -> instance.getInstanceId().equals("instance-003"));
                });

        // 清理：注销所有实例
        for (int i = 1; i <= 3; i++) {
            String instanceId = "instance-" + String.format("%03d", i);
            webTestClient.delete()
                    .uri("/api/v1/registry/services/{serviceId}/instances/{instanceId}", serviceId, instanceId)
                    .header(API_KEY_HEADER, VALID_API_KEY)
                    .exchange()
                    .expectStatus().isNoContent();
        }
    }

    @Test
    void testGetServicesListWithMultipleServices() {
        String serviceId1 = "test-service-1";
        String serviceId2 = "test-service-2";
        String serviceId3 = "test-service-3";

        // 注册三个不同的服务
        for (String serviceId : new String[]{serviceId1, serviceId2, serviceId3}) {
            ServiceRegistration registration = new ServiceRegistration();
            registration.setServiceId(serviceId);
            registration.setInstanceId(serviceId + "-instance-001");
            registration.setHost("192.168.1.100");
            registration.setPort(8080);
            registration.setMetadata(Map.of("version", "1.0.0"));

            webTestClient.post()
                    .uri("/api/v1/registry/services/{serviceId}/instances", serviceId)
                    .header(API_KEY_HEADER, VALID_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(registration)
                    .exchange()
                    .expectStatus().isCreated();
        }

        // 验证服务列表包含所有注册的服务
        webTestClient.get()
                .uri("/api/v1/registry/services")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[*]").value(org.hamcrest.Matchers.hasItems(serviceId1, serviceId2, serviceId3));

        // 清理：注销所有服务实例
        for (String serviceId : new String[]{serviceId1, serviceId2, serviceId3}) {
            webTestClient.delete()
                    .uri("/api/v1/registry/services/{serviceId}/instances/{instanceId}", 
                         serviceId, serviceId + "-instance-001")
                    .header(API_KEY_HEADER, VALID_API_KEY)
                    .exchange()
                    .expectStatus().isNoContent();
        }
    }

    @Test
    void testRegistrationWithInvalidData() {
        // 测试无效的端口号
        ServiceRegistration invalidRegistration = new ServiceRegistration();
        invalidRegistration.setServiceId("invalid-service");
        invalidRegistration.setInstanceId("invalid-instance");
        invalidRegistration.setHost("192.168.1.100");
        invalidRegistration.setPort(-1); // 无效端口

        webTestClient.post()
                .uri("/api/v1/registry/services/invalid-service/instances")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRegistration)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_001");
    }

    @Test
    void testHeartbeatForNonExistentInstance() {
        // 对不存在的实例进行心跳续约
        webTestClient.put()
                .uri("/api/v1/registry/services/non-existent-service/instances/non-existent-instance/heartbeat")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testDeregisterNonExistentInstance() {
        // 注销不存在的实例（应该成功，因为结果是幂等的）
        webTestClient.delete()
                .uri("/api/v1/registry/services/non-existent-service/instances/non-existent-instance")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void testDuplicateRegistration() {
        String serviceId = "duplicate-test-service";
        String instanceId = "duplicate-test-instance";

        ServiceRegistration registration = new ServiceRegistration();
        registration.setServiceId(serviceId);
        registration.setInstanceId(instanceId);
        registration.setHost("192.168.1.100");
        registration.setPort(8080);
        registration.setMetadata(Map.of("version", "1.0.0"));

        // 第一次注册
        webTestClient.post()
                .uri("/api/v1/registry/services/{serviceId}/instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registration)
                .exchange()
                .expectStatus().isCreated();

        // 第二次注册相同实例（应该更新现有实例）
        registration.setMetadata(Map.of("version", "1.1.0")); // 更新版本
        webTestClient.post()
                .uri("/api/v1/registry/services/{serviceId}/instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registration)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceInstance.class)
                .value(instance -> {
                    assert instance.getMetadata().get("version").equals("1.1.0");
                });

        // 验证只有一个实例
        webTestClient.get()
                .uri("/api/v1/registry/services/{serviceId}/instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceInstance.class)
                .hasSize(1);

        // 清理
        webTestClient.delete()
                .uri("/api/v1/registry/services/{serviceId}/instances/{instanceId}", serviceId, instanceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isNoContent();
    }
}