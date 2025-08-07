package io.github.alvin.hsc.registry.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;

/**
 * Configuration Validator
 * 配置验证器，在应用启动时验证配置的有效性
 * 
 * @author Alvin
 */
@Component
public class ConfigurationValidator {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidator.class);

    private final RegistryServerProperties registryServerProperties;
    private final HealthCheckProperties healthCheckProperties;
    private final Validator validator;

    public ConfigurationValidator(RegistryServerProperties registryServerProperties,
                                HealthCheckProperties healthCheckProperties,
                                Validator validator) {
        this.registryServerProperties = registryServerProperties;
        this.healthCheckProperties = healthCheckProperties;
        this.validator = validator;
    }

    /**
     * 在应用启动完成后验证配置
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        logger.info("开始验证注册中心配置...");

        // 验证主配置
        validateRegistryServerProperties();
        
        // 验证健康检查配置
        validateHealthCheckProperties();
        
        // 执行自定义验证逻辑
        validateCustomRules();

        logger.info("配置验证完成");
    }

    /**
     * 验证注册中心主配置
     */
    private void validateRegistryServerProperties() {
        Set<ConstraintViolation<RegistryServerProperties>> violations = 
            validator.validate(registryServerProperties);
        
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("注册中心配置验证失败：\n");
            for (ConstraintViolation<RegistryServerProperties> violation : violations) {
                sb.append("- ").append(violation.getPropertyPath())
                  .append(": ").append(violation.getMessage()).append("\n");
            }
            throw new IllegalArgumentException(sb.toString());
        }

        // 执行自定义验证
        try {
            registryServerProperties.validate();
        } catch (IllegalArgumentException e) {
            logger.error("注册中心配置验证失败: {}", e.getMessage());
            throw e;
        }

        logger.debug("注册中心主配置验证通过: {}", registryServerProperties);
    }

    /**
     * 验证健康检查配置
     */
    private void validateHealthCheckProperties() {
        Set<ConstraintViolation<HealthCheckProperties>> violations = 
            validator.validate(healthCheckProperties);
        
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("健康检查配置验证失败：\n");
            for (ConstraintViolation<HealthCheckProperties> violation : violations) {
                sb.append("- ").append(violation.getPropertyPath())
                  .append(": ").append(violation.getMessage()).append("\n");
            }
            throw new IllegalArgumentException(sb.toString());
        }

        logger.debug("健康检查配置验证通过: {}", healthCheckProperties);
    }

    /**
     * 执行自定义验证规则
     */
    private void validateCustomRules() {
        // 验证端口冲突
        if (registryServerProperties.getPort() == 8080) {
            logger.warn("注册中心使用8080端口可能与其他服务冲突，建议使用8761端口");
        }

        // 验证集群配置
        if (registryServerProperties.getCluster().isEnabled()) {
            var nodes = registryServerProperties.getCluster().getNodes();
            if (nodes.isEmpty()) {
                throw new IllegalArgumentException("启用集群模式时必须配置至少一个集群节点");
            }
            
            // 验证节点格式
            for (String node : nodes) {
                if (!node.matches("^[^:]+:\\d+$")) {
                    throw new IllegalArgumentException("集群节点格式错误: " + node + "，正确格式为 host:port");
                }
            }
            
            logger.info("集群模式已启用，节点列表: {}", nodes);
        }

        // 验证安全配置
        if (registryServerProperties.getSecurity().isEnabled()) {
            String apiKey = registryServerProperties.getSecurity().getApiKey();
            if (apiKey.equals("hsc-registry-default-key-2024")) {
                logger.warn("正在使用默认API密钥，生产环境中请更换为自定义密钥");
            }
            
            if (apiKey.length() < 32) {
                logger.warn("API密钥长度较短，建议使用32位以上的强密钥");
            }
            
            logger.info("安全认证已启用，API密钥长度: {} 位", apiKey.length());
        }

        // 验证存储配置
        String storageType = registryServerProperties.getStorage().getType();
        if (!"memory".equals(storageType)) {
            logger.warn("当前版本仅支持内存存储，配置的存储类型 '{}' 将被忽略", storageType);
        }

        // 验证健康检查配置合理性
        if (healthCheckProperties.isEnabled()) {
            var interval = healthCheckProperties.getDefaultInterval();
            var timeout = healthCheckProperties.getDefaultTimeout();
            
            if (timeout.toSeconds() >= interval.toSeconds()) {
                logger.warn("健康检查超时时间 ({}) 不应大于等于检查间隔 ({})", timeout, interval);
            }
            
            if (interval.toSeconds() < 10) {
                logger.warn("健康检查间隔过短 ({})，可能影响性能", interval);
            }
        }
    }

    /**
     * 获取配置摘要信息
     */
    public String getConfigurationSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("注册中心配置摘要:\n");
        sb.append("- 服务端口: ").append(registryServerProperties.getPort()).append("\n");
        sb.append("- 集群模式: ").append(registryServerProperties.getCluster().isEnabled() ? "启用" : "禁用").append("\n");
        sb.append("- 安全认证: ").append(registryServerProperties.getSecurity().isEnabled() ? "启用" : "禁用").append("\n");
        sb.append("- 健康检查: ").append(healthCheckProperties.isEnabled() ? "启用" : "禁用").append("\n");
        sb.append("- 存储类型: ").append(registryServerProperties.getStorage().getType()).append("\n");
        sb.append("- 监控指标: ").append(registryServerProperties.getMonitoring().isMetricsEnabled() ? "启用" : "禁用");
        return sb.toString();
    }
}