package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.config.ConfigurationValidator;
import io.github.alvin.hsc.registry.server.config.EnvironmentConfigProcessor;
import io.github.alvin.hsc.registry.server.config.HealthCheckProperties;
import io.github.alvin.hsc.registry.server.config.RegistryServerProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Controller
 * 配置信息控制器，提供配置查看和管理功能
 * 
 * @author Alvin
 */
@RestController
@RequestMapping("/management/config")
public class ConfigurationController {

    private final RegistryServerProperties registryServerProperties;
    private final HealthCheckProperties healthCheckProperties;
    private final ConfigurationValidator configurationValidator;
    private final Environment environment;

    public ConfigurationController(RegistryServerProperties registryServerProperties,
                                 HealthCheckProperties healthCheckProperties,
                                 ConfigurationValidator configurationValidator,
                                 Environment environment) {
        this.registryServerProperties = registryServerProperties;
        this.healthCheckProperties = healthCheckProperties;
        this.configurationValidator = configurationValidator;
        this.environment = environment;
    }

    /**
     * 获取配置摘要信息
     */
    @GetMapping("/summary")
    public Mono<Map<String, Object>> getConfigurationSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("timestamp", Instant.now());
        summary.put("summary", configurationValidator.getConfigurationSummary());
        summary.put("activeProfiles", environment.getActiveProfiles());
        summary.put("defaultProfiles", environment.getDefaultProfiles());
        
        return Mono.just(summary);
    }

    /**
     * 获取服务器配置详情
     */
    @GetMapping("/server")
    public Mono<RegistryServerProperties> getServerConfiguration() {
        return Mono.just(registryServerProperties);
    }

    /**
     * 获取健康检查配置详情
     */
    @GetMapping("/health-check")
    public Mono<HealthCheckProperties> getHealthCheckConfiguration() {
        return Mono.just(healthCheckProperties);
    }

    /**
     * 获取支持的环境变量列表
     */
    @GetMapping("/environment-variables")
    public Mono<Map<String, Object>> getSupportedEnvironmentVariables() {
        Map<String, Object> result = new HashMap<>();
        result.put("supportedVariables", EnvironmentConfigProcessor.getSupportedEnvironmentVariables());
        result.put("mapping", EnvironmentConfigProcessor.getEnvironmentPropertyMapping());
        result.put("description", "支持的环境变量及其对应的配置属性映射");
        
        return Mono.just(result);
    }

    /**
     * 获取当前环境变量覆盖情况
     */
    @GetMapping("/environment-overrides")
    public Mono<Map<String, Object>> getEnvironmentOverrides() {
        Map<String, Object> overrides = new HashMap<>();
        
        // 检查哪些环境变量被设置了
        for (String envVar : EnvironmentConfigProcessor.getSupportedEnvironmentVariables()) {
            String value = environment.getProperty(envVar);
            if (value != null) {
                // 对于敏感信息，只显示长度
                if (envVar.contains("API_KEY") || envVar.contains("PASSWORD")) {
                    overrides.put(envVar, "***(" + value.length() + " chars)");
                } else {
                    overrides.put(envVar, value);
                }
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now());
        result.put("overrides", overrides);
        result.put("count", overrides.size());
        
        return Mono.just(result);
    }

    /**
     * 验证当前配置
     */
    @GetMapping("/validate")
    public Mono<Map<String, Object>> validateConfiguration() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now());
        
        try {
            // 重新验证配置
            configurationValidator.validateConfiguration();
            result.put("status", "valid");
            result.put("message", "配置验证通过");
            result.put("summary", configurationValidator.getConfigurationSummary());
        } catch (Exception e) {
            result.put("status", "invalid");
            result.put("message", "配置验证失败");
            result.put("error", e.getMessage());
        }
        
        return Mono.just(result);
    }

    /**
     * 获取配置属性的详细信息
     */
    @GetMapping("/properties")
    public Mono<Map<String, Object>> getConfigurationProperties() {
        Map<String, Object> properties = new HashMap<>();
        
        // 服务器配置
        Map<String, Object> serverConfig = new HashMap<>();
        serverConfig.put("port", registryServerProperties.getPort());
        serverConfig.put("contextPath", registryServerProperties.getContextPath());
        properties.put("server", serverConfig);
        
        // 集群配置
        Map<String, Object> clusterConfig = new HashMap<>();
        clusterConfig.put("enabled", registryServerProperties.getCluster().isEnabled());
        clusterConfig.put("nodes", registryServerProperties.getCluster().getNodes());
        clusterConfig.put("syncInterval", registryServerProperties.getCluster().getSyncInterval());
        properties.put("cluster", clusterConfig);
        
        // 健康检查配置
        Map<String, Object> healthConfig = new HashMap<>();
        healthConfig.put("enabled", healthCheckProperties.isEnabled());
        healthConfig.put("defaultInterval", healthCheckProperties.getDefaultInterval());
        healthConfig.put("defaultTimeout", healthCheckProperties.getDefaultTimeout());
        healthConfig.put("maxRetry", healthCheckProperties.getMaxRetry());
        healthConfig.put("threadPoolSize", healthCheckProperties.getThreadPoolSize());
        properties.put("healthCheck", healthConfig);
        
        // 存储配置
        Map<String, Object> storageConfig = new HashMap<>();
        storageConfig.put("type", registryServerProperties.getStorage().getType());
        storageConfig.put("evictionInterval", registryServerProperties.getStorage().getEvictionInterval());
        properties.put("storage", storageConfig);
        
        // 安全配置（隐藏敏感信息）
        Map<String, Object> securityConfig = new HashMap<>();
        securityConfig.put("enabled", registryServerProperties.getSecurity().isEnabled());
        securityConfig.put("headerName", registryServerProperties.getSecurity().getHeaderName());
        securityConfig.put("queryParamName", registryServerProperties.getSecurity().getQueryParamName());
        securityConfig.put("publicPaths", registryServerProperties.getSecurity().getPublicPaths());
        securityConfig.put("apiKeyLength", registryServerProperties.getSecurity().getApiKey().length());
        properties.put("security", securityConfig);
        
        // 监控配置
        Map<String, Object> monitoringConfig = new HashMap<>();
        monitoringConfig.put("metricsEnabled", registryServerProperties.getMonitoring().isMetricsEnabled());
        monitoringConfig.put("healthEndpoint", registryServerProperties.getMonitoring().getHealthEndpoint());
        monitoringConfig.put("detailedHealthEnabled", registryServerProperties.getMonitoring().isDetailedHealthEnabled());
        monitoringConfig.put("metricsInterval", registryServerProperties.getMonitoring().getMetricsInterval());
        properties.put("monitoring", monitoringConfig);
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now());
        result.put("properties", properties);
        
        return Mono.just(result);
    }
}