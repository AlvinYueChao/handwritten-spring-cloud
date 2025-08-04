package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceCatalog;
import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Discovery Service Interface
 * 服务发现接口
 * 
 * @author Alvin
 */
public interface DiscoveryService {

    /**
     * 发现指定服务的所有实例
     * 
     * @param serviceId 服务ID
     * @return 服务实例列表
     */
    Flux<ServiceInstance> discover(String serviceId);

    /**
     * 发现指定服务的健康实例
     * 
     * @param serviceId 服务ID
     * @return 健康的服务实例列表
     */
    Flux<ServiceInstance> discoverHealthy(String serviceId);

    /**
     * 获取服务目录
     * 
     * @return 服务目录信息
     */
    Mono<ServiceCatalog> getCatalog();

    /**
     * 监听服务变更事件
     * 
     * @param serviceId 服务ID
     * @return 服务变更事件流
     */
    Flux<ServiceEvent> watchService(String serviceId);
}