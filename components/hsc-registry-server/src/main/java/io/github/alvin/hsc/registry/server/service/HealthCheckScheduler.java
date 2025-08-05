package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.config.HealthCheckProperties;
import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Health Check Scheduler
 * 健康检查调度器
 * 
 * @author Alvin
 */
@Component
public class HealthCheckScheduler {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckScheduler.class);
    
    private final RegistryService registryService;
    private final HealthCheckService healthCheckService;
    private final HealthCheckProperties properties;
    private final ScheduledExecutorService scheduler;
    
    @Autowired
    public HealthCheckScheduler(RegistryService registryService, HealthCheckService healthCheckService, 
                               HealthCheckProperties properties) {
        this.registryService = registryService;
        this.healthCheckService = healthCheckService;
        this.properties = properties;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "health-check-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            logger.info("Health check is disabled");
            return;
        }
        
        logger.info("Starting health check scheduler");
        
        // 启动实例过期检查任务
        long expirationCheckSeconds = properties.getExpirationCheckInterval().getSeconds();
        scheduler.scheduleWithFixedDelay(this::checkExpiredInstances, 
                expirationCheckSeconds, expirationCheckSeconds, TimeUnit.SECONDS);
        
        // 启动健康检查任务同步
        long taskSyncSeconds = properties.getTaskSyncInterval().getSeconds();
        scheduler.scheduleWithFixedDelay(this::syncHealthCheckTasks, 
                taskSyncSeconds, taskSyncSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        logger.info("Stopping health check scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 检查过期的服务实例
     */
    private void checkExpiredInstances() {
        try {
            registryService.getServices()
                    .flatMap(registryService::getInstances)
                    .filter(this::isInstanceExpired)
                    .subscribe(this::handleExpiredInstance);
        } catch (Exception e) {
            logger.error("Error checking expired instances", e);
        }
    }

    /**
     * 同步健康检查任务
     */
    private void syncHealthCheckTasks() {
        try {
            // 确保每个启用健康检查的实例都有对应的调度任务
            registryService.getServices()
                    .flatMap(registryService::getInstances)
                    .filter(instance -> instance.getHealthCheck() != null && instance.getHealthCheck().isEnabled())
                    .subscribe(healthCheckService::scheduleHealthCheck);
        } catch (Exception e) {
            logger.error("Error syncing health check tasks", e);
        }
    }

    /**
     * 检查实例是否过期
     */
    private boolean isInstanceExpired(ServiceInstance instance) {
        if (instance.getLastHeartbeat() == null) {
            return false;
        }
        
        Instant now = Instant.now();
        Duration timeSinceLastHeartbeat = Duration.between(instance.getLastHeartbeat(), now);
        
        return timeSinceLastHeartbeat.compareTo(properties.getInstanceExpirationThreshold()) > 0;
    }

    /**
     * 处理过期的实例
     */
    private void handleExpiredInstance(ServiceInstance instance) {
        logger.warn("Instance expired: {} (last heartbeat: {})", 
                instance.getInstanceId(), instance.getLastHeartbeat());
        
        // 取消健康检查
        healthCheckService.cancelHealthCheck(instance.getInstanceId());
        
        // 注销过期实例
        registryService.deregister(instance.getServiceId(), instance.getInstanceId())
                .subscribe(
                        unused -> logger.info("Deregistered expired instance: {}", instance.getInstanceId()),
                        error -> logger.error("Failed to deregister expired instance: {}", 
                                instance.getInstanceId(), error)
                );
    }

    /**
     * 为新注册的实例启动健康检查
     */
    public void startHealthCheckForInstance(ServiceInstance instance) {
        if (instance.getHealthCheck() != null && instance.getHealthCheck().isEnabled()) {
            healthCheckService.scheduleHealthCheck(instance);
            logger.info("Started health check for instance: {}", instance.getInstanceId());
        }
    }

    /**
     * 为注销的实例停止健康检查
     */
    public void stopHealthCheckForInstance(String instanceId) {
        healthCheckService.cancelHealthCheck(instanceId);
        logger.info("Stopped health check for instance: {}", instanceId);
    }
}