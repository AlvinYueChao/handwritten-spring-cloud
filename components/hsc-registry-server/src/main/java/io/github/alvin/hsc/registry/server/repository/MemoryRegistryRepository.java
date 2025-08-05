package io.github.alvin.hsc.registry.server.repository;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Memory-based Registry Repository
 * 基于内存的注册表存储库
 * 
 * 提供线程安全的服务实例存储和管理功能，包括：
 * - 基于 ConcurrentHashMap 的内存存储
 * - 线程安全的 CRUD 操作
 * - 服务实例的过期清理机制
 * 
 * @author Alvin
 */
@Repository
public class MemoryRegistryRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryRegistryRepository.class);
    
    /**
     * 服务注册表：serviceId -> (instanceId -> ServiceInstance)
     * 使用嵌套的 ConcurrentHashMap 确保线程安全
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ServiceInstance>> registry = new ConcurrentHashMap<>();
    
    /**
     * 实例过期时间配置，默认90秒
     */
    private final Duration instanceExpiration = Duration.ofSeconds(90);
    
    /**
     * 清理任务调度器
     */
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "registry-cleanup");
        thread.setDaemon(true);
        return thread;
    });
    
    public MemoryRegistryRepository() {
        // 启动定期清理任务，每30秒执行一次
        cleanupScheduler.scheduleWithFixedDelay(this::cleanupExpiredInstances, 30, 30, TimeUnit.SECONDS);
        logger.info("Memory registry repository initialized with cleanup interval: 30 seconds");
    }
    
    /**
     * 注册服务实例
     * 
     * @param instance 服务实例
     * @return 注册的服务实例
     */
    public ServiceInstance register(ServiceInstance instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Service instance cannot be null");
        }
        
        String serviceId = instance.getServiceId();
        String instanceId = instance.getInstanceId();
        
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
    
    /**
     * 注销服务实例
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @return 被注销的服务实例，如果不存在则返回null
     */
    public ServiceInstance deregister(String serviceId, String instanceId) {
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
    
    /**
     * 更新服务实例心跳
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @return 更新后的服务实例，如果不存在则返回null
     */
    public ServiceInstance renew(String serviceId, String instanceId) {
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
    
    /**
     * 获取指定服务的所有实例
     * 
     * @param serviceId 服务ID
     * @return 服务实例列表
     */
    public List<ServiceInstance> getInstances(String serviceId) {
        if (serviceId == null) {
            return Collections.emptyList();
        }
        
        ConcurrentHashMap<String, ServiceInstance> instances = registry.get(serviceId);
        if (instances == null) {
            return Collections.emptyList();
        }
        
        return new ArrayList<>(instances.values());
    }
    
    /**
     * 获取指定服务的健康实例
     * 
     * @param serviceId 服务ID
     * @return 健康的服务实例列表
     */
    public List<ServiceInstance> getHealthyInstances(String serviceId) {
        return getInstances(serviceId).stream()
            .filter(instance -> instance.getStatus().isHealthy())
            .collect(Collectors.toList());
    }
    
    /**
     * 获取特定的服务实例
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @return 服务实例，如果不存在则返回null
     */
    public ServiceInstance getInstance(String serviceId, String instanceId) {
        if (serviceId == null || instanceId == null) {
            return null;
        }
        
        ConcurrentHashMap<String, ServiceInstance> instances = registry.get(serviceId);
        return instances != null ? instances.get(instanceId) : null;
    }
    
    /**
     * 获取所有已注册的服务名称
     * 
     * @return 服务名称集合
     */
    public Set<String> getServices() {
        return new HashSet<>(registry.keySet());
    }
    
    /**
     * 获取所有服务实例
     * 
     * @return 所有服务实例的映射
     */
    public Map<String, List<ServiceInstance>> getAllInstances() {
        Map<String, List<ServiceInstance>> result = new HashMap<>();
        
        registry.forEach((serviceId, instances) -> {
            result.put(serviceId, new ArrayList<>(instances.values()));
        });
        
        return result;
    }
    
    /**
     * 更新服务实例状态
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @param status 新状态
     * @return 更新后的服务实例，如果不存在则返回null
     */
    public ServiceInstance updateInstanceStatus(String serviceId, String instanceId, InstanceStatus status) {
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
    
    /**
     * 获取注册表统计信息
     * 
     * @return 统计信息映射
     */
    public Map<String, Object> getStatistics() {
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
        
        return stats;
    }
    
    /**
     * 清理过期的服务实例
     * 定期任务，移除长时间未发送心跳的实例
     */
    private void cleanupExpiredInstances() {
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
    }
    
    /**
     * 清理所有数据（主要用于测试）
     */
    public void clear() {
        registry.clear();
        logger.info("Registry cleared");
    }
    
    /**
     * 关闭资源
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Memory registry repository shutdown completed");
    }
}