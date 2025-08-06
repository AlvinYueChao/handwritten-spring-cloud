package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceRegistration;
import io.github.alvin.hsc.registry.server.service.RegistryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * Registry Controller
 * 服务注册控制器
 * 
 * 提供服务注册、注销、续约等 REST API 端点
 * 
 * @author Alvin
 */
@RestController
@RequestMapping("/api/v1/registry")
@Validated
public class RegistryController {

    private static final Logger logger = LoggerFactory.getLogger(RegistryController.class);

    private final RegistryService registryService;

    public RegistryController(RegistryService registryService) {
        this.registryService = registryService;
    }

    /**
     * 注册服务实例
     * POST /api/v1/registry/services/{serviceId}/instances
     * 
     * @param serviceId 服务ID
     * @param registration 服务注册信息
     * @return 注册后的服务实例
     */
    @PostMapping("/services/{serviceId}/instances")
    public Mono<ResponseEntity<ServiceInstance>> registerInstance(
            @PathVariable String serviceId,
            @Valid @RequestBody ServiceRegistration registration) {
        
        logger.info("Registering service instance: serviceId={}, instanceId={}", 
                serviceId, registration.getInstanceId());
        
        // 确保路径参数和请求体中的 serviceId 一致
        registration.setServiceId(serviceId);
        
        return registryService.register(registration)
                .map(instance -> {
                    logger.info("Successfully registered service instance: {}", instance);
                    return ResponseEntity.status(HttpStatus.CREATED).body(instance);
                })
                .onErrorResume(throwable -> {
                    logger.error("Failed to register service instance: serviceId={}, instanceId={}", 
                            serviceId, registration.getInstanceId(), throwable);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * 注销服务实例
     * DELETE /api/v1/registry/services/{serviceId}/instances/{instanceId}
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @return 注销结果
     */
    @DeleteMapping("/services/{serviceId}/instances/{instanceId}")
    public Mono<ResponseEntity<Void>> deregisterInstance(
            @PathVariable String serviceId,
            @PathVariable String instanceId) {
        
        logger.info("Deregistering service instance: serviceId={}, instanceId={}", serviceId, instanceId);
        
        return registryService.deregister(serviceId, instanceId)
                .then(Mono.fromCallable(() -> {
                    logger.info("Successfully deregistered service instance: serviceId={}, instanceId={}", 
                            serviceId, instanceId);
                    return ResponseEntity.noContent().<Void>build();
                }))
                .onErrorResume(throwable -> {
                    logger.error("Failed to deregister service instance: serviceId={}, instanceId={}", 
                            serviceId, instanceId, throwable);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * 续约服务实例（心跳）
     * PUT /api/v1/registry/services/{serviceId}/instances/{instanceId}/heartbeat
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @return 续约后的服务实例
     */
    @PutMapping("/services/{serviceId}/instances/{instanceId}/heartbeat")
    public Mono<ResponseEntity<ServiceInstance>> renewInstance(
            @PathVariable String serviceId,
            @PathVariable String instanceId) {
        
        logger.debug("Renewing service instance: serviceId={}, instanceId={}", serviceId, instanceId);
        
        return registryService.renew(serviceId, instanceId)
                .map(instance -> {
                    logger.debug("Successfully renewed service instance: {}", instance);
                    return ResponseEntity.ok(instance);
                })
                .onErrorResume(throwable -> {
                    logger.warn("Failed to renew service instance: serviceId={}, instanceId={}", 
                            serviceId, instanceId, throwable);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    /**
     * 获取指定服务的所有实例
     * GET /api/v1/registry/services/{serviceId}/instances
     * 
     * @param serviceId 服务ID
     * @return 服务实例列表
     */
    @GetMapping("/services/{serviceId}/instances")
    public Flux<ServiceInstance> getServiceInstances(@PathVariable String serviceId) {
        logger.debug("Getting service instances: serviceId={}", serviceId);
        
        return registryService.getInstances(serviceId)
                .doOnComplete(() -> logger.debug("Retrieved service instances for serviceId={}", serviceId))
                .onErrorResume(throwable -> {
                    logger.error("Failed to get service instances: serviceId={}", serviceId, throwable);
                    return Flux.empty();
                });
    }

    /**
     * 获取所有已注册的服务名称
     * GET /api/v1/registry/services
     * 
     * @return 服务名称列表
     */
    @GetMapping("/services")
    public Mono<ResponseEntity<List<String>>> getServices() {
        logger.debug("Getting all registered services");
        
        return registryService.getServices()
                .collectList()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(Collections.emptyList()))
                .doOnNext(response -> logger.debug("Retrieved {} registered services", response.getBody().size()))
                .onErrorResume(throwable -> {
                    logger.error("Failed to get registered services", throwable);
                    return Mono.just(ResponseEntity.ok(Collections.emptyList()));
                });
    }
}