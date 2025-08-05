package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service Instance Lifecycle Manager
 * 服务实例生命周期管理器
 * 
 * @author Alvin
 */
@Service
public class InstanceLifecycleManager {
    
    private static final Logger logger = LoggerFactory.getLogger(InstanceLifecycleManager.class);
    
    /**
     * 实例状态变更历史记录
     */
    private final ConcurrentMap<String, InstanceStatusHistory> statusHistories = new ConcurrentHashMap<>();
    
    /**
     * 默认心跳超时时间
     */
    private static final Duration DEFAULT_HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);
    
    /**
     * 更新服务实例状态
     * 
     * @param instance 服务实例
     * @param newStatus 新状态
     * @return 是否更新成功
     */
    public boolean updateInstanceStatus(ServiceInstance instance, InstanceStatus newStatus) {
        return updateInstanceStatus(instance, newStatus, null);
    }
    
    /**
     * 更新服务实例状态
     * 
     * @param instance 服务实例
     * @param newStatus 新状态
     * @param reason 状态变更原因
     * @return 是否更新成功
     */
    public boolean updateInstanceStatus(ServiceInstance instance, InstanceStatus newStatus, String reason) {
        if (instance == null || newStatus == null) {
            logger.warn("Cannot update status: instance or newStatus is null");
            return false;
        }
        
        InstanceStatus currentStatus = instance.getStatus();
        
        // 检查状态转换是否有效
        if (!currentStatus.canTransitionTo(newStatus)) {
            logger.warn("Invalid status transition from {} to {} for instance {}", 
                    currentStatus, newStatus, instance.getInstanceId());
            return false;
        }
        
        // 记录状态变更历史
        String instanceKey = getInstanceKey(instance);
        InstanceStatusHistory history = statusHistories.computeIfAbsent(instanceKey, 
                k -> new InstanceStatusHistory(instance.getInstanceId()));
        
        history.addStatusChange(currentStatus, newStatus, reason);
        
        // 更新实例状态
        instance.setStatus(newStatus);
        
        logger.info("Instance {} status changed from {} to {}, reason: {}", 
                instance.getInstanceId(), currentStatus, newStatus, reason);
        
        return true;
    }
    
    /**
     * 处理服务实例注册
     * 
     * @param instance 服务实例
     */
    public void handleInstanceRegistration(ServiceInstance instance) {
        if (instance == null) {
            return;
        }
        
        // 新注册的实例默认为 STARTING 状态
        if (instance.getStatus() == null) {
            instance.setStatus(InstanceStatus.STARTING);
        }
        
        updateInstanceStatus(instance, InstanceStatus.STARTING, "Instance registered");
        
        logger.info("Instance {} registered with status {}", 
                instance.getInstanceId(), instance.getStatus());
    }
    
    /**
     * 处理服务实例心跳
     * 
     * @param instance 服务实例
     */
    public void handleInstanceHeartbeat(ServiceInstance instance) {
        if (instance == null) {
            return;
        }
        
        instance.updateHeartbeat();
        
        // 如果实例当前状态不是 UP，且心跳正常，则转换为 UP
        if (instance.getStatus() != InstanceStatus.UP && 
            instance.getStatus() != InstanceStatus.OUT_OF_SERVICE) {
            updateInstanceStatus(instance, InstanceStatus.UP, "Heartbeat received");
        }
        
        logger.debug("Heartbeat received from instance {}", instance.getInstanceId());
    }
    
    /**
     * 处理服务实例注销
     * 
     * @param instance 服务实例
     */
    public void handleInstanceDeregistration(ServiceInstance instance) {
        if (instance == null) {
            return;
        }
        
        updateInstanceStatus(instance, InstanceStatus.OUT_OF_SERVICE, "Instance deregistered");
        
        // 清理状态历史记录
        String instanceKey = getInstanceKey(instance);
        statusHistories.remove(instanceKey);
        
        logger.info("Instance {} deregistered", instance.getInstanceId());
    }
    
    /**
     * 检查实例是否心跳超时
     * 
     * @param instance 服务实例
     * @return 是否超时
     */
    public boolean isHeartbeatTimeout(ServiceInstance instance) {
        return isHeartbeatTimeout(instance, DEFAULT_HEARTBEAT_TIMEOUT);
    }
    
    /**
     * 检查实例是否心跳超时
     * 
     * @param instance 服务实例
     * @param timeout 超时时间
     * @return 是否超时
     */
    public boolean isHeartbeatTimeout(ServiceInstance instance, Duration timeout) {
        if (instance == null || instance.getLastHeartbeat() == null) {
            return true;
        }
        
        Instant now = Instant.now();
        Duration timeSinceLastHeartbeat = Duration.between(instance.getLastHeartbeat(), now);
        
        return timeSinceLastHeartbeat.compareTo(timeout) > 0;
    }

  /**
   * 处理心跳超时的实例
   * <br>
   * 容错考虑:
   * <p>1. 避免因为短暂的网络问题就将实例标记为 UNKNOWN</p>
   * <p>2. 给实例更多时间来恢复连接</p>
   * <p>3. 区分临时故障和永久故障</p>
   * </br>
   *
   * @param instance 服务实例
   */
  public void handleHeartbeatTimeout(ServiceInstance instance) {
        if (instance == null) {
            return;
        }

        // 第一层：正常超时 -> DOWN
        if (instance.getStatus() == InstanceStatus.UP) {
            updateInstanceStatus(instance, InstanceStatus.DOWN, "Heartbeat timeout");
        } else if (instance.getStatus() == InstanceStatus.DOWN) {
            // 第二层：如果已经是 DOWN 状态，且长时间超时 -> UNKNOWN
            Duration timeSinceLastHeartbeat = Duration.between(instance.getLastHeartbeat(), Instant.now());
            if (timeSinceLastHeartbeat.compareTo(DEFAULT_HEARTBEAT_TIMEOUT.multipliedBy(2)) > 0) {
                updateInstanceStatus(instance, InstanceStatus.UNKNOWN, "Long time no heartbeat");
            }
        }
        
        logger.warn("Instance {} heartbeat timeout, current status: {}", 
                instance.getInstanceId(), instance.getStatus());
    }
    
    /**
     * 获取实例状态历史
     * 
     * @param instance 服务实例
     * @return 状态历史
     */
    public InstanceStatusHistory getInstanceStatusHistory(ServiceInstance instance) {
        if (instance == null) {
            return null;
        }
        
        String instanceKey = getInstanceKey(instance);
        return statusHistories.get(instanceKey);
    }
    
    /**
     * 获取实例唯一键
     * 
     * @param instance 服务实例
     * @return 实例键
     */
    private String getInstanceKey(ServiceInstance instance) {
        return instance.getServiceId() + ":" + instance.getInstanceId();
    }
    
    /**
     * 实例状态历史记录
     */
    public static class InstanceStatusHistory {
        private final String instanceId;
        private final ConcurrentMap<Instant, StatusChange> statusChanges = new ConcurrentHashMap<>();
        
        public InstanceStatusHistory(String instanceId) {
            this.instanceId = instanceId;
        }
        
        public void addStatusChange(InstanceStatus fromStatus, InstanceStatus toStatus, String reason) {
            Instant timestamp = Instant.now();
            StatusChange change = new StatusChange(fromStatus, toStatus, reason, timestamp);
            statusChanges.put(timestamp, change);
        }
        
        public String getInstanceId() {
            return instanceId;
        }
        
        public ConcurrentMap<Instant, StatusChange> getStatusChanges() {
            return statusChanges;
        }
    }
    
    /**
     * 状态变更记录
     */
    public static class StatusChange {
        private final InstanceStatus fromStatus;
        private final InstanceStatus toStatus;
        private final String reason;
        private final Instant timestamp;
        
        public StatusChange(InstanceStatus fromStatus, InstanceStatus toStatus, String reason, Instant timestamp) {
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.reason = reason;
            this.timestamp = timestamp;
        }
        
        public InstanceStatus getFromStatus() {
            return fromStatus;
        }
        
        public InstanceStatus getToStatus() {
            return toStatus;
        }
        
        public String getReason() {
            return reason;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("StatusChange{%s -> %s, reason='%s', timestamp=%s}", 
                    fromStatus, toStatus, reason, timestamp);
        }
    }
}