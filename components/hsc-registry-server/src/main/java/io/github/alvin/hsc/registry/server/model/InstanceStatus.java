package io.github.alvin.hsc.registry.server.model;

/**
 * Instance Status Enum
 * 服务实例状态枚举
 * 
 * @author Alvin
 */
public enum InstanceStatus {
    
    /**
     * 实例正常运行
     */
    UP,
    
    /**
     * 实例不健康
     */
    DOWN,
    
    /**
     * 实例启动中
     */
    STARTING,
    
    /**
     * 实例未知状态
     */
    UNKNOWN,
    
    /**
     * 实例超出服务范围（暂时不可用）
     */
    OUT_OF_SERVICE
}