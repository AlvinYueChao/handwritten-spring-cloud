package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Duration;

/**
 * Health Check Configuration Model
 * 健康检查配置模型
 * 
 * @author Alvin
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthCheckConfig {

    private boolean enabled = true;
    private String path = "/actuator/health";
    private Duration interval = Duration.ofSeconds(30);
    private Duration timeout = Duration.ofSeconds(5);
    private int retryCount = 3;
    private HealthCheckType type = HealthCheckType.HTTP;

    // Constructors
    public HealthCheckConfig() {}

    public HealthCheckConfig(boolean enabled, String path, Duration interval, Duration timeout) {
        this.enabled = enabled;
        this.path = path;
        this.interval = interval;
        this.timeout = timeout;
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public HealthCheckType getType() {
        return type;
    }

    public void setType(HealthCheckType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("HealthCheckConfig{enabled=%s, path='%s', interval=%s, timeout=%s, type=%s}", 
                enabled, path, interval, timeout, type);
    }
}