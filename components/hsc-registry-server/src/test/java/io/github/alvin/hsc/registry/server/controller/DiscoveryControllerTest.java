package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.controller.DiscoveryController.DiscoveryResponse;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.model.ServiceCatalog;
import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.service.DiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Discovery Controller Unit Test
 * 服务发现控制器单元测试
 * 
 * 测试控制器逻辑，使用Mock对象隔离依赖
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryControllerTest {

    @Mock
    private DiscoveryService discoveryService;

    private DiscoveryController discoveryController;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        discoveryController = new DiscoveryController(discoveryService);
        webTestClient = WebTestClient.bindToController(discoveryController).build();
    }

    @Test
    void testDiscoverServiceInstances() {
        // 准备测试数据
        String serviceId = "test-service";
        List<ServiceInstance> instances = createTestInstances(serviceId);

        // Mock服务行为
        when(discoveryService.discover(serviceId)).thenReturn(Flux.fromIterable(instances));

        // 执行测试
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances", serviceId)
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
        String serviceId = "test-service";
        List<ServiceInstance> healthyInstances = createTestInstances(serviceId).stream()
                .filter(instance -> instance.getStatus() == InstanceStatus.UP)
                .toList();

        // Mock服务行为
        when(discoveryService.discoverHealthy(serviceId)).thenReturn(Flux.fromIterable(healthyInstances));

        // 执行测试
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?healthyOnly=true", serviceId)
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
    void testDiscoverWithStatusFilter() {
        // 准备测试数据
        String serviceId = "test-service";
        List<ServiceInstance> instances = createTestInstances(serviceId);

        // Mock服务行为
        when(discoveryService.discover(serviceId)).thenReturn(Flux.fromIterable(instances));

        // 测试过滤UP状态的实例
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?status=UP", serviceId)
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
    void testDiscoverWithZoneFilter() {
        // 准备测试数据
        String serviceId = "test-service";
        List<ServiceInstance> instances = createTestInstances(serviceId);

        // Mock服务行为
        when(discoveryService.discover(serviceId)).thenReturn(Flux.fromIterable(instances));

        // 测试按可用区过滤
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?zone=us-east-1a", serviceId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 2;
                    assert response.getInstances().stream()
                            .allMatch(instance -> "us-east-1a".equals(instance.getMetadata().get("zone")));
                });
    }

    @Test
    void testDiscoverWithVersionFilter() {
        // 准备测试数据
        String serviceId = "test-service";
        List<ServiceInstance> instances = createTestInstances(serviceId);

        // Mock服务行为
        when(discoveryService.discover(serviceId)).thenReturn(Flux.fromIterable(instances));

        // 测试按版本过滤
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?version=1.0.0", serviceId)
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
        String serviceId = "test-service";
        List<ServiceInstance> instances = createTestInstances(serviceId);

        // Mock服务行为
        when(discoveryService.discover(serviceId)).thenReturn(Flux.fromIterable(instances));

        // 测试多个过滤条件
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?status=UP&zone=us-east-1a&version=1.0.0", serviceId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 2; // 两个实例都符合条件
                    assert response.getInstances().stream()
                            .allMatch(instance -> instance.getStatus() == InstanceStatus.UP);
                    assert response.getInstances().stream()
                            .allMatch(instance -> "us-east-1a".equals(instance.getMetadata().get("zone")));
                    assert response.getInstances().stream()
                            .allMatch(instance -> "1.0.0".equals(instance.getMetadata().get("version")));
                });
    }

    @Test
    void testGetServiceCatalog() {
        // 准备测试数据
        Map<String, List<ServiceInstance>> services = Map.of(
                "service-a", createTestInstances("service-a"),
                "service-b", createTestInstances("service-b")
        );
        ServiceCatalog catalog = new ServiceCatalog(services);

        // Mock服务行为
        when(discoveryService.getCatalog()).thenReturn(Mono.just(catalog));

        // 执行测试
        webTestClient.get()
                .uri("/api/v1/discovery/catalog")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceCatalog.class)
                .value(result -> {
                    assert result.getTotalServices() == 2;
                    assert result.getTotalInstances() == 6;
                    assert result.getServices().containsKey("service-a");
                    assert result.getServices().containsKey("service-b");
                });
    }

    @Test
    void testGetServiceCatalogHealthyOnly() {
        // 准备测试数据
        Map<String, List<ServiceInstance>> services = Map.of(
                "service-a", createTestInstances("service-a")
        );
        ServiceCatalog catalog = new ServiceCatalog(services);

        // Mock服务行为
        when(discoveryService.getCatalog()).thenReturn(Mono.just(catalog));

        // 执行测试
        webTestClient.get()
                .uri("/api/v1/discovery/catalog?healthyOnly=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceCatalog.class)
                .value(result -> {
                    assert result.getTotalServices() == 1;
                    assert result.getTotalInstances() == 2; // 只有健康实例
                    assert result.getServices().get("service-a").stream()
                            .allMatch(instance -> instance.getStatus() == InstanceStatus.UP);
                });
    }

    @Test
    void testGetServices() {
        // 准备测试数据
        Map<String, List<ServiceInstance>> services = Map.of(
                "service-a", createTestInstances("service-a"),
                "service-b", createTestInstances("service-b"),
                "service-c", createTestInstances("service-c")
        );
        ServiceCatalog catalog = new ServiceCatalog(services);

        // Mock服务行为
        when(discoveryService.getCatalog()).thenReturn(Mono.just(catalog));

        // 执行测试
        webTestClient.get()
                .uri("/api/v1/discovery/services")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$").value(org.hamcrest.Matchers.containsInAnyOrder("service-a", "service-b", "service-c"));
    }

    @Test
    void testGetHealthyInstances() {
        // 准备测试数据
        String serviceId = "test-service";
        List<ServiceInstance> healthyInstances = createTestInstances(serviceId).stream()
                .filter(instance -> instance.getStatus() == InstanceStatus.UP)
                .toList();

        // Mock服务行为
        when(discoveryService.discoverHealthy(serviceId)).thenReturn(Flux.fromIterable(healthyInstances));

        // 执行测试
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/healthy-instances", serviceId)
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
    void testDiscoverServiceInstancesWithError() {
        // 准备测试数据
        String serviceId = "error-service";

        // Mock服务抛出异常
        when(discoveryService.discover(serviceId)).thenReturn(Flux.error(new RuntimeException("Service error")));

        // 执行测试
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances", serviceId)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void testDiscoverWithInvalidServiceId() {
        // 执行测试 - 不需要mock，因为控制器会直接验证服务ID格式
        webTestClient.get()
                .uri("/api/v1/discovery/services/invalid@service/instances")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testGetServiceCatalogWithError() {
        // Mock服务抛出异常
        when(discoveryService.getCatalog()).thenReturn(Mono.error(new RuntimeException("Catalog error")));

        // 执行测试
        webTestClient.get()
                .uri("/api/v1/discovery/catalog")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void testDiscoverEmptyResult() {
        // 准备测试数据
        String serviceId = "empty-service";

        // Mock服务返回空结果
        when(discoveryService.discover(serviceId)).thenReturn(Flux.empty());

        // 执行测试
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances", serviceId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    assert response.getServiceId().equals(serviceId);
                    assert response.getTotalInstances() == 0;
                    assert response.getInstances().isEmpty();
                });
    }

    @Test
    void testDiscoverWithInvalidStatusFilter() {
        // 准备测试数据
        String serviceId = "test-service";
        List<ServiceInstance> instances = createTestInstances(serviceId);

        // Mock服务行为
        when(discoveryService.discover(serviceId)).thenReturn(Flux.fromIterable(instances));

        // 测试无效的状态过滤器
        webTestClient.get()
                .uri("/api/v1/discovery/services/{serviceId}/instances?status=INVALID_STATUS", serviceId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveryResponse.class)
                .value(response -> {
                    // 无效状态过滤器应该返回空结果
                    assert response.getTotalInstances() == 0;
                    assert response.getInstances().isEmpty();
                });
    }

    /**
     * 创建测试服务实例列表
     * 包含3个实例：2个UP状态，1个DOWN状态
     */
    private List<ServiceInstance> createTestInstances(String serviceId) {
        // 实例1：UP状态，us-east-1a区域，版本1.0.0
        ServiceInstance instance1 = new ServiceInstance();
        instance1.setServiceId(serviceId);
        instance1.setInstanceId("instance-001");
        instance1.setHost("192.168.1.101");
        instance1.setPort(8081);
        instance1.setStatus(InstanceStatus.UP);
        instance1.setMetadata(Map.of("version", "1.0.0", "zone", "us-east-1a"));

        // 实例2：UP状态，us-east-1a区域，版本1.0.0
        ServiceInstance instance2 = new ServiceInstance();
        instance2.setServiceId(serviceId);
        instance2.setInstanceId("instance-002");
        instance2.setHost("192.168.1.102");
        instance2.setPort(8082);
        instance2.setStatus(InstanceStatus.UP);
        instance2.setMetadata(Map.of("version", "1.0.0", "zone", "us-east-1a"));

        // 实例3：DOWN状态，us-west-1a区域，版本1.1.0
        ServiceInstance instance3 = new ServiceInstance();
        instance3.setServiceId(serviceId);
        instance3.setInstanceId("instance-003");
        instance3.setHost("192.168.1.103");
        instance3.setPort(8083);
        instance3.setStatus(InstanceStatus.DOWN);
        instance3.setMetadata(Map.of("version", "1.1.0", "zone", "us-west-1a"));

        return List.of(instance1, instance2, instance3);
    }
}