package io.github.alvin.hsc.registry.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Health Check Configuration Properties
 * 健康检查配置属性
 * 
 * @author Alvin
 */
@ConfigurationProperties(prefix = "hsc.registry.server.health-check")
public class HealthCheckProperties {

    /**
     * 是否启用健康检查
     */
    private boolean enabled = true;

    /**
     * 默认健康检查间隔
     */
    private Duration defaultInterval = Duration.ofSeconds(30);

    /**
     * 默认健康检查超时时间
     */
    private Duration defaultTimeout = Duration.ofSeconds(5);

    /**
     * 默认最大重试次数
     */
    private int maxRetry = 3;

    /**
     * 实例过期时间阈值
     */
    private Duration instanceExpirationThreshold = Duration.ofSeconds(90);

    /**
     * 健康检查线程池大小
     */
    private int threadPoolSize = 10;

    /**
     * 过期检查间隔
     */
    private Duration expirationCheckInterval = Duration.ofSeconds(30);

    /**
     * 任务同步间隔
     */
    private Duration taskSyncInterval = Duration.ofSeconds(60);

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDefaultInterval() {
        return defaultInterval;
    }

    public void setDefaultInterval(Duration defaultInterval) {
        this.defaultInterval = defaultInterval;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public Duration getInstanceExpirationThreshold() {
        return instanceExpirationThreshold;
    }

    public void setInstanceExpirationThreshold(Duration instanceExpirationThreshold) {
        this.instanceExpirationThreshold = instanceExpirationThreshold;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public Duration getExpirationCheckInterval() {
        return expirationCheckInterval;
    }

    public void setExpirationCheckInterval(Duration expirationCheckInterval) {
        this.expirationCheckInterval = expirationCheckInterval;
    }

    public Duration getTaskSyncInterval() {
        return taskSyncInterval;
    }

    public void setTaskSyncInterval(Duration taskSyncInterval) {
        this.taskSyncInterval = taskSyncInterval;
    }

    @Override
    public String toString() {
        return String.format("HealthCheckProperties{enabled=%s, defaultInterval=%s, defaultTimeout=%s, maxRetry=%d}", 
                enabled, defaultInterval, defaultTimeout, maxRetry);
    }
}