package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.controller.DiscoveryController.DiscoveryResponse;
import io.github.alvin.hsc.registry.server.model.HealthCheckConfig;
import io.github.alvin.hsc.registry.server.model.HealthCheckType;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.model.ServiceCatalog;
import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceRegistration;
import io.github.alvin.hsc.registry.server.repository.RegistryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Discovery Controller Integration Test
 * 服务发现控制器集成测试
 * 
 * 测试服务发现相关的 REST API 端点的完整集成功能
 * 
 * @author Alvin
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DiscoveryControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RegistryStorage registryStorage;
    
    private static final String VALID_API_KEY = "test-integration-key-2024";
    private static final String API_KEY_HEADER = "X-Registry-API-Key";

    @BeforeEach
    void setUp() {
        // 清理存储，确保测试环境干净
        registryStorage.clear();
    }

    @Test
    void testDiscoverServiceInstances() {
        // 准备测试数据
        String serviceId = "discovery-test-service";
        setupTestInstances(serviceId);

        // 测试发现所有实例
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 3;
                    assert response.getInstances().size() == 3;
                });
    }

    @Test
    void testDiscoverHealthyInstancesOnly() {
        // 准备测试数据
        String serviceId = "healthy-test-service";
        setupTestInstances(serviceId);

        // 测试只发现健康实例
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?healthyOnly=true", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 2; // 只有2个健康实例
                    assert response.getInstances().stream()
                            .allMatch(instance -> instance.getStatus() == InstanceStatus.UP);
                });
    }

    @Test
    void testDiscoverWithStatusFilter() {
        // 准备测试数据
        String serviceId = "status-filter-service";
        setupTestInstances(serviceId);

        // 测试按状态过滤
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?status=UP", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 2;
                    assert response.getInstances().stream()
                            .allMatch(instance -> instance.getStatus() == InstanceStatus.UP);
                });

        // 测试过滤DOWN状态的实例
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?status=DOWN", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 1;
                    assert response.getInstances().get(0).getStatus() == InstanceStatus.DOWN;
                });
    }

    @Test
    void testDiscoverWithZoneFilter() {
        // 准备测试数据
        String serviceId = "zone-filter-service";
        setupTestInstances(serviceId);

        // 测试按可用区过滤
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?zone=us-east-1a", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 1;
                    assert response.getInstances().stream()
                            .allMatch(instance -> "us-east-1a".equals(instance.getMetadata().get("zone")));
                });
    }

    @Test
    void testDiscoverWithVersionFilter() {
        // 准备测试数据
        String serviceId = "version-filter-service";
        setupTestInstances(serviceId);

        // 测试按版本过滤
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?version=1.0.0", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 2;
                    assert response.getInstances().stream()
                            .allMatch(instance -> "1.0.0".equals(instance.getMetadata().get("version")));
                });
    }

    @Test
    void testDiscoverWithMultipleFilters() {
        // 准备测试数据
        String serviceId = "multi-filter-service";
        setupTestInstances(serviceId);

        // 测试多个过滤条件组合
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?healthyOnly=true&zone=us-east-1a&version=1.0.0", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 1;
                    ServiceInstance instance = response.getInstances().get(0);
                    assert instance.getStatus() == InstanceStatus.UP;
                    assert "us-east-1a".equals(instance.getMetadata().get("zone"));
                    assert "1.0.0".equals(instance.getMetadata().get("version"));
                });
    }

    @Test
    void testGetServiceCatalog() {
        // 准备多个服务的测试数据
        setupTestInstances("service-a");
        setupTestInstances("service-b");

        // 测试获取服务目录
        webTestClient.get()
                .uri("/api/v1/discovery/catalog")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceCatalog.class)
                .value(catalog -> {
                    assert catalog.getTotalServices() == 2;
                    assert catalog.getTotalInstances() == 6; // 每个服务3个实例
                    assert catalog.getServices().containsKey("service-a");
                    assert catalog.getServices().containsKey("service-b");
                });
    }

    @Test
    void testGetServiceCatalogHealthyOnly() {
        // 准备测试数据
        setupTestInstances("catalog-service");

        // 测试只获取健康实例的服务目录
        webTestClient.get()
                .uri("/api/v1/discovery/catalog?healthyOnly=true")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceCatalog.class)
                .value(catalog -> {
                    assert catalog.getTotalServices() == 1;
                    assert catalog.getTotalInstances() == 2; // 只有2个健康实例
                    assert catalog.getServices().get("catalog-service").stream()
                            .allMatch(instance -> instance.getStatus() == InstanceStatus.UP);
                });
    }

    @Test
    void testGetServices() {
        // 准备测试数据
        setupTestInstances("list-service-a");
        setupTestInstances("list-service-b");
        setupTestInstances("list-service-c");

        // 测试获取服务名称列表
        webTestClient.get()
                .uri("/api/v1/discovery/services")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[*]").value(org.hamcrest.Matchers.containsInAnyOrder("list-service-a", "list-service-b", "list-service-c"));
    }

    @Test
    void testGetHealthyInstances() {
        // 准备测试数据
        String serviceId = "healthy-instances-service";
        setupTestInstances(serviceId);

        // 测试获取健康实例
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/healthy-instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 2;
                    assert response.getInstances().stream()
                            .allMatch(instance -> instance.getStatus() == InstanceStatus.UP);
                });
    }

    @Test
    void testDiscoverNonExistentService() {
        // 测试发现不存在的服务
        webTestClient.get()
                .uri("/api/v1/discovery/services/non-existent-service/instances")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals("non-existent-service");
                    assert response.getTotalInstances() == 0;
                    assert response.getInstances().isEmpty();
                });
    }

    @Test
    void testDiscoverWithInvalidServiceId() {
        // 测试无效的服务ID - 空字符串会导致路径不匹配
        webTestClient.get()
                .uri("/api/v1/discovery/services//instances")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().is5xxServerError(); // 空路径段会导致500 NoResourceFoundException

        // 测试包含无效字符的服务ID
        webTestClient.get()
                .uri("/api/v1/discovery/services/invalid@service/instances")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isBadRequest(); // 控制器会验证服务ID格式，返回400
    }

    @Test
    void testDiscoverWithInvalidStatusFilter() {
        // 准备测试数据
        String serviceId = "invalid-status-service";
        setupTestInstances(serviceId);

        // 测试无效的状态过滤器
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?status=INVALID_STATUS", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    // 无效状态过滤器应该返回空结果
                    assert response.getTotalInstances() == 0;
                });
    }

    @Test
    void testEmptyServiceCatalog() {
        // 测试空的服务目录
        webTestClient.get()
                .uri("/api/v1/discovery/catalog")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceCatalog.class)
                .value(catalog -> {
                    assert catalog.getTotalServices() == 0;
                    assert catalog.getTotalInstances() == 0;
                    assert catalog.getServices().isEmpty();
                });
    }

    @Test
    void testCompleteDiscoveryFlow() {
        String serviceId = "complete-flow-service";

        // 1. 首先注册一些服务实例
        registerServiceInstance(serviceId, "instance-001", "192.168.1.101", 8081, 
                Map.of("version", "1.0.0", "zone", "us-east-1a"));
        registerServiceInstance(serviceId, "instance-002", "192.168.1.102", 8082, 
                Map.of("version", "1.1.0", "zone", "us-east-1b"));

        // 2. 验证服务发现能找到所有实例
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getTotalInstances() == 2;
                });

        // 3. 验证服务目录包含新服务
        webTestClient.get()
                .uri("/api/v1/discovery/catalog")
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceCatalog.class)
                .value(catalog -> {
                    assert catalog.getServices().containsKey(serviceId);
                    assert catalog.getServices().get(serviceId).size() == 2;
                });

        // 4. 验证健康实例发现
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/healthy-instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getTotalInstances() == 2; // 所有实例都是健康的
                });
    }

    /**
     * 设置测试实例数据
     * 创建3个实例：2个UP状态，1个DOWN状态
     */
    private void setupTestInstances(String serviceId) {
        // 实例1：UP状态，us-east-1a区域，版本1.0.0
        ServiceInstance instance1 = createTestInstance(serviceId, "instance-001", 
                "192.168.1.101", 8081, InstanceStatus.UP,
                Map.of("version", "1.0.0", "zone", "us-east-1a"));
        registryStorage.register(instance1);

        // 实例2：UP状态，us-east-1b区域，版本1.0.0
        ServiceInstance instance2 = createTestInstance(serviceId, "instance-002", 
                "192.168.1.102", 8082, InstanceStatus.UP,
                Map.of("version", "1.0.0", "zone", "us-east-1b"));
        registryStorage.register(instance2);

        // 实例3：DOWN状态，us-west-1a区域，版本1.1.0
        ServiceInstance instance3 = createTestInstance(serviceId, "instance-003", 
                "192.168.1.103", 8083, InstanceStatus.DOWN,
                Map.of("version", "1.1.0", "zone", "us-west-1a"));
        registryStorage.register(instance3);
    }

    /**
     * 创建测试服务实例
     */
    private ServiceInstance createTestInstance(String serviceId, String instanceId, 
                                             String host, int port, InstanceStatus status,
                                             Map<String, String> metadata) {
        ServiceInstance instance = new ServiceInstance();
        instance.setServiceId(serviceId);
        instance.setInstanceId(instanceId);
        instance.setHost(host);
        instance.setPort(port);
        instance.setSecure(false);
        instance.setStatus(status);
        instance.setMetadata(metadata);
        instance.setRegistrationTime(java.time.Instant.now());
        instance.setLastHeartbeat(java.time.Instant.now());

        HealthCheckConfig healthCheck = new HealthCheckConfig();
        healthCheck.setEnabled(true);
        healthCheck.setPath("/actuator/health");
        healthCheck.setInterval(Duration.ofSeconds(30));
        healthCheck.setTimeout(Duration.ofSeconds(5));
        healthCheck.setType(HealthCheckType.HTTP);
        instance.setHealthCheck(healthCheck);

        return instance;
    }

    /**
     * 通过REST API注册服务实例
     */
    private void registerServiceInstance(String serviceId, String instanceId, 
                                       String host, int port, Map<String, String> metadata) {
        ServiceRegistration registration = new ServiceRegistration();
        registration.setServiceId(serviceId);
        registration.setInstanceId(instanceId);
        registration.setHost(host);
        registration.setPort(port);
        registration.setSecure(false);
        registration.setMetadata(metadata);

        HealthCheckConfig healthCheck = new HealthCheckConfig();
        healthCheck.setEnabled(true);
        healthCheck.setPath("/actuator/health");
        healthCheck.setInterval(Duration.ofSeconds(30));
        healthCheck.setTimeout(Duration.ofSeconds(5));
        healthCheck.setType(HealthCheckType.HTTP);
        registration.setHealthCheck(healthCheck);

        webTestClient.post()
                .uri("/api/v1/registry/services/{serviceId}/instances", serviceId)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registration)
                .exchange()
                .expectStatus().isCreated();
    }
}