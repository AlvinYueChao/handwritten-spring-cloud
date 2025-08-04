package io.github.alvin.hsc.registry.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Registry Server Configuration Properties
 * 注册中心服务器配置属性
 * 
 * @author Alvin
 */
@Component
@ConfigurationProperties(prefix = "hsc.registry.server")
public class RegistryServerProperties {

    private int port = 8761;
    private String contextPath = "/";
    private ClusterConfig cluster = new ClusterConfig();
    private HealthCheckConfig healthCheck = new HealthCheckConfig();
    private StorageConfig storage = new StorageConfig();
    private SecurityConfig security = new SecurityConfig();
    private MonitoringConfig monitoring = new MonitoringConfig();

    // Getters and Setters
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public ClusterConfig getCluster() {
        return cluster;
    }

    public void setCluster(ClusterConfig cluster) {
        this.cluster = cluster;
    }

    public HealthCheckConfig getHealthCheck() {
        return healthCheck;
    }

    public void setHealthCheck(HealthCheckConfig healthCheck) {
        this.healthCheck = healthCheck;
    }

    public StorageConfig getStorage() {
        return storage;
    }

    public void setStorage(StorageConfig storage) {
        this.storage = storage;
    }

    public SecurityConfig getSecurity() {
        return security;
    }

    public void setSecurity(SecurityConfig security) {
        this.security = security;
    }

    public MonitoringConfig getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(MonitoringConfig monitoring) {
        this.monitoring = monitoring;
    }

    /**
     * 集群配置
     */
    public static class ClusterConfig {
        private boolean enabled = false;
        private List<String> nodes;
        private Duration syncInterval = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes;
        }

        public Duration getSyncInterval() {
            return syncInterval;
        }

        public void setSyncInterval(Duration syncInterval) {
            this.syncInterval = syncInterval;
        }
    }

    /**
     * 健康检查配置
     */
    public static class HealthCheckConfig {
        private boolean enabled = true;
        private Duration defaultInterval = Duration.ofSeconds(30);
        private Duration defaultTimeout = Duration.ofSeconds(5);
        private int maxRetry = 3;

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
    }

    /**
     * 存储配置
     */
    public static class StorageConfig {
        private String type = "memory";
        private Duration evictionInterval = Duration.ofSeconds(60);

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Duration getEvictionInterval() {
            return evictionInterval;
        }

        public void setEvictionInterval(Duration evictionInterval) {
            this.evictionInterval = evictionInterval;
        }
    }

    /**
     * 安全配置
     */
    public static class SecurityConfig {
        private boolean enabled = false;
        private String username = "admin";
        private String password = "admin";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /**
     * 监控配置
     */
    public static class MonitoringConfig {
        private boolean metricsEnabled = true;
        private String healthEndpoint = "/actuator/health";

        public boolean isMetricsEnabled() {
            return metricsEnabled;
        }

        public void setMetricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
        }

        public String getHealthEndpoint() {
            return healthEndpoint;
        }

        public void setHealthEndpoint(String healthEndpoint) {
            this.healthEndpoint = healthEndpoint;
        }
    }
}