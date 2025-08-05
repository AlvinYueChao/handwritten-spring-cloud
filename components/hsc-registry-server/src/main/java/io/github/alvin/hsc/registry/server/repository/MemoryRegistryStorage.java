package io.github.alvin.hsc.registry.server.repository;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Memory-based Registry Storage Implementation
 * 基于内存的注册表存储实现
 * 
 * 实现 RegistryStorage 接口，提供基于内存的服务注册表存储功能。
 * 该实现具有以下特性：
 * - 基于 ConcurrentHashMap 的线程安全存储
 * - 自动过期清理机制
 * - 高性能的读写操作
 * - 完整的统计信息支持
 * 
 * @author Alvin
 */
@Component
public class MemoryRegistryStorage implements RegistryStorage {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryRegistryStorage.class);
    
    /**
     * 服务注册表：serviceId -> (instanceId -> ServiceInstance)
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ServiceInstance>> registry = new ConcurrentHashMap<>();
    
    /**
     * 实例过期时间配置，默认90秒
     */
    private final Duration instanceExpiration = Duration.ofSeconds(90);
    
    /**
     * 清理任务调度器
     */
    private final ScheduledExecutorService cleanupScheduler;
    
    /**
     * 存储健康状态
     */
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    
    /**
     * 是否已关闭
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    
    public MemoryRegistryStorage() {
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "memory-registry-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        
        // 启动定期清理任务，每30秒执行一次
        cleanupScheduler.scheduleWithFixedDelay(this::performCleanup, 30, 30, TimeUnit.SECONDS);
        logger.info("Memory registry storage initialized with cleanup interval: 30 seconds");
    }
    
    @Override
    public ServiceInstance register(ServiceInstance instance) {
        checkNotShutdown();
        
        if (instance == null) {
            throw new IllegalArgumentException("Service instance cannot be null");
        }
        
        String serviceId = instance.getServiceId();
        String instanceId = instance.getInstanceId();
        
        if (serviceId == null || serviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Service ID cannot be null or empty");
        }
        
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Instance ID cannot be null or empty");
        }
        
        // 获取或创建服务的实例映射
        ConcurrentHashMap<String, ServiceInstance> instances = registry.computeIfAbsent(serviceId, 
            k -> new ConcurrentHashMap<>());
        
        // 设置注册时间和心跳时间
        if (instance.getRegistrationTime() == null) {
            instance.setRegistrationTime(Instant.now());
        }
        instance.updateHeartbeat();
        
        // 存储实例
        ServiceInstance previous = instances.put(instanceId, instance);
        
        if (previous == null) {
            logger.info("Registered new service instance: {} - {}", serviceId, instanceId);
        } else {
            logger.info("Updated existing service instance: {} - {}", serviceId, instanceId);
        }
        
        return instance;
    }
    
    @Override
    public ServiceInstance deregister(String serviceId, String instanceId) {
        checkNotShutdown();
        
        if (serviceId == null || instanceId == null) {
            throw new IllegalArgumentException("Service ID and instance ID cannot be null");
        }
        
        ConcurrentHashMap<String, ServiceInstance> instances = registry.get(serviceId);
        if (instances == null) {
            logger.warn("Attempted to deregister instance from non-existent service: {}", serviceId);
            return null;
        }
        
        ServiceInstance removed = instances.remove(instanceId);
        
        // 如果服务下没有实例了，移除整个服务
        if (instances.isEmpty()) {
            registry.remove(serviceId);
            logger.info("Removed empty service: {}", serviceId);
        }
        
        if (removed != null) {
            logger.info("Deregistered service instance: {} - {}", serviceId, instanceId);
        } else {
            logger.warn("Attempted to deregister non-existent instance: {} - {}", serviceId, instanceId);
        }
        
        return removed;
    }
    
    @Override
    public ServiceInstance renew(String serviceId, String instanceId) {
        checkNotShutdown();
        
        if (serviceId == null || instanceId == null) {
            throw new IllegalArgumentException("Service ID and instance ID cannot be null");
        }
        
        ConcurrentHashMap<String, ServiceInstance> instances = registry.get(serviceId);
        if (instances == null) {
            logger.warn("Attempted to renew instance from non-existent service: {}", serviceId);
            return null;
        }
        
        ServiceInstance instance = instances.get(instanceId);
        if (instance != null) {
            instance.updateHeartbeat();
            logger.debug("Renewed heartbeat for instance: {} - {}", serviceId, instanceId);
        } else {
            logger.warn("Attempted to renew non-existent instance: {} - {}", serviceId, instanceId);
        }
        
        return instance;
    }
    
    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        checkNotShutdown();
        
        if (serviceId == null) {
            return Collections.emptyList();
        }
        
        ConcurrentHashMap<String, ServiceInstance> instances = registry.get(serviceId);
        if (instances == null) {
            return Collections.emptyList();
        }
        
        return new ArrayList<>(instances.values());
    }
    
    @Override
    public List<ServiceInstance> getHealthyInstances(String serviceId) {
        return getInstances(serviceId).stream()
            .filter(instance -> instance.getStatus().isHealthy())
            .collect(Collectors.toList());
    }
    
    @Override
    public ServiceInstance getInstance(String serviceId, String instanceId) {
        checkNotShutdown();
        
        if (serviceId == null || instanceId == null) {
            return null;
        }
        
        ConcurrentHashMap<String, ServiceInstance> instances = registry.get(serviceId);
        return instances != null ? instances.get(instanceId) : null;
    }
    
    @Override
    public Set<String> getServices() {
        checkNotShutdown();
        return new HashSet<>(registry.keySet());
    }
    
    @Override
    public Map<String, List<ServiceInstance>> getAllInstances() {
        checkNotShutdown();
        
        Map<String, List<ServiceInstance>> result = new HashMap<>();
        
        registry.forEach((serviceId, instances) -> {
            result.put(serviceId, new ArrayList<>(instances.values()));
        });
        
        return result;
    }
    
    @Override
    public ServiceInstance updateInstanceStatus(String serviceId, String instanceId, InstanceStatus status) {
        checkNotShutdown();
        
        if (serviceId == null || instanceId == null || status == null) {
            throw new IllegalArgumentException("Service ID, instance ID and status cannot be null");
        }
        
        ServiceInstance instance = getInstance(serviceId, instanceId);
        if (instance != null) {
            // 检查状态转换是否有效
            if (instance.getStatus().canTransitionTo(status)) {
                instance.setStatus(status);
                instance.updateHeartbeat();
                logger.info("Updated instance status: {} - {} -> {}", serviceId, instanceId, status);
            } else {
                logger.warn("Invalid status transition for instance {} - {}: {} -> {}", 
                    serviceId, instanceId, instance.getStatus(), status);
                throw new IllegalStateException(
                    String.format("Cannot transition from %s to %s", instance.getStatus(), status));
            }
        } else {
            logger.warn("Attempted to update status of non-existent instance: {} - {}", serviceId, instanceId);
        }
        
        return instance;
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        checkNotShutdown();
        
        Map<String, Object> stats = new HashMap<>();
        
        int totalServices = registry.size();
        int totalInstances = registry.values().stream()
            .mapToInt(Map::size)
            .sum();
        
        long healthyInstances = registry.values().stream()
            .flatMap(instances -> instances.values().stream())
            .filter(instance -> instance.getStatus().isHealthy())
            .count();
        
        stats.put("totalServices", totalServices);
        stats.put("totalInstances", totalInstances);
        stats.put("healthyInstances", healthyInstances);
        stats.put("unhealthyInstances", totalInstances - healthyInstances);
        stats.put("storageType", "memory");
        stats.put("healthy", healthy.get());
        
        return stats;
    }
    
    @Override
    public int cleanupExpiredInstances() {
        checkNotShutdown();
        return performCleanup();
    }
    
    @Override
    public void clear() {
        checkNotShutdown();
        registry.clear();
        logger.info("Registry storage cleared");
    }
    
    @Override
    public boolean isHealthy() {
        return healthy.get() && !shutdown.get();
    }
    
    @Override
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            healthy.set(false);
            
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.info("Memory registry storage shutdown completed");
        }
    }
    
    /**
     * 执行清理过期实例的任务
     * 
     * @return 清理的实例数量
     */
    private int performCleanup() {
        if (shutdown.get()) {
            return 0;
        }
        
        try {
            Instant cutoffTime = Instant.now().minus(instanceExpiration);
            int removedCount = 0;
            
            Iterator<Map.Entry<String, ConcurrentHashMap<String, ServiceInstance>>> serviceIterator = 
                registry.entrySet().iterator();
            
            while (serviceIterator.hasNext()) {
                Map.Entry<String, ConcurrentHashMap<String, ServiceInstance>> serviceEntry = serviceIterator.next();
                String serviceId = serviceEntry.getKey();
                ConcurrentHashMap<String, ServiceInstance> instances = serviceEntry.getValue();
                
                Iterator<Map.Entry<String, ServiceInstance>> instanceIterator = instances.entrySet().iterator();
                
                while (instanceIterator.hasNext()) {
                    Map.Entry<String, ServiceInstance> instanceEntry = instanceIterator.next();
                    ServiceInstance instance = instanceEntry.getValue();
                    
                    // 检查实例是否过期
                    if (instance.getLastHeartbeat().isBefore(cutoffTime)) {
                        instanceIterator.remove();
                        removedCount++;
                        logger.info("Removed expired instance: {} - {} (last heartbeat: {})", 
                            serviceId, instance.getInstanceId(), instance.getLastHeartbeat());
                    }
                }
                
                // 如果服务下没有实例了，移除整个服务
                if (instances.isEmpty()) {
                    serviceIterator.remove();
                    logger.info("Removed empty service after cleanup: {}", serviceId);
                }
            }
            
            if (removedCount > 0) {
                logger.info("Cleanup completed: removed {} expired instances", removedCount);
            }
            
            return removedCount;
            
        } catch (Exception e) {
            logger.error("Error during cleanup task", e);
            healthy.set(false);
            return 0;
        }
    }
    
    /**
     * 检查存储是否已关闭
     */
    private void checkNotShutdown() {
        if (shutdown.get()) {
            throw new IllegalStateException("Registry storage has been shutdown");
        }
    }
}