package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;

/**
 * Service Instance Model
 * 服务实例模型
 * 
 * @author Alvin
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceInstance {

    @NotBlank(message = "Service ID cannot be blank")
    private String serviceId;
    
    @NotBlank(message = "Instance ID cannot be blank")
    private String instanceId;
    
    @NotBlank(message = "Host cannot be blank")
    private String host;
    
    @NotNull(message = "Port cannot be null")
    @Positive(message = "Port must be positive")
    private int port;
    
    private boolean secure;
    
    @NotNull(message = "Status cannot be null")
    private InstanceStatus status;
    
    private Map<String, String> metadata;
    
    @Valid
    private HealthCheckConfig healthCheck;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant registrationTime;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastHeartbeat;

    // Constructors
    public ServiceInstance() {}

    public ServiceInstance(String serviceId, String instanceId, String host, int port) {
        this.serviceId = serviceId;
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.status = InstanceStatus.UP;
        this.registrationTime = Instant.now();
        this.lastHeartbeat = Instant.now();
    }

    // Getters and Setters
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

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public HealthCheckConfig getHealthCheck() {
        return healthCheck;
    }

    public void setHealthCheck(HealthCheckConfig healthCheck) {
        this.healthCheck = healthCheck;
    }

    public Instant getRegistrationTime() {
        return registrationTime;
    }

    public void setRegistrationTime(Instant registrationTime) {
        this.registrationTime = registrationTime;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    /**
     * 获取服务实例的完整地址
     */
    public String getUri() {
        String scheme = secure ? "https" : "http";
        return String.format("%s://%s:%d", scheme, host, port);
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = Instant.now();
    }

    @Override
    public String toString() {
        return String.format("ServiceInstance{serviceId='%s', instanceId='%s', host='%s', port=%d, status=%s}", 
                serviceId, instanceId, host, port, status);
    }
}