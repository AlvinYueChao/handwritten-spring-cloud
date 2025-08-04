package io.github.alvin.hsc.registry.server.model;

import java.util.EnumSet;
import java.util.Set;

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
    OUT_OF_SERVICE;
    
    /**
     * 检查是否可以从当前状态转换到目标状态
     * 
     * @param targetStatus 目标状态
     * @return 是否可以转换
     */
    public boolean canTransitionTo(InstanceStatus targetStatus) {
        if (this == targetStatus) {
            return true;
        }
        
        return switch (this) {
            case STARTING -> EnumSet.of(UP, DOWN, UNKNOWN, OUT_OF_SERVICE).contains(targetStatus);
            case UP -> EnumSet.of(DOWN, OUT_OF_SERVICE, UNKNOWN).contains(targetStatus);
            case DOWN -> EnumSet.of(UP, STARTING, OUT_OF_SERVICE, UNKNOWN).contains(targetStatus);
            case OUT_OF_SERVICE -> EnumSet.of(UP, DOWN, STARTING, UNKNOWN).contains(targetStatus);
            case UNKNOWN -> EnumSet.allOf(InstanceStatus.class).contains(targetStatus);
        };
    }
    
    /**
     * 获取所有可以转换到的状态
     * 
     * @return 可转换的状态集合
     */
    public Set<InstanceStatus> getValidTransitions() {
        return switch (this) {
            case STARTING -> EnumSet.of(UP, DOWN, UNKNOWN, OUT_OF_SERVICE);
            case UP -> EnumSet.of(DOWN, OUT_OF_SERVICE, UNKNOWN);
            case DOWN -> EnumSet.of(UP, STARTING, OUT_OF_SERVICE, UNKNOWN);
            case OUT_OF_SERVICE -> EnumSet.of(UP, DOWN, STARTING, UNKNOWN);
            case UNKNOWN -> EnumSet.allOf(InstanceStatus.class);
        };
    }
    
    /**
     * 检查状态是否为健康状态
     * 
     * @return 是否健康
     */
    public boolean isHealthy() {
        return this == UP;
    }
    
    /**
     * 检查状态是否为可用状态（可以接收流量）
     * 
     * @return 是否可用
     */
    public boolean isAvailable() {
        return this == UP;
    }
    
    /**
     * 检查状态是否为终端状态（不会自动恢复）
     * 
     * @return 是否为终端状态
     */
    public boolean isTerminal() {
        return this == OUT_OF_SERVICE;
    }
}