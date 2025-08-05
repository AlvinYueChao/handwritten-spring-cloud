package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.model.HealthEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Health Status Manager
 * 健康状态管理器
 * 
 * @author Alvin
 */
@Component
public class HealthStatusManager {

    private static final Logger logger = LoggerFactory.getLogger(HealthStatusManager.class);

    private final RegistryService registryService;
    private final Sinks.Many<HealthEvent> healthEventSink;
    private final ConcurrentHashMap<String, InstanceStatus> previousStatuses;
    
    // Use lazy initialization to avoid circular dependency
    private HealthCheckService healthCheckService;

    @Autowired
    public HealthStatusManager(RegistryService registryService) {
        this.registryService = registryService;
        this.healthEventSink = Sinks.many().multicast().onBackpressureBuffer();
        this.previousStatuses = new ConcurrentHashMap<>();
    }
    
    /**
     * Set the health check service (used to avoid circular dependency)
     */
    @Autowired
    public void setHealthCheckService(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    /**
     * 更新服务实例的健康状态
     * 
     * @param instance 服务实例
     * @param newStatus 新的健康状态
     * @param reason 状态变更原因
     * @return 更新结果
     */
    public Mono<Void> updateInstanceStatus(ServiceInstance instance, InstanceStatus newStatus, String reason) {
        String instanceId = instance.getInstanceId();
        InstanceStatus previousStatus = instance.getStatus();
        
        // 检查状态是否可以转换
        if (!previousStatus.canTransitionTo(newStatus)) {
            logger.warn("Invalid status transition for instance {}: {} -> {}", 
                    instanceId, previousStatus, newStatus);
            return Mono.empty();
        }

        // 更新实例状态
        instance.setStatus(newStatus);
        previousStatuses.put(instanceId, previousStatus);

        // 发布健康状态变更事件
        HealthEvent event = new HealthEvent(instanceId, previousStatus, newStatus, reason);
        healthEventSink.tryEmitNext(event);

        logger.info("Instance status updated: {} {} -> {}, reason: {}", 
                instanceId, previousStatus, newStatus, reason);

        // 根据新状态执行相应操作
        return handleStatusChange(instance, previousStatus, newStatus, reason);
    }

    /**
     * 处理状态变更
     */
    private Mono<Void> handleStatusChange(ServiceInstance instance, InstanceStatus previousStatus, 
                                        InstanceStatus newStatus, String reason) {
        String instanceId = instance.getInstanceId();
        
        return switch (newStatus) {
            case UP -> handleInstanceUp(instance, previousStatus, reason);
            case DOWN -> handleInstanceDown(instance, previousStatus, reason);
            case OUT_OF_SERVICE -> handleInstanceOutOfService(instance, previousStatus, reason);
            case UNKNOWN -> handleInstanceUnknown(instance, previousStatus, reason);
            case STARTING -> handleInstanceStarting(instance, previousStatus, reason);
        };
    }

    /**
     * 处理实例上线
     */
    private Mono<Void> handleInstanceUp(ServiceInstance instance, InstanceStatus previousStatus, String reason) {
        String instanceId = instance.getInstanceId();
        
        if (previousStatus != InstanceStatus.UP) {
            logger.info("Instance {} is now healthy and available for traffic", instanceId);
            
            // 重新启动健康检查
            if (healthCheckService != null && instance.getHealthCheck() != null && instance.getHealthCheck().isEnabled()) {
                healthCheckService.scheduleHealthCheck(instance);
            }
        }
        
        return Mono.empty();
    }

    /**
     * 处理实例下线
     */
    private Mono<Void> handleInstanceDown(ServiceInstance instance, InstanceStatus previousStatus, String reason) {
        String instanceId = instance.getInstanceId();
        
        logger.warn("Instance {} is down and unavailable for traffic, reason: {}", instanceId, reason);
        
        // 如果实例持续不健康，考虑自动注销
        if (shouldAutoDeregister(instance, reason)) {
            logger.info("Auto-deregistering unhealthy instance: {}", instanceId);
            return registryService.deregister(instance.getServiceId(), instanceId)
                    .doOnSuccess(unused -> logger.info("Successfully deregistered instance: {}", instanceId))
                    .doOnError(error -> logger.error("Failed to deregister instance: {}", instanceId, error))
                    .then();
        }
        
        return Mono.empty();
    }

    /**
     * 处理实例超出服务范围
     */
    private Mono<Void> handleInstanceOutOfService(ServiceInstance instance, InstanceStatus previousStatus, String reason) {
        String instanceId = instance.getInstanceId();
        
        logger.info("Instance {} is out of service, reason: {}", instanceId, reason);
        
        // 停止健康检查
        if (healthCheckService != null) {
            healthCheckService.cancelHealthCheck(instanceId);
        }
        
        return Mono.empty();
    }

    /**
     * 处理实例状态未知
     */
    private Mono<Void> handleInstanceUnknown(ServiceInstance instance, InstanceStatus previousStatus, String reason) {
        String instanceId = instance.getInstanceId();
        
        logger.warn("Instance {} status is unknown, reason: {}", instanceId, reason);
        
        // 对于未知状态的实例，增加健康检查频率
        if (healthCheckService != null && instance.getHealthCheck() != null && instance.getHealthCheck().isEnabled()) {
            // 这里可以实现更频繁的健康检查逻辑
            healthCheckService.scheduleHealthCheck(instance);
        }
        
        return Mono.empty();
    }

    /**
     * 处理实例启动中
     */
    private Mono<Void> handleInstanceStarting(ServiceInstance instance, InstanceStatus previousStatus, String reason) {
        String instanceId = instance.getInstanceId();
        
        logger.info("Instance {} is starting, reason: {}", instanceId, reason);
        
        // 为启动中的实例启动健康检查
        if (healthCheckService != null && instance.getHealthCheck() != null && instance.getHealthCheck().isEnabled()) {
            healthCheckService.scheduleHealthCheck(instance);
        }
        
        return Mono.empty();
    }

    /**
     * 判断是否应该自动注销实例
     */
    private boolean shouldAutoDeregister(ServiceInstance instance, String reason) {
        // 如果是因为长时间未发送心跳导致的下线，则自动注销
        if (reason != null && reason.contains("expired")) {
            return true;
        }
        
        // 如果健康检查连续失败超过一定次数，也可以考虑自动注销
        if (reason != null && reason.contains("Health check failed")) {
            // 这里可以根据具体的业务需求来决定
            return false; // 暂时不自动注销健康检查失败的实例
        }
        
        return false;
    }

    /**
     * 获取健康状态变更事件流
     */
    public Flux<HealthEvent> getHealthEvents() {
        return healthEventSink.asFlux();
    }

    /**
     * 获取实例的前一个状态
     */
    public InstanceStatus getPreviousStatus(String instanceId) {
        return previousStatuses.get(instanceId);
    }

    /**
     * 清理实例的状态历史
     */
    public void cleanupInstanceHistory(String instanceId) {
        previousStatuses.remove(instanceId);
        logger.debug("Cleaned up status history for instance: {}", instanceId);
    }

    /**
     * 批量更新实例状态
     */
    public Flux<Void> batchUpdateStatus(Flux<ServiceInstance> instances, InstanceStatus newStatus, String reason) {
        return instances
                .flatMap(instance -> updateInstanceStatus(instance, newStatus, reason))
                .doOnComplete(() -> logger.info("Batch status update completed"));
    }

    /**
     * 强制下线实例
     */
    public Mono<Void> forceOfflineInstance(String serviceId, String instanceId, String reason) {
        return registryService.getInstances(serviceId)
                .filter(instance -> instanceId.equals(instance.getInstanceId()))
                .next()
                .flatMap(instance -> updateInstanceStatus(instance, InstanceStatus.OUT_OF_SERVICE, reason))
                .doOnSuccess(unused -> logger.info("Forced instance {} offline, reason: {}", instanceId, reason));
    }

    /**
     * 恢复实例上线
     */
    public Mono<Void> bringInstanceOnline(String serviceId, String instanceId, String reason) {
        return registryService.getInstances(serviceId)
                .filter(instance -> instanceId.equals(instance.getInstanceId()))
                .next()
                .flatMap(instance -> updateInstanceStatus(instance, InstanceStatus.UP, reason))
                .doOnSuccess(unused -> logger.info("Brought instance {} online, reason: {}", instanceId, reason));
    }
}