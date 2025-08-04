package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Health Event Model
 * 健康检查事件模型
 * 
 * @author Alvin
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthEvent {

    private String eventId;
    private String instanceId;
    private InstanceStatus previousStatus;
    private InstanceStatus currentStatus;
    private String message;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    // Constructors
    public HealthEvent() {
        this.timestamp = Instant.now();
    }

    public HealthEvent(String instanceId, InstanceStatus previousStatus, InstanceStatus currentStatus) {
        this();
        this.instanceId = instanceId;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
        this.eventId = generateEventId();
    }

    public HealthEvent(String instanceId, InstanceStatus previousStatus, InstanceStatus currentStatus, String message) {
        this(instanceId, previousStatus, currentStatus);
        this.message = message;
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public InstanceStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(InstanceStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public InstanceStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(InstanceStatus currentStatus) {
        this.currentStatus = currentStatus;
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

    private String generateEventId() {
        return String.format("health-%s-%d", instanceId, System.nanoTime());
    }

    /**
     * 检查状态是否发生变化
     */
    public boolean isStatusChanged() {
        return previousStatus != currentStatus;
    }

    @Override
    public String toString() {
        return String.format("HealthEvent{instanceId='%s', %s -> %s, timestamp=%s}", 
                instanceId, previousStatus, currentStatus, timestamp);
    }
}