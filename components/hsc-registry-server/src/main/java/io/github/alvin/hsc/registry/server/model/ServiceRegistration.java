package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Duration;
import java.util.Map;

/**
 * Service Registration Model
 * 服务注册信息模型
 * 
 * @author Alvin
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceRegistration {

    @NotBlank(message = "Service ID cannot be blank")
    private String serviceId;

    @NotBlank(message = "Instance ID cannot be blank")
    private String instanceId;

    @NotBlank(message = "Host cannot be blank")
    private String host;

    @NotNull(message = "Port cannot be null")
    @Positive(message = "Port must be positive")
    private Integer port;
    
    private boolean secure = false;
    private Map<String, String> metadata;
    private HealthCheckConfig healthCheck;
    private Duration leaseDuration = Duration.ofSeconds(90);

    // Constructors
    public ServiceRegistration() {}

    public ServiceRegistration(String serviceId, String instanceId, String host, int port) {
        this.serviceId = serviceId;
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
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

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
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

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    /**
     * 转换为服务实例
     */
    public ServiceInstance toServiceInstance() {
        ServiceInstance instance = new ServiceInstance(serviceId, instanceId, host, port);
        instance.setSecure(secure);
        instance.setMetadata(metadata);
        instance.setHealthCheck(healthCheck);
        return instance;
    }

    @Override
    public String toString() {
        return String.format("ServiceRegistration{serviceId='%s', instanceId='%s', host='%s', port=%d}", 
                serviceId, instanceId, host, port);
    }
}