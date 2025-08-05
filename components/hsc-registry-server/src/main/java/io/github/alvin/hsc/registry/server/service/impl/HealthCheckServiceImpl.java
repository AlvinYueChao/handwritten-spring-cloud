package io.github.alvin.hsc.registry.server.service.impl;

import io.github.alvin.hsc.registry.server.config.HealthCheckProperties;
import io.github.alvin.hsc.registry.server.model.*;
import io.github.alvin.hsc.registry.server.service.HealthCheckService;
import io.github.alvin.hsc.registry.server.service.HealthStatusManager;
import io.github.alvin.hsc.registry.server.service.RegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Health Check Service Implementation
 * 健康检查服务实现
 * 
 * @author Alvin
 */
@Service
public class HealthCheckServiceImpl implements HealthCheckService {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckServiceImpl.class);

    private final WebClient webClient;
    private final RegistryService registryService;
    private final HealthCheckProperties properties;
    private final HealthStatusManager healthStatusManager;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks;
    private final ConcurrentHashMap<String, Integer> failureCounters;
    private final Sinks.Many<HealthEvent> healthEventSink;

    @Autowired
    public HealthCheckServiceImpl(RegistryService registryService, HealthCheckProperties properties,
                                 HealthStatusManager healthStatusManager) {
        this.registryService = registryService;
        this.properties = properties;
        this.healthStatusManager = healthStatusManager;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        this.scheduler = Executors.newScheduledThreadPool(properties.getThreadPoolSize(), r -> {
            Thread thread = new Thread(r, "health-check-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.failureCounters = new ConcurrentHashMap<>();
        this.healthEventSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    @Override
    public Mono<HealthStatus> checkHealth(ServiceInstance instance) {
        if (instance.getHealthCheck() == null || !instance.getHealthCheck().isEnabled()) {
            return Mono.just(new HealthStatus(instance.getInstanceId(), InstanceStatus.UP, "Health check disabled"));
        }

        HealthCheckConfig config = instance.getHealthCheck();
        
        return switch (config.getType()) {
            case HTTP -> performHttpHealthCheck(instance, config);
            case TCP -> performTcpHealthCheck(instance, config);
            case SCRIPT -> performScriptHealthCheck(instance, config);
        };
    }

    @Override
    public void scheduleHealthCheck(ServiceInstance instance) {
        if (instance.getHealthCheck() == null || !instance.getHealthCheck().isEnabled()) {
            logger.debug("Health check disabled for instance: {}", instance.getInstanceId());
            return;
        }

        String instanceId = instance.getInstanceId();
        
        // Cancel existing scheduled task if any
        cancelHealthCheck(instanceId);
        
        HealthCheckConfig config = instance.getHealthCheck();
        long intervalSeconds = config.getInterval().getSeconds();
        
        logger.info("Scheduling health check for instance: {} with interval: {}s", 
                instanceId, intervalSeconds);
        
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                () -> performHealthCheckTask(instance),
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        
        scheduledTasks.put(instanceId, future);
        failureCounters.put(instanceId, 0);
    }

    @Override
    public void cancelHealthCheck(String instanceId) {
        ScheduledFuture<?> future = scheduledTasks.remove(instanceId);
        if (future != null) {
            future.cancel(false);
            logger.info("Cancelled health check for instance: {}", instanceId);
        }
        failureCounters.remove(instanceId);
    }

    @Override
    public Flux<HealthEvent> getHealthEvents() {
        return healthEventSink.asFlux();
    }

    /**
     * 执行健康检查任务
     */
    private void performHealthCheckTask(ServiceInstance instance) {
        checkHealth(instance)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        healthStatus -> handleHealthCheckResult(instance, healthStatus),
                        error -> handleHealthCheckError(instance, error)
                );
    }

    /**
     * 处理健康检查结果
     */
    private void handleHealthCheckResult(ServiceInstance instance, HealthStatus healthStatus) {
        String instanceId = instance.getInstanceId();
        InstanceStatus currentStatus = instance.getStatus();
        InstanceStatus newStatus = healthStatus.getStatus();
        
        if (newStatus == InstanceStatus.UP) {
            // 健康检查成功，重置失败计数器
            failureCounters.put(instanceId, 0);
            
            if (currentStatus != InstanceStatus.UP) {
                // 状态从不健康恢复到健康
                updateInstanceStatus(instance, newStatus, "Health check recovered");
            }
        } else {
            // 健康检查失败，增加失败计数器
            int failureCount = failureCounters.compute(instanceId, (k, v) -> (v == null ? 0 : v) + 1);
            HealthCheckConfig config = instance.getHealthCheck();
            
            if (failureCount >= config.getRetryCount()) {
                // 连续失败次数达到阈值，标记为不健康
                updateInstanceStatus(instance, InstanceStatus.DOWN, 
                        String.format("Health check failed %d times", failureCount));
            } else {
                logger.warn("Health check failed for instance: {} (attempt {}/{})", 
                        instanceId, failureCount, config.getRetryCount());
            }
        }
    }

    /**
     * 处理健康检查错误
     */
    private void handleHealthCheckError(ServiceInstance instance, Throwable error) {
        logger.error("Health check error for instance: {}", instance.getInstanceId(), error);
        
        HealthStatus errorStatus = new HealthStatus(
                instance.getInstanceId(), 
                InstanceStatus.DOWN, 
                "Health check error: " + error.getMessage()
        );
        
        handleHealthCheckResult(instance, errorStatus);
    }

    /**
     * 更新实例状态
     */
    private void updateInstanceStatus(ServiceInstance instance, InstanceStatus newStatus, String message) {
        // 使用 HealthStatusManager 来管理状态更新
        healthStatusManager.updateInstanceStatus(instance, newStatus, message)
                .subscribe(
                        unused -> logger.debug("Instance status updated successfully: {}", instance.getInstanceId()),
                        error -> logger.error("Failed to update instance status: {}", instance.getInstanceId(), error)
                );
    }

    /**
     * 执行 HTTP 健康检查
     */
    private Mono<HealthStatus> performHttpHealthCheck(ServiceInstance instance, HealthCheckConfig config) {
        String url = instance.getUri() + config.getPath();
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .toBodilessEntity()
                .timeout(config.getTimeout())
                .map(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        return new HealthStatus(instance.getInstanceId(), InstanceStatus.UP, "HTTP check successful");
                    } else {
                        return new HealthStatus(instance.getInstanceId(), InstanceStatus.DOWN, 
                                "HTTP check failed with status: " + response.getStatusCode());
                    }
                })
                .onErrorReturn(new HealthStatus(instance.getInstanceId(), InstanceStatus.DOWN, 
                        "HTTP check failed"));
    }

    /**
     * 执行 TCP 健康检查
     */
    private Mono<HealthStatus> performTcpHealthCheck(ServiceInstance instance, HealthCheckConfig config) {
        return Mono.fromCallable(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(instance.getHost(), instance.getPort()), 
                        (int) config.getTimeout().toMillis());
                return new HealthStatus(instance.getInstanceId(), InstanceStatus.UP, "TCP check successful");
            } catch (IOException e) {
                return new HealthStatus(instance.getInstanceId(), InstanceStatus.DOWN, 
                        "TCP check failed: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 执行脚本健康检查
     */
    private Mono<HealthStatus> performScriptHealthCheck(ServiceInstance instance, HealthCheckConfig config) {
        // 脚本健康检查的简单实现，实际项目中可以根据需要扩展
        return Mono.just(new HealthStatus(instance.getInstanceId(), InstanceStatus.UP, 
                "Script check not implemented"));
    }
}