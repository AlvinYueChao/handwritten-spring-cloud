package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceRegistration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Registry Service Interface
 * 服务注册接口
 * 
 * @author Alvin
 */
public interface RegistryService {

    /**
     * 注册服务实例
     * 
     * @param registration 服务注册信息
     * @return 注册后的服务实例
     */
    Mono<ServiceInstance> register(ServiceRegistration registration);

    /**
     * 注销服务实例
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @return 注销结果
     */
    Mono<Void> deregister(String serviceId, String instanceId);

    /**
     * 续约服务实例
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @return 续约后的服务实例
     */
    Mono<ServiceInstance> renew(String serviceId, String instanceId);

    /**
     * 获取指定服务的所有实例
     * 
     * @param serviceId 服务ID
     * @return 服务实例列表
     */
    Flux<ServiceInstance> getInstances(String serviceId);

    /**
     * 获取所有已注册的服务名称
     * 
     * @return 服务名称列表
     */
    Flux<String> getServices();
}