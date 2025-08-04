package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Health Status Model
 * 健康状态模型
 * 
 * @author Alvin
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthStatus {

    private String instanceId;
    private InstanceStatus status;
    private String message;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    // Constructors
    public HealthStatus() {
        this.timestamp = Instant.now();
    }

    public HealthStatus(String instanceId, InstanceStatus status) {
        this();
        this.instanceId = instanceId;
        this.status = status;
    }

    public HealthStatus(String instanceId, InstanceStatus status, String message) {
        this(instanceId, status);
        this.message = message;
    }

    // Getters and Setters
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 检查是否为健康状态
     */
    public boolean isHealthy() {
        return status == InstanceStatus.UP;
    }

    @Override
    public String toString() {
        return String.format("HealthStatus{instanceId='%s', status=%s, message='%s', timestamp=%s}", 
                instanceId, status, message, timestamp);
    }
}