package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.HealthStatus;
import io.github.alvin.hsc.registry.server.model.HealthEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Health Check Service Interface
 * 健康检查服务接口
 * 
 * @author Alvin
 */
public interface HealthCheckService {

    /**
     * 检查服务实例健康状态
     * 
     * @param instance 服务实例
     * @return 健康状态
     */
    Mono<HealthStatus> checkHealth(ServiceInstance instance);

    /**
     * 调度健康检查
     * 
     * @param instance 服务实例
     */
    void scheduleHealthCheck(ServiceInstance instance);

    /**
     * 取消健康检查
     * 
     * @param instanceId 实例ID
     */
    void cancelHealthCheck(String instanceId);

    /**
     * 获取健康检查事件流
     * 
     * @return 健康检查事件流
     */
    Flux<HealthEvent> getHealthEvents();
}