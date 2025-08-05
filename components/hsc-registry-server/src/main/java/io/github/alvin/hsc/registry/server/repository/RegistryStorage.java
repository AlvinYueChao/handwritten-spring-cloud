package io.github.alvin.hsc.registry.server.repository;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry Storage Interface
 * 注册表存储接口
 * 
 * 定义服务注册表的存储抽象层，支持多种存储实现：
 * - 内存存储（MemoryRegistryStorage）
 * - Redis存储（RedisRegistryStorage）
 * - 数据库存储（DatabaseRegistryStorage）
 * 
 * @author Alvin
 */
public interface RegistryStorage {
    
    /**
     * 注册服务实例
     * 
     * @param instance 服务实例
     * @return 注册的服务实例
     * @throws IllegalArgumentException 如果参数无效
     */
    ServiceInstance register(ServiceInstance instance);
    
    /**
     * 注销服务实例
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @return 被注销的服务实例，如果不存在则返回null
     * @throws IllegalArgumentException 如果参数无效
     */
    ServiceInstance deregister(String serviceId, String instanceId);
    
    /**
     * 更新服务实例心跳
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @return 更新后的服务实例，如果不存在则返回null
     * @throws IllegalArgumentException 如果参数无效
     */
    ServiceInstance renew(String serviceId, String instanceId);
    
    /**
     * 获取指定服务的所有实例
     * 
     * @param serviceId 服务ID
     * @return 服务实例列表，如果服务不存在则返回空列表
     */
    List<ServiceInstance> getInstances(String serviceId);
    
    /**
     * 获取指定服务的健康实例
     * 
     * @param serviceId 服务ID
     * @return 健康的服务实例列表
     */
    List<ServiceInstance> getHealthyInstances(String serviceId);
    
    /**
     * 获取特定的服务实例
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @return 服务实例，如果不存在则返回null
     */
    ServiceInstance getInstance(String serviceId, String instanceId);
    
    /**
     * 获取所有已注册的服务名称
     * 
     * @return 服务名称集合
     */
    Set<String> getServices();
    
    /**
     * 获取所有服务实例
     * 
     * @return 所有服务实例的映射，key为服务ID，value为实例列表
     */
    Map<String, List<ServiceInstance>> getAllInstances();
    
    /**
     * 更新服务实例状态
     * 
     * @param serviceId 服务ID
     * @param instanceId 实例ID
     * @param status 新状态
     * @return 更新后的服务实例，如果不存在则返回null
     * @throws IllegalArgumentException 如果参数无效
     * @throws IllegalStateException 如果状态转换无效
     */
    ServiceInstance updateInstanceStatus(String serviceId, String instanceId, InstanceStatus status);
    
    /**
     * 获取注册表统计信息
     * 
     * @return 统计信息映射，包含服务数量、实例数量等
     */
    Map<String, Object> getStatistics();
    
    /**
     * 清理过期的服务实例
     * 
     * @return 清理的实例数量
     */
    int cleanupExpiredInstances();
    
    /**
     * 清理所有数据
     * 主要用于测试或重置场景
     */
    void clear();
    
    /**
     * 检查存储是否健康
     * 
     * @return 是否健康
     */
    boolean isHealthy();
    
    /**
     * 关闭存储资源
     * 释放连接、线程池等资源
     */
    void shutdown();
}