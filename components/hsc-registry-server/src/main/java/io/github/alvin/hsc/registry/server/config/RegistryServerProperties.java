package io.github.alvin.hsc.registry.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Registry Server Configuration Properties
 * 注册中心服务器配置属性
 * 
 * 支持环境变量覆盖：
 * - HSC_REGISTRY_SERVER_PORT: 服务端口
 * - HSC_REGISTRY_SERVER_CLUSTER_ENABLED: 是否启用集群
 * - HSC_REGISTRY_SERVER_CLUSTER_NODES: 集群节点列表（逗号分隔）
 * - HSC_REGISTRY_SERVER_SECURITY_ENABLED: 是否启用安全认证
 * - HSC_REGISTRY_SERVER_SECURITY_API_KEY: API密钥
 * 
 * @author Alvin
 */
@ConfigurationProperties(prefix = "hsc.registry.server")
@Validated
public class RegistryServerProperties {

    /**
     * 服务器端口，支持环境变量 HSC_REGISTRY_SERVER_PORT
     */
    @Min(value = 1024, message = "端口号必须大于等于1024")
    @Max(value = 65535, message = "端口号必须小于等于65535")
    private int port = 8761;

    /**
     * 上下文路径
     */
    @NotBlank(message = "上下文路径不能为空")
    @Pattern(regexp = "^/.*", message = "上下文路径必须以/开头")
    private String contextPath = "/";

    /**
     * 集群配置
     */
    @Valid
    @NotNull(message = "集群配置不能为空")
    private ClusterConfig cluster = new ClusterConfig();

    /**
     * 健康检查配置
     */
    @Valid
    @NotNull(message = "健康检查配置不能为空")
    private HealthCheckConfig healthCheck = new HealthCheckConfig();

    /**
     * 存储配置
     */
    @Valid
    @NotNull(message = "存储配置不能为空")
    private StorageConfig storage = new StorageConfig();

    /**
     * 安全配置
     */
    @Valid
    @NotNull(message = "安全配置不能为空")
    private SecurityConfig security = new SecurityConfig();

    /**
     * 监控配置
     */
    @Valid
    @NotNull(message = "监控配置不能为空")
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
     * 支持环境变量：
     * - HSC_REGISTRY_SERVER_CLUSTER_ENABLED: 是否启用集群
     * - HSC_REGISTRY_SERVER_CLUSTER_NODES: 集群节点列表（逗号分隔）
     * - HSC_REGISTRY_SERVER_CLUSTER_SYNC_INTERVAL: 同步间隔（秒）
     */
    public static class ClusterConfig {
        /**
         * 是否启用集群模式
         */
        private boolean enabled = false;

        /**
         * 集群节点列表，格式：host:port
         */
        private List<String> nodes = new ArrayList<>();

        /**
         * 集群同步间隔
         */
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
     * 支持环境变量：
     * - HSC_REGISTRY_SERVER_HEALTH_CHECK_ENABLED: 是否启用健康检查
     * - HSC_REGISTRY_SERVER_HEALTH_CHECK_DEFAULT_INTERVAL: 默认检查间隔（秒）
     * - HSC_REGISTRY_SERVER_HEALTH_CHECK_DEFAULT_TIMEOUT: 默认超时时间（秒）
     * - HSC_REGISTRY_SERVER_HEALTH_CHECK_MAX_RETRY: 最大重试次数
     */
    public static class HealthCheckConfig {
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
         * 最大重试次数
         */
        @Min(value = 0, message = "重试次数不能为负数")
        @Max(value = 10, message = "重试次数不能超过10次")
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
     * 支持环境变量：
     * - HSC_REGISTRY_SERVER_STORAGE_TYPE: 存储类型
     * - HSC_REGISTRY_SERVER_STORAGE_EVICTION_INTERVAL: 清理间隔（秒）
     */
    public static class StorageConfig {
        /**
         * 存储类型：memory, redis, database
         */
        @Pattern(regexp = "^(memory|redis|database)$", message = "存储类型必须是memory、redis或database")
        private String type = "memory";

        /**
         * 过期数据清理间隔
         */
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
     * 支持环境变量：
     * - HSC_REGISTRY_SERVER_SECURITY_ENABLED: 是否启用安全认证
     * - HSC_REGISTRY_SERVER_SECURITY_API_KEY: API密钥
     * - HSC_REGISTRY_SERVER_SECURITY_USERNAME: 用户名（已废弃，使用API密钥）
     * - HSC_REGISTRY_SERVER_SECURITY_PASSWORD: 密码（已废弃，使用API密钥）
     */
    public static class SecurityConfig {
        /**
         * 是否启用安全认证
         */
        private boolean enabled = false;

        /**
         * API密钥，用于API访问认证
         */
        @Size(min = 16, message = "API密钥长度不能少于16位")
        private String apiKey = "hsc-registry-default-key-2024";

        /**
         * API密钥请求头名称
         */
        private String headerName = "X-Registry-API-Key";

        /**
         * API密钥查询参数名称
         */
        private String queryParamName = "api_key";

        /**
         * 公开路径列表，不需要认证
         */
        private List<String> publicPaths = List.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            "/management/info"
        );

        /**
         * 认证错误代码
         */
        private String authErrorCode = "AUTH_001";

        /**
         * 认证错误消息
         */
        private String authErrorMessage = "API key authentication required";

        /**
         * 用户名（已废弃，保留向后兼容）
         */
        @Deprecated
        private String username = "admin";

        /**
         * 密码（已废弃，保留向后兼容）
         */
        @Deprecated
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

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getQueryParamName() {
            return queryParamName;
        }

        public void setQueryParamName(String queryParamName) {
            this.queryParamName = queryParamName;
        }

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths;
        }

        public String getAuthErrorCode() {
            return authErrorCode;
        }

        public void setAuthErrorCode(String authErrorCode) {
            this.authErrorCode = authErrorCode;
        }

        public String getAuthErrorMessage() {
            return authErrorMessage;
        }

        public void setAuthErrorMessage(String authErrorMessage) {
            this.authErrorMessage = authErrorMessage;
        }
    }

    /**
     * 监控配置
     * 支持环境变量：
     * - HSC_REGISTRY_SERVER_MONITORING_METRICS_ENABLED: 是否启用指标收集
     * - HSC_REGISTRY_SERVER_MONITORING_HEALTH_ENDPOINT: 健康检查端点
     */
    public static class MonitoringConfig {
        /**
         * 是否启用指标收集
         */
        private boolean metricsEnabled = true;

        /**
         * 健康检查端点路径
         */
        private String healthEndpoint = "/actuator/health";

        /**
         * 是否启用详细的健康检查信息
         */
        private boolean detailedHealthEnabled = true;

        /**
         * 指标收集间隔
         */
        private Duration metricsInterval = Duration.ofSeconds(30);

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

        public boolean isDetailedHealthEnabled() {
            return detailedHealthEnabled;
        }

        public void setDetailedHealthEnabled(boolean detailedHealthEnabled) {
            this.detailedHealthEnabled = detailedHealthEnabled;
        }

        public Duration getMetricsInterval() {
            return metricsInterval;
        }

        public void setMetricsInterval(Duration metricsInterval) {
            this.metricsInterval = metricsInterval;
        }
    }

    /**
     * 验证配置的有效性
     */
    public void validate() {
        if (cluster.isEnabled() && (cluster.getNodes() == null || cluster.getNodes().isEmpty())) {
            throw new IllegalArgumentException("集群模式启用时必须配置集群节点");
        }
        
        if (security.isEnabled() && (security.getApiKey() == null || security.getApiKey().trim().isEmpty())) {
            throw new IllegalArgumentException("安全认证启用时必须配置API密钥");
        }
    }

    @Override
    public String toString() {
        return String.format("RegistryServerProperties{port=%d, contextPath='%s', cluster=%s, security.enabled=%s}", 
                port, contextPath, cluster.isEnabled(), security.isEnabled());
    }
}