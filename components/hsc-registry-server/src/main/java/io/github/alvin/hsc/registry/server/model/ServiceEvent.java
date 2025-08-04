package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Service Event Model
 * 服务事件模型
 * 
 * @author Alvin
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceEvent {

    private String eventId;
    private ServiceEventType type;
    private String serviceId;
    private String instanceId;
    private ServiceInstance instance;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    // Constructors
    public ServiceEvent() {
        this.timestamp = Instant.now();
    }

    public ServiceEvent(ServiceEventType type, String serviceId, String instanceId) {
        this();
        this.type = type;
        this.serviceId = serviceId;
        this.instanceId = instanceId;
        this.eventId = generateEventId();
    }

    public ServiceEvent(ServiceEventType type, ServiceInstance instance) {
        this(type, instance.getServiceId(), instance.getInstanceId());
        this.instance = instance;
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public ServiceEventType getType() {
        return type;
    }

    public void setType(ServiceEventType type) {
        this.type = type;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public ServiceInstance getInstance() {
        return instance;
    }

    public void setInstance(ServiceInstance instance) {
        this.instance = instance;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    private String generateEventId() {
        return String.format("%s-%s-%d", serviceId, instanceId, System.nanoTime());
    }

    @Override
    public String toString() {
        return String.format("ServiceEvent{type=%s, serviceId='%s', instanceId='%s', timestamp=%s}", 
                type, serviceId, instanceId, timestamp);
    }
}