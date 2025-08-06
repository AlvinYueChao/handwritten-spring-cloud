package io.github.alvin.hsc.registry.server.service.impl;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceCatalog;
import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.model.ServiceEventType;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.repository.RegistryStorage;
import io.github.alvin.hsc.registry.server.service.DiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Discovery Service Implementation
 * 服务发现实现类
 * 
 * 实现 DiscoveryService 接口，提供服务发现、健康实例过滤、服务目录查询等功能。
 * 该实现具有以下特性：
 * - 响应式编程模型，基于 Reactor
 * - 健康实例过滤功能
 * - 服务变更事件监听和推送
 * - 线程安全的操作
 * - 详细的日志记录
 * 
 * @author Alvin
 */
@Service
@Validated
public class DiscoveryServiceImpl implements DiscoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryServiceImpl.class);
    
    private final RegistryStorage registryStorage;
    
    // 服务变更事件流，用于实时推送服务变更
    private final Map<String, Sinks.Many<ServiceEvent>> serviceEventSinks = new ConcurrentHashMap<>();
    
    public DiscoveryServiceImpl(RegistryStorage registryStorage) {
        this.registryStorage = registryStorage;
        logger.info("Discovery service initialized with storage: {}", 
                   registryStorage.getClass().getSimpleName());
    }
    
    @Override
    public Flux<ServiceInstance> discover(String serviceId) {
        return Mono.fromCallable(() -> {
            logger.debug("Discovering all instances for service: {}", serviceId);
            
            // 验证参数
            validateServiceId(serviceId);
            
            List<ServiceInstance> instances = registryStorage.getInstances(serviceId);
            
            logger.debug("Found {} instances for service: {}", instances.size(), serviceId);
            
            return instances;
        })
        .flatMapMany(Flux::fromIterable)
        .onErrorMap(ex -> !(ex instanceof IllegalArgumentException), ex -> 
            new RuntimeException("Failed to discover service instances: " + ex.getMessage(), ex));
    }
    
    @Override
    public Flux<ServiceInstance> discoverHealthy(String serviceId) {
        return Mono.fromCallable(() -> {
            logger.debug("Discovering healthy instances for service: {}", serviceId);
            
            // 验证参数
            validateServiceId(serviceId);
            
            List<ServiceInstance> healthyInstances = registryStorage.getHealthyInstances(serviceId);
            
            logger.debug("Found {} healthy instances for service: {}", healthyInstances.size(), serviceId);
            
            return healthyInstances;
        })
        .flatMapMany(Flux::fromIterable)
        .onErrorMap(ex -> !(ex instanceof IllegalArgumentException), ex -> 
            new RuntimeException("Failed to discover healthy service instances: " + ex.getMessage(), ex));
    }
    
    @Override
    public Mono<ServiceCatalog> getCatalog() {
        return Mono.fromCallable(() -> {
            logger.debug("Getting service catalog");
            
            Map<String, List<ServiceInstance>> allInstances = registryStorage.getAllInstances();
            
            // 过滤掉空的服务列表
            Map<String, List<ServiceInstance>> filteredInstances = allInstances.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ));
            
            ServiceCatalog catalog = new ServiceCatalog(filteredInstances);
            
            logger.debug("Service catalog contains {} services with {} total instances", 
                        catalog.getTotalServices(), catalog.getTotalInstances());
            
            return catalog;
        })
        .onErrorMap(Exception.class, ex -> 
            new RuntimeException("Failed to get service catalog: " + ex.getMessage(), ex));
    }
    
    @Override
    public Flux<ServiceEvent> watchService(String serviceId) {
        return Mono.fromCallable(() -> {
            logger.debug("Starting to watch service: {}", serviceId);
            
            // 验证参数
            validateServiceId(serviceId);
            
            // 为该服务创建事件流,创建一个多播Sinks，可以同时被多个订阅者订阅，并在背压时缓存数据
            Sinks.Many<ServiceEvent> sink = serviceEventSinks.computeIfAbsent(serviceId, 
                key -> Sinks.many().multicast().onBackpressureBuffer());
            
            return sink.asFlux();
        })
        .flatMapMany(flux -> flux)
        .doOnCancel(() -> {
            logger.debug("Stopped watching service: {}", serviceId);
            // 当订阅被取消时，执行清理操作，移除没有订阅者的事件流，避免内存泄漏。
            cleanupEventSink(serviceId);
        })
        .onErrorMap(ex -> !(ex instanceof IllegalArgumentException), ex -> 
            new RuntimeException("Failed to watch service: " + ex.getMessage(), ex));
    }
    
    /**
     * 监听服务事件并推送给相关的观察者
     * 
     * @param event 服务事件
     */
    @EventListener
    public void handleServiceEvent(ServiceEvent event) {
        if (event == null || event.getServiceId() == null) {
            return;
        }
        
        String serviceId = event.getServiceId();
        logger.debug("Handling service event: {} for service: {}", event.getType(), serviceId);
        
        // 获取该服务的事件流
        Sinks.Many<ServiceEvent> sink = serviceEventSinks.get(serviceId);
        if (sink != null) {
            // 推送事件
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                logger.warn("Failed to emit service event: {} for service: {}, result: {}", 
                           event.getType(), serviceId, result);
            } else {
                logger.debug("Successfully emitted service event: {} for service: {}", 
                            event.getType(), serviceId);
            }
        }
    }
    
    /**
     * 验证服务ID
     * 
     * @param serviceId 服务ID
     * @throws IllegalArgumentException 如果验证失败
     */
    private void validateServiceId(String serviceId) {
        if (serviceId == null || serviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Service ID cannot be null or empty");
        }
        
        // 验证服务ID格式
        if (!isValidIdentifier(serviceId)) {
            throw new IllegalArgumentException("Service ID contains invalid characters");
        }
    }
    
    /**
     * 验证标识符格式
     * 允许字母、数字、连字符、下划线和点号
     * 
     * @param identifier 标识符
     * @return 是否有效
     */
    private boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        
        // 允许字母、数字、连字符、下划线和点号
        return identifier.matches("^[a-zA-Z0-9._-]+$");
    }
    
    /**
     * 清理不再使用的事件流
     * 
     * @param serviceId 服务ID
     */
    private void cleanupEventSink(String serviceId) {
        Sinks.Many<ServiceEvent> sink = serviceEventSinks.get(serviceId);
        if (sink != null && sink.currentSubscriberCount() == 0) {
            // 如果没有订阅者，则移除事件流
            serviceEventSinks.remove(serviceId);
            logger.debug("Cleaned up event sink for service: {}", serviceId);
        }
    }
}