package io.github.alvin.hsc.registry.server.model;

/**
 * Node Status Enum
 * 集群节点状态枚举
 * 
 * @author Alvin
 */
public enum NodeStatus {
    
    /**
     * 节点正常运行
     */
    UP,
    
    /**
     * 节点不可用
     */
    DOWN,
    
    /**
     * 节点启动中
     */
    STARTING,
    
    /**
     * 节点未知状态
     */
    UNKNOWN
}