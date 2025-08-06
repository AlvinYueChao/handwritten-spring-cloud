package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceCatalog;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.service.DiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 * Discovery Controller
 * 服务发现控制器
 * 
 * 提供服务发现相关的 REST API 端点，包括：
 * - 服务实例发现
 * - 健康实例过滤
 * - 服务目录查询
 * - 支持查询参数和过滤条件
 * 
 * @author Alvin
 */
@RestController
@RequestMapping("/api/v1/discovery")
@Validated
public class DiscoveryController {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryController.class);

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * 发现指定服务的实例
     * GET /api/v1/discovery/services/{serviceId}/instances
     * 
     * 支持查询参数：
     * - healthyOnly: 是否只返回健康实例 (默认: false)
     * - status: 按状态过滤实例
     * - zone: 按可用区过滤实例
     * - version: 按版本过滤实例
     * 
     * @param serviceId 服务ID
     * @param healthyOnly 是否只返回健康实例
     * @param status 实例状态过滤
     * @param zone 可用区过滤
     * @param version 版本过滤
     * @return 服务实例列表
     */
    @GetMapping("/services/{serviceId}/instances")
    public Mono<ResponseEntity<DiscoveryResponse>> discoverServiceInstances(
            @PathVariable String serviceId,
            @RequestParam(value = "healthyOnly", defaultValue = "false") boolean healthyOnly,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "zone", required = false) String zone,
            @RequestParam(value = "version", required = false) String version) {
        
        logger.debug("Discovering service instances: serviceId={}, healthyOnly={}, status={}, zone={}, version={}", 
                serviceId, healthyOnly, status, zone, version);
        
        // 添加显式的服务 ID 验证
        if (serviceId == null || serviceId.trim().isEmpty()) {
            logger.warn("Empty service ID provided");
            return Mono.just(ResponseEntity.notFound().build());
        }
        
        if (!isValidServiceId(serviceId)) {
            logger.warn("Invalid service ID format: {}", serviceId);
            return Mono.just(ResponseEntity.badRequest().build());
        }
        
        // 根据 healthyOnly 参数选择发现方法
        Flux<ServiceInstance> instancesFlux = healthyOnly 
            ? discoveryService.discoverHealthy(serviceId)
            : discoveryService.discover(serviceId);
        
        return instancesFlux
                // 应用过滤条件
                .filter(instance -> applyFilters(instance, status, zone, version))
                .collectList()
                .map(instances -> {
                    logger.debug("Found {} instances for service: {} (after filtering)", instances.size(), serviceId);
                    
                    DiscoveryResponse response = new DiscoveryResponse(serviceId, instances);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(IllegalArgumentException.class, ex -> {
                    logger.warn("Invalid request parameters: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                })
                .onErrorResume(Exception.class, ex -> {
                    logger.error("Failed to discover service instances: serviceId={}", serviceId, ex);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * 获取服务目录
     * GET /api/v1/discovery/catalog
     * 
     * 支持查询参数：
     * - healthyOnly: 是否只包含健康实例 (默认: false)
     * 
     * @param healthyOnly 是否只包含健康实例
     * @return 服务目录
     */
    @GetMapping("/catalog")
    public Mono<ResponseEntity<ServiceCatalog>> getServiceCatalog(
            @RequestParam(value = "healthyOnly", defaultValue = "false") boolean healthyOnly) {
        
        logger.debug("Getting service catalog: healthyOnly={}", healthyOnly);
        
        return discoveryService.getCatalog()
                .map(catalog -> {
                    if (healthyOnly) {
                        // 过滤只保留健康实例
                        catalog = filterHealthyInstancesInCatalog(catalog);
                    }
                    
                    logger.debug("Retrieved service catalog with {} services and {} instances", 
                            catalog.getTotalServices(), catalog.getTotalInstances());
                    
                    return ResponseEntity.ok(catalog);
                })
                .onErrorResume(Exception.class, ex -> {
                    logger.error("Failed to get service catalog", ex);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * 获取所有服务名称列表
     * GET /api/v1/discovery/services
     * 
     * @return 服务名称列表
     */
    @GetMapping("/services")
    public Mono<ResponseEntity<List<String>>> getServices() {
        logger.debug("Getting all service names");
        
        return discoveryService.getCatalog()
                .map(catalog -> {
                    List<String> serviceNames = List.copyOf(catalog.getServices().keySet());
                    logger.debug("Retrieved {} service names", serviceNames.size());
                    return ResponseEntity.ok(serviceNames);
                })
                .onErrorResume(throwable -> {
                    logger.error("Failed to get service names", throwable);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * 获取指定服务的健康实例
     * GET /api/v1/discovery/services/{serviceId}/healthy-instances
     * 
     * @param serviceId 服务ID
     * @return 健康的服务实例列表
     */
    @GetMapping("/services/{serviceId}/healthy-instances")
    public Mono<ResponseEntity<DiscoveryResponse>> getHealthyInstances(
            @PathVariable String serviceId) {
        
        logger.debug("Getting healthy instances for service: {}", serviceId);
        
        // 添加显式的服务 ID 验证
        if (serviceId == null || serviceId.trim().isEmpty()) {
            logger.warn("Empty service ID provided");
            return Mono.just(ResponseEntity.notFound().build());
        }
        
        if (!isValidServiceId(serviceId)) {
            logger.warn("Invalid service ID format: {}", serviceId);
            return Mono.just(ResponseEntity.badRequest().build());
        }
        
        return discoveryService.discoverHealthy(serviceId)
                .collectList()
                .map(instances -> {
                    logger.debug("Found {} healthy instances for service: {}", instances.size(), serviceId);
                    
                    DiscoveryResponse response = new DiscoveryResponse(serviceId, instances);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(IllegalArgumentException.class, ex -> {
                    logger.warn("Invalid service ID: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                })
                .onErrorResume(Exception.class, ex -> {
                    logger.error("Failed to get healthy instances: serviceId={}", serviceId, ex);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * 验证服务 ID 格式是否有效
     * 
     * 服务 ID 必须满足以下条件：
     * - 不能为空或仅包含空白字符
     * - 只能包含字母、数字、点号、下划线和连字符
     * - 长度限制：1-100 字符
     * 
     * @param serviceId 服务 ID
     * @return 是否为有效的服务 ID
     */
    private boolean isValidServiceId(String serviceId) {
        if (serviceId == null || serviceId.trim().isEmpty()) {
            return false;
        }
        
        // 检查长度限制
        if (serviceId.length() < 1 || serviceId.length() > 100) {
            return false;
        }
        
        // 检查字符格式：只允许字母、数字、点号、下划线和连字符
        return serviceId.matches("^[a-zA-Z0-9._-]+$");
    }

    /**
     * 应用过滤条件
     * 
     * @param instance 服务实例
     * @param status 状态过滤
     * @param zone 可用区过滤
     * @param version 版本过滤
     * @return 是否通过过滤
     */
    private boolean applyFilters(ServiceInstance instance, String status, String zone, String version) {
        // 状态过滤 - 改进错误处理
        if (status != null && !status.trim().isEmpty()) {
            try {
                InstanceStatus filterStatus = InstanceStatus.valueOf(status.toUpperCase().trim());
                if (instance.getStatus() != filterStatus) {
                    return false;
                }
            } catch (IllegalArgumentException ex) {
                logger.warn("Invalid status filter: {}", status);
                return false; // 无效状态过滤器返回 false，导致空结果
            }
        }
        
        // 区域过滤 - 处理 null 和空字符串
        if (zone != null && !zone.trim().isEmpty()) {
            String instanceZone = instance.getMetadata() != null ? 
                instance.getMetadata().get("zone") : null;
            if (instanceZone == null || !zone.trim().equals(instanceZone.trim())) {
                return false;
            }
        }
        
        // 版本过滤 - 处理 null 和空字符串
        if (version != null && !version.trim().isEmpty()) {
            String instanceVersion = instance.getMetadata() != null ? 
                instance.getMetadata().get("version") : null;
            if (instanceVersion == null || !version.trim().equals(instanceVersion.trim())) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * 过滤服务目录中的健康实例
     * 
     * 此方法确保：
     * 1. 只返回 UP 状态的实例
     * 2. 移除没有健康实例的服务
     * 3. 准确计算目录计数
     * 
     * @param catalog 原始服务目录
     * @return 只包含健康实例的服务目录
     */
    private ServiceCatalog filterHealthyInstancesInCatalog(ServiceCatalog catalog) {
        if (catalog == null || catalog.getServices() == null) {
            logger.debug("Catalog is null or empty, returning empty catalog");
            return new ServiceCatalog(Map.of());
        }
        
        logger.debug("Filtering healthy instances from catalog with {} services and {} total instances", 
                catalog.getTotalServices(), catalog.getTotalInstances());
        
        // 过滤每个服务的实例，只保留 UP 状态的实例
        Map<String, List<ServiceInstance>> filteredServices = catalog.getServices()
                .entrySet()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                        List<ServiceInstance> healthyInstances = entry.getValue()
                                .stream()
                                .filter(instance -> {
                                    boolean isHealthy = instance.getStatus() == InstanceStatus.UP;
                                    if (!isHealthy) {
                                        logger.trace("Filtering out unhealthy instance: {} (status: {})", 
                                                instance.getInstanceId(), instance.getStatus());
                                    }
                                    return isHealthy;
                                })
                                .collect(java.util.stream.Collectors.toList());
                        
                        logger.trace("Service {} has {} healthy instances out of {} total", 
                                entry.getKey(), healthyInstances.size(), entry.getValue().size());
                        
                        return healthyInstances;
                    }
                ))
                // 移除没有健康实例的服务
                .entrySet()
                .stream()
                .filter(entry -> {
                    boolean hasHealthyInstances = !entry.getValue().isEmpty();
                    if (!hasHealthyInstances) {
                        logger.debug("Removing service {} as it has no healthy instances", entry.getKey());
                    }
                    return hasHealthyInstances;
                })
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ));
        
        // 创建新的服务目录，构造函数会自动计算准确的计数
        ServiceCatalog filteredCatalog = new ServiceCatalog(filteredServices);
        
        logger.debug("Filtered catalog contains {} services and {} healthy instances", 
                filteredCatalog.getTotalServices(), filteredCatalog.getTotalInstances());
        
        return filteredCatalog;
    }

    /**
     * 服务发现响应模型
     */
    public static class DiscoveryResponse {
        private String serviceId;
        private List<ServiceInstance> instances;
        private int totalInstances;

        public DiscoveryResponse() {}

        public DiscoveryResponse(String serviceId, List<ServiceInstance> instances) {
            this.serviceId = serviceId;
            this.instances = instances;
            this.totalInstances = instances != null ? instances.size() : 0;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public List<ServiceInstance> getInstances() {
            return instances;
        }

        public void setInstances(List<ServiceInstance> instances) {
            this.instances = instances;
            this.totalInstances = instances != null ? instances.size() : 0;
        }

        public int getTotalInstances() {
            return totalInstances;
        }

        public void setTotalInstances(int totalInstances) {
            this.totalInstances = totalInstances;
        }

        @Override
        public String toString() {
            return String.format("DiscoveryResponse{serviceId='%s', totalInstances=%d}", 
                    serviceId, totalInstances);
        }
    }
}