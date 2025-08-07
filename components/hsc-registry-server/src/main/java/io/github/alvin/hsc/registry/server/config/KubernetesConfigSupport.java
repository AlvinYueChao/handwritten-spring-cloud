package io.github.alvin.hsc.registry.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Kubernetes Configuration Support
 * Kubernetes配置支持，用于读取ConfigMap和Secret
 * 
 * 支持的Kubernetes配置：
 * - ConfigMap挂载路径：/etc/config/
 * - Secret挂载路径：/etc/secrets/
 * 
 * @author Alvin
 */
@Component
public class KubernetesConfigSupport implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesConfigSupport.class);

    /**
     * 默认ConfigMap挂载路径
     */
    private static final String DEFAULT_CONFIGMAP_PATH = "/etc/config";

    /**
     * 默认Secret挂载路径
     */
    private static final String DEFAULT_SECRET_PATH = "/etc/secrets";

    /**
     * 配置文件映射
     */
    private static final Map<String, String> CONFIG_FILE_MAPPING = Map.of(
        "registry-port", "hsc.registry.server.port",
        "registry-context-path", "hsc.registry.server.context-path",
        "cluster-enabled", "hsc.registry.server.cluster.enabled",
        "cluster-nodes", "hsc.registry.server.cluster.nodes",
        "security-enabled", "hsc.registry.server.security.enabled",
        "health-check-enabled", "hsc.registry.server.health-check.enabled"
    );

    /**
     * Secret文件映射
     */
    private static final Map<String, String> SECRET_FILE_MAPPING = Map.of(
        "api-key", "hsc.registry.server.security.api-key",
        "registry-password", "hsc.registry.server.security.password"
    );

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        
        // 检查是否在Kubernetes环境中运行
        if (!isRunningInKubernetes()) {
            logger.debug("未检测到Kubernetes环境，跳过Kubernetes配置加载");
            return;
        }
        
        logger.info("检测到Kubernetes环境，开始加载ConfigMap和Secret配置");
        
        Map<String, Object> kubernetesProperties = new HashMap<>();
        
        // 加载ConfigMap配置
        loadConfigMapProperties(kubernetesProperties);
        
        // 加载Secret配置
        loadSecretProperties(kubernetesProperties);
        
        // 如果有Kubernetes配置，添加到环境中
        if (!kubernetesProperties.isEmpty()) {
            MapPropertySource kubernetesPropertySource = new MapPropertySource(
                "kubernetesConfig", kubernetesProperties);
            environment.getPropertySources().addFirst(kubernetesPropertySource);
            
            logger.info("已加载 {} 个Kubernetes配置属性", kubernetesProperties.size());
        }
    }

    /**
     * 检查是否在Kubernetes环境中运行
     */
    private boolean isRunningInKubernetes() {
        // 检查Kubernetes服务账户token文件
        Path tokenPath = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/token");
        if (Files.exists(tokenPath)) {
            return true;
        }
        
        // 检查环境变量
        String kubernetesService = System.getenv("KUBERNETES_SERVICE_HOST");
        if (kubernetesService != null && !kubernetesService.isEmpty()) {
            return true;
        }
        
        // 检查ConfigMap或Secret挂载路径
        return Files.exists(Paths.get(DEFAULT_CONFIGMAP_PATH)) || 
               Files.exists(Paths.get(DEFAULT_SECRET_PATH));
    }

    /**
     * 加载ConfigMap配置
     */
    private void loadConfigMapProperties(Map<String, Object> properties) {
        String configMapPath = System.getenv("CONFIG_MAP_PATH");
        if (configMapPath == null) {
            configMapPath = DEFAULT_CONFIGMAP_PATH;
        }
        
        Path configPath = Paths.get(configMapPath);
        if (!Files.exists(configPath)) {
            logger.debug("ConfigMap路径不存在: {}", configMapPath);
            return;
        }
        
        logger.info("从ConfigMap路径加载配置: {}", configMapPath);
        
        for (Map.Entry<String, String> entry : CONFIG_FILE_MAPPING.entrySet()) {
            String fileName = entry.getKey();
            String propertyKey = entry.getValue();
            
            Path filePath = configPath.resolve(fileName);
            if (Files.exists(filePath)) {
                try {
                    String value = Files.readString(filePath).trim();
                    if (!value.isEmpty()) {
                        Object convertedValue = convertConfigValue(propertyKey, value);
                        properties.put(propertyKey, convertedValue);
                        logger.debug("加载ConfigMap配置: {} = {}", propertyKey, 
                            propertyKey.contains("password") || propertyKey.contains("key") ? "***" : convertedValue);
                    }
                } catch (IOException e) {
                    logger.warn("读取ConfigMap文件失败: {}", filePath, e);
                }
            }
        }
    }

    /**
     * 加载Secret配置
     */
    private void loadSecretProperties(Map<String, Object> properties) {
        String secretPath = System.getenv("SECRET_PATH");
        if (secretPath == null) {
            secretPath = DEFAULT_SECRET_PATH;
        }
        
        Path secretDir = Paths.get(secretPath);
        if (!Files.exists(secretDir)) {
            logger.debug("Secret路径不存在: {}", secretPath);
            return;
        }
        
        logger.info("从Secret路径加载配置: {}", secretPath);
        
        for (Map.Entry<String, String> entry : SECRET_FILE_MAPPING.entrySet()) {
            String fileName = entry.getKey();
            String propertyKey = entry.getValue();
            
            Path filePath = secretDir.resolve(fileName);
            if (Files.exists(filePath)) {
                try {
                    String value = Files.readString(filePath).trim();
                    if (!value.isEmpty()) {
                        properties.put(propertyKey, value);
                        logger.debug("加载Secret配置: {} = ***", propertyKey);
                    }
                } catch (IOException e) {
                    logger.warn("读取Secret文件失败: {}", filePath, e);
                }
            }
        }
    }

    /**
     * 转换配置值类型
     */
    private Object convertConfigValue(String propertyKey, String value) {
        try {
            // 布尔值转换
            if (propertyKey.contains("enabled")) {
                return Boolean.parseBoolean(value);
            }
            
            // 数字转换
            if (propertyKey.contains("port")) {
                return Integer.parseInt(value);
            }
            
            // 列表转换（逗号分隔）
            if (propertyKey.contains("nodes")) {
                return value.split(",");
            }
            
            // 默认返回字符串
            return value;
        } catch (Exception e) {
            logger.warn("配置值转换失败: {} = {}, 使用原始字符串值", propertyKey, value, e);
            return value;
        }
    }

    /**
     * 获取支持的ConfigMap文件列表
     */
    public static Map<String, String> getSupportedConfigMapFiles() {
        return CONFIG_FILE_MAPPING;
    }

    /**
     * 获取支持的Secret文件列表
     */
    public static Map<String, String> getSupportedSecretFiles() {
        return SECRET_FILE_MAPPING;
    }
}