package io.github.alvin.hsc.registry.server.service.impl;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceRegistration;
import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.model.ServiceEventType;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.repository.RegistryStorage;
import io.github.alvin.hsc.registry.server.service.RegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Registry Service Implementation
 * 服务注册实现类
 * 
 * 实现 RegistryService 接口，提供服务注册、注销、续约等核心功能。
 * 该实现具有以下特性：
 * - 响应式编程模型，基于 Reactor
 * - 完整的输入验证和错误处理
 * - 事件发布机制，支持服务变更通知
 * - 线程安全的操作
 * - 详细的日志记录
 * 
 * @author Alvin
 */
@Service
@Validated
public class RegistryServiceImpl implements RegistryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RegistryServiceImpl.class);
    
    private final RegistryStorage registryStorage;
    private final ApplicationEventPublisher eventPublisher;
    
    public RegistryServiceImpl(RegistryStorage registryStorage, 
                              ApplicationEventPublisher eventPublisher) {
        this.registryStorage = registryStorage;
        this.eventPublisher = eventPublisher;
        logger.info("Registry service initialized with storage: {}", 
                   registryStorage.getClass().getSimpleName());
    }
    
    @Override
    public Mono<ServiceInstance> register(@Valid @NotNull ServiceRegistration registration) {
        /*
        onErrorMap 是Project Reactor中Mono流的一个错误处理操作符。它的作用是：
        条件性转换异常：
            第一个参数是一个Predicate（判断条件）：ex -> !(ex instanceof IllegalArgumentException)
            第二个参数是一个Function（转换函数）：ex -> new RuntimeException(...)
        具体逻辑：
            如果发生的异常不是IllegalArgumentException类型，就将它转换为一个带有特定消息的RuntimeException
            如果是IllegalArgumentException，则不进行转换，保持原样
        为什么这样写，这种设计有几个重要原因：
            区分业务异常和系统异常：
                IllegalArgumentException是业务验证异常，通常是用户输入参数不合法导致的，这类异常信息应该直接返回给调用方
                其他异常（如存储异常、网络异常等）属于系统内部异常，需要统一包装后再返回，避免暴露内部实现细节
            统一错误信息格式：
                对于系统异常，提供统一的错误消息格式："Failed to register service instance: [原始错误信息]"
                这样可以确保对外暴露的错误信息具有一致的格式
            保留原始异常信息：
                使用new RuntimeException(message, cause)的形式，既提供了用户友好的错误消息，又保留了原始异常作为cause，便于问题排查
            符合微服务设计原则：
                在微服务架构中，服务间通信需要明确区分客户端错误(4xx)和服务端错误(5xx)
                业务验证错误（如参数不合法）通常是客户端错误
                系统内部错误是服务端错误，需要统一处理
                类似的设计也出现在服务注销(deregister)和续约(renew)等方法中，体现了代码的一致性和良好的错误处理策略。
         */
        return Mono.fromCallable(() -> {
            // 验证注册信息
            validateRegistration(registration);
            
            logger.info("Registering service instance: {} - {}", 
                       registration.getServiceId(), registration.getInstanceId());
            
            // 转换为服务实例
            ServiceInstance instance = registration.toServiceInstance();
            
            // 设置初始状态
            if (instance.getStatus() == null) {
                instance.setStatus(InstanceStatus.STARTING);
            }
            
            // 执行注册
            ServiceInstance registeredInstance = registryStorage.register(instance);
            
            // 发布注册事件
            publishEvent(ServiceEventType.REGISTER, registeredInstance);
            
            logger.info("Successfully registered service instance: {} - {} at {}:{}", 
                       registeredInstance.getServiceId(), 
                       registeredInstance.getInstanceId(),
                       registeredInstance.getHost(),
                       registeredInstance.getPort());
            
            return registeredInstance;
        })
        .onErrorMap(ex -> !(ex instanceof IllegalArgumentException), ex -> 
            new RuntimeException("Failed to register service instance: " + ex.getMessage(), ex));
    }
    
    @Override
    public Mono<Void> deregister(@NotBlank String serviceId, @NotBlank String instanceId) {
        return Mono.<Void>fromRunnable(() -> {
            logger.info("Deregistering service instance: {} - {}", serviceId, instanceId);
            
            // 验证参数
            validateServiceAndInstanceId(serviceId, instanceId);
            
            // 执行注销
            ServiceInstance deregisteredInstance = registryStorage.deregister(serviceId, instanceId);
            
            if (deregisteredInstance != null) {
                // 发布注销事件
                publishEvent(ServiceEventType.DEREGISTER, deregisteredInstance);
                
                logger.info("Successfully deregistered service instance: {} - {}", 
                           serviceId, instanceId);
            } else {
                logger.warn("Attempted to deregister non-existent service instance: {} - {}", 
                           serviceId, instanceId);
            }
        })
        .onErrorMap(ex -> !(ex instanceof IllegalArgumentException), ex -> 
            new RuntimeException("Failed to deregister service instance: " + ex.getMessage(), ex));
    }
    
    @Override
    public Mono<ServiceInstance> renew(@NotBlank String serviceId, @NotBlank String instanceId) {
        return Mono.fromCallable(() -> {
            logger.debug("Renewing service instance: {} - {}", serviceId, instanceId);
            
            // 验证参数
            validateServiceAndInstanceId(serviceId, instanceId);
            
            // 执行续约
            ServiceInstance renewedInstance = registryStorage.renew(serviceId, instanceId);
            
            if (renewedInstance != null) {
                // 发布续约事件
                publishEvent(ServiceEventType.RENEW, renewedInstance);
                
                logger.debug("Successfully renewed service instance: {} - {}", 
                            serviceId, instanceId);
            } else {
                logger.warn("Attempted to renew non-existent service instance: {} - {}", 
                           serviceId, instanceId);
                throw new IllegalArgumentException(
                    String.format("Service instance not found: %s - %s", serviceId, instanceId));
            }
            
            return renewedInstance;
        })
        .onErrorMap(ex -> !(ex instanceof IllegalArgumentException), ex -> 
            new RuntimeException("Failed to renew service instance: " + ex.getMessage(), ex));
    }
    
    @Override
    public Flux<ServiceInstance> getInstances(@NotBlank String serviceId) {
        return Mono.fromCallable(() -> {
            logger.debug("Getting instances for service: {}", serviceId);
            
            // 验证参数
            if (serviceId == null || serviceId.trim().isEmpty()) {
                throw new IllegalArgumentException("Service ID cannot be null or empty");
            }
            
            return registryStorage.getInstances(serviceId);
        })
        .flatMapMany(Flux::fromIterable)
        .onErrorMap(ex -> !(ex instanceof IllegalArgumentException), ex -> 
            new RuntimeException("Failed to get service instances: " + ex.getMessage(), ex));
    }
    
    @Override
    public Flux<String> getServices() {
        return Mono.fromCallable(() -> {
            logger.debug("Getting all registered services");
            return registryStorage.getServices();
        })
        .flatMapMany(Flux::fromIterable)
        .onErrorMap(Exception.class, ex -> 
            new RuntimeException("Failed to get services: " + ex.getMessage(), ex));
    }
    
    /**
     * 验证服务注册信息
     * 
     * @param registration 注册信息
     * @throws IllegalArgumentException 如果验证失败
     */
    private void validateRegistration(ServiceRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("Service registration cannot be null");
        }
        
        if (registration.getServiceId() == null || registration.getServiceId().trim().isEmpty()) {
            throw new IllegalArgumentException("Service ID cannot be null or empty");
        }
        
        if (registration.getInstanceId() == null || registration.getInstanceId().trim().isEmpty()) {
            throw new IllegalArgumentException("Instance ID cannot be null or empty");
        }
        
        if (registration.getHost() == null || registration.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        
        if (registration.getPort() == null || registration.getPort() <= 0 || registration.getPort() > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        
        // 验证服务ID和实例ID格式
        if (!isValidIdentifier(registration.getServiceId())) {
            throw new IllegalArgumentException("Service ID contains invalid characters");
        }
        
        if (!isValidIdentifier(registration.getInstanceId())) {
            throw new IllegalArgumentException("Instance ID contains invalid characters");
        }
        
        // 验证主机名格式
        if (!isValidHost(registration.getHost())) {
            throw new IllegalArgumentException("Invalid host format");
        }
    }
    
    /**
     * 验证服务ID和实例ID
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @throws IllegalArgumentException 如果验证失败
     */
    private void validateServiceAndInstanceId(String serviceId, String instanceId) {
        if (serviceId == null || serviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Service ID cannot be null or empty");
        }
        
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Instance ID cannot be null or empty");
        }
        
        if (!isValidIdentifier(serviceId)) {
            throw new IllegalArgumentException("Service ID contains invalid characters");
        }
        
        if (!isValidIdentifier(instanceId)) {
            throw new IllegalArgumentException("Instance ID contains invalid characters");
        }
    }
    
    /**
     * 验证标识符格式（服务ID和实例ID）
     * 允许字母、数字、连字符和下划线
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
     * 验证主机名格式
     * 支持IP地址和域名
     * 
     * @param host 主机名
     * @return 是否有效
     */
    private boolean isValidHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            return false;
        }
        
        // 简单的主机名验证，支持IP地址和域名
        // IPv4地址格式
        if (host.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")) {
            return true;
        }
        
        // 域名格式（简化版）
        if (host.matches("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?))*$")) {
            return true;
        }
        
        // localhost
        return "localhost".equals(host);
    }
    
    /**
     * 发布服务事件
     * 
     * @param eventType 事件类型
     * @param instance 服务实例
     */
    private void publishEvent(ServiceEventType eventType, ServiceInstance instance) {
        try {
            ServiceEvent event = new ServiceEvent(eventType, instance);
            eventPublisher.publishEvent(event);
            logger.debug("Published service event: {}", event);
        } catch (Exception e) {
            logger.warn("Failed to publish service event: {} for instance {} - {}", 
                       eventType, instance.getServiceId(), instance.getInstanceId(), e);
        }
    }
}