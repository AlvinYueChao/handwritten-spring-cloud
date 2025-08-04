package io.github.alvin.hsc.registry.server.model;

/**
 * Service Event Type Enum
 * 服务事件类型枚举
 * 
 * @author Alvin
 */
public enum ServiceEventType {
    
    /**
     * 服务注册事件
     */
    REGISTER,
    
    /**
     * 服务注销事件
     */
    DEREGISTER,
    
    /**
     * 服务续约事件
     */
    RENEW,
    
    /**
     * 服务状态变更事件
     */
    STATUS_CHANGE,
    
    /**
     * 服务健康检查事件
     */
    HEALTH_CHECK
}