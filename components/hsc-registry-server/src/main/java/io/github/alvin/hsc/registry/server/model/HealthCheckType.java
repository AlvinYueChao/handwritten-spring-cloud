package io.github.alvin.hsc.registry.server.model;

/**
 * Health Check Type Enum
 * 健康检查类型枚举
 * 
 * @author Alvin
 */
public enum HealthCheckType {
    
    /**
     * HTTP 健康检查
     */
    HTTP,
    
    /**
     * TCP 健康检查
     */
    TCP,
    
    /**
     * 脚本健康检查
     */
    SCRIPT
}