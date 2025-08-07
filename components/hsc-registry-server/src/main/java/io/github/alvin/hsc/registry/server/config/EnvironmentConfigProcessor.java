package io.github.alvin.hsc.registry.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * Environment Configuration Processor
 * 环境变量配置处理器，支持通过环境变量覆盖配置文件中的设置
 * 
 * 支持的环境变量：
 * - HSC_REGISTRY_SERVER_PORT: 服务端口
 * - HSC_REGISTRY_SERVER_CLUSTER_ENABLED: 是否启用集群
 * - HSC_REGISTRY_SERVER_CLUSTER_NODES: 集群节点列表（逗号分隔）
 * - HSC_REGISTRY_SERVER_SECURITY_ENABLED: 是否启用安全认证
 * - HSC_REGISTRY_SERVER_SECURITY_API_KEY: API密钥
 * - HSC_REGISTRY_SERVER_HEALTH_CHECK_ENABLED: 是否启用健康检查
 * 
 * @author Alvin
 */
@Component
public class EnvironmentConfigProcessor implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentConfigProcessor.class);

    /**
     * 环境变量到配置属性的映射
     */
    private static final Map<String, String> ENV_TO_PROPERTY_MAPPING = createEnvironmentMapping();

    /**
     * 创建环境变量映射
     */
    private static Map<String, String> createEnvironmentMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("HSC_REGISTRY_SERVER_PORT", "hsc.registry.server.port");
        mapping.put("HSC_REGISTRY_SERVER_CONTEXT_PATH", "hsc.registry.server.context-path");
        mapping.put("HSC_REGISTRY_SERVER_CLUSTER_ENABLED", "hsc.registry.server.cluster.enabled");
        mapping.put("HSC_REGISTRY_SERVER_CLUSTER_NODES", "hsc.registry.server.cluster.nodes");
        mapping.put("HSC_REGISTRY_SERVER_CLUSTER_SYNC_INTERVAL", "hsc.registry.server.cluster.sync-interval");
        mapping.put("HSC_REGISTRY_SERVER_SECURITY_ENABLED", "hsc.registry.server.security.enabled");
        mapping.put("HSC_REGISTRY_SERVER_SECURITY_API_KEY", "hsc.registry.server.security.api-key");
        mapping.put("HSC_REGISTRY_SERVER_SECURITY_HEADER_NAME", "hsc.registry.server.security.header-name");
        mapping.put("HSC_REGISTRY_SERVER_HEALTH_CHECK_ENABLED", "hsc.registry.server.health-check.enabled");
        mapping.put("HSC_REGISTRY_SERVER_HEALTH_CHECK_DEFAULT_INTERVAL", "hsc.registry.server.health-check.default-interval");
        mapping.put("HSC_REGISTRY_SERVER_HEALTH_CHECK_DEFAULT_TIMEOUT", "hsc.registry.server.health-check.default-timeout");
        mapping.put("HSC_REGISTRY_SERVER_HEALTH_CHECK_MAX_RETRY", "hsc.registry.server.health-check.max-retry");
        mapping.put("HSC_REGISTRY_SERVER_STORAGE_TYPE", "hsc.registry.server.storage.type");
        mapping.put("HSC_REGISTRY_SERVER_STORAGE_EVICTION_INTERVAL", "hsc.registry.server.storage.eviction-interval");
        mapping.put("HSC_REGISTRY_SERVER_MONITORING_METRICS_ENABLED", "hsc.registry.server.monitoring.metrics-enabled");
        mapping.put("HSC_REGISTRY_SERVER_MONITORING_HEALTH_ENDPOINT", "hsc.registry.server.monitoring.health-endpoint");
        return Collections.unmodifiableMap(mapping);
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        
        logger.info("开始处理环境变量配置覆盖...");
        
        Map<String, Object> envProperties = new HashMap<>();
        int overrideCount = 0;
        
        // 处理环境变量映射
        for (Map.Entry<String, String> entry : ENV_TO_PROPERTY_MAPPING.entrySet()) {
            String envVar = entry.getKey();
            String propertyKey = entry.getValue();
            String envValue = environment.getProperty(envVar);
            
            if (envValue != null && !envValue.trim().isEmpty()) {
                Object convertedValue = convertValue(propertyKey, envValue.trim());
                envProperties.put(propertyKey, convertedValue);
                overrideCount++;
                
                logger.debug("环境变量覆盖: {} = {} -> {} = {}", 
                    envVar, envValue, propertyKey, convertedValue);
            }
        }
        
        // 处理特殊的集群节点列表
        String clusterNodes = environment.getProperty("HSC_REGISTRY_SERVER_CLUSTER_NODES");
        if (clusterNodes != null && !clusterNodes.trim().isEmpty()) {
            List<String> nodeList = Arrays.asList(clusterNodes.split(","));
            // 清理空白字符
            nodeList = nodeList.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
            envProperties.put("hsc.registry.server.cluster.nodes", nodeList);
        }
        
        // 如果有环境变量覆盖，添加到环境中
        if (!envProperties.isEmpty()) {
            MapPropertySource envPropertySource = new MapPropertySource(
                "environmentOverrides", envProperties);
            environment.getPropertySources().addFirst(envPropertySource);
            
            logger.info("已应用 {} 个环境变量配置覆盖", overrideCount);
        } else {
            logger.debug("未发现需要覆盖的环境变量配置");
        }
        
        // 记录当前环境信息
        logEnvironmentInfo(environment);
    }

    /**
     * 转换配置值类型
     */
    private Object convertValue(String propertyKey, String value) {
        try {
            // 布尔值转换
            if (propertyKey.contains("enabled") || propertyKey.contains("metrics-enabled")) {
                return Boolean.parseBoolean(value);
            }
            
            // 数字转换
            if (propertyKey.contains("port") || propertyKey.contains("max-retry")) {
                return Integer.parseInt(value);
            }
            
            // 时间间隔转换
            if (propertyKey.contains("interval") || propertyKey.contains("timeout")) {
                // 支持秒数或Duration格式
                if (value.matches("\\d+")) {
                    return Duration.ofSeconds(Long.parseLong(value));
                } else {
                    return Duration.parse(value);
                }
            }
            
            // 默认返回字符串
            return value;
        } catch (Exception e) {
            logger.warn("配置值转换失败: {} = {}, 使用原始字符串值", propertyKey, value, e);
            return value;
        }
    }

    /**
     * 记录环境信息
     */
    private void logEnvironmentInfo(ConfigurableEnvironment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        String[] defaultProfiles = environment.getDefaultProfiles();
        
        logger.info("当前激活的配置文件: {}", 
            activeProfiles.length > 0 ? Arrays.toString(activeProfiles) : "无");
        logger.info("默认配置文件: {}", Arrays.toString(defaultProfiles));
        
        // 记录关键配置值
        logKeyConfiguration(environment);
    }

    /**
     * 记录关键配置信息
     */
    private void logKeyConfiguration(ConfigurableEnvironment environment) {
        logger.info("关键配置信息:");
        logger.info("- 服务端口: {}", environment.getProperty("hsc.registry.server.port", "8761"));
        logger.info("- 集群模式: {}", environment.getProperty("hsc.registry.server.cluster.enabled", "false"));
        logger.info("- 安全认证: {}", environment.getProperty("hsc.registry.server.security.enabled", "false"));
        logger.info("- 健康检查: {}", environment.getProperty("hsc.registry.server.health-check.enabled", "true"));
        logger.info("- 存储类型: {}", environment.getProperty("hsc.registry.server.storage.type", "memory"));
        
        // 如果启用了集群，记录节点信息
        boolean clusterEnabled = Boolean.parseBoolean(
            environment.getProperty("hsc.registry.server.cluster.enabled", "false"));
        if (clusterEnabled) {
            String nodes = environment.getProperty("hsc.registry.server.cluster.nodes");
            logger.info("- 集群节点: {}", nodes != null ? nodes : "未配置");
        }
        
        // 如果启用了安全认证，记录API密钥长度
        boolean securityEnabled = Boolean.parseBoolean(
            environment.getProperty("hsc.registry.server.security.enabled", "false"));
        if (securityEnabled) {
            String apiKey = environment.getProperty("hsc.registry.server.security.api-key");
            if (apiKey != null) {
                logger.info("- API密钥长度: {} 位", apiKey.length());
            }
        }
    }

    /**
     * 获取支持的环境变量列表
     */
    public static Set<String> getSupportedEnvironmentVariables() {
        return ENV_TO_PROPERTY_MAPPING.keySet();
    }

    /**
     * 获取环境变量到属性的映射
     */
    public static Map<String, String> getEnvironmentPropertyMapping() {
        return Collections.unmodifiableMap(ENV_TO_PROPERTY_MAPPING);
    }
}