package io.github.alvin.hsc.registry.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registry Server Properties Test
 * 注册中心服务器配置属性测试
 * 
 * @author Alvin
 */
class RegistryServerPropertiesTest {

    private Validator validator;
    private RegistryServerProperties properties;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        properties = new RegistryServerProperties();
    }

    @Test
    void testDefaultValues() {
        // 验证默认值
        assertEquals(8761, properties.getPort());
        assertEquals("/", properties.getContextPath());
        assertFalse(properties.getCluster().isEnabled());
        assertTrue(properties.getHealthCheck().isEnabled());
        assertEquals("memory", properties.getStorage().getType());
        assertFalse(properties.getSecurity().isEnabled());
        assertTrue(properties.getMonitoring().isMetricsEnabled());
    }

    @Test
    void testValidConfiguration() {
        // 设置有效配置
        properties.setPort(8080);
        properties.setContextPath("/registry");
        
        // 验证配置
        Set<ConstraintViolation<RegistryServerProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty(), "有效配置不应该有验证错误");
    }

    @Test
    void testInvalidPort() {
        // 测试无效端口
        properties.setPort(100); // 小于1024
        
        Set<ConstraintViolation<RegistryServerProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("端口号必须大于等于1024")));
        
        properties.setPort(70000); // 大于65535
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("端口号必须小于等于65535")));
    }

    @Test
    void testInvalidContextPath() {
        // 测试无效上下文路径
        properties.setContextPath("invalid"); // 不以/开头
        
        Set<ConstraintViolation<RegistryServerProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("上下文路径必须以/开头")));
    }

    @Test
    void testClusterConfiguration() {
        var cluster = properties.getCluster();
        
        // 测试默认值
        assertFalse(cluster.isEnabled());
        assertEquals(Duration.ofSeconds(30), cluster.getSyncInterval());
        
        // 测试设置值
        cluster.setEnabled(true);
        cluster.setNodes(List.of("node1:8761", "node2:8762"));
        cluster.setSyncInterval(Duration.ofSeconds(60));
        
        assertTrue(cluster.isEnabled());
        assertEquals(2, cluster.getNodes().size());
        assertEquals(Duration.ofSeconds(60), cluster.getSyncInterval());
    }

    @Test
    void testHealthCheckConfiguration() {
        var healthCheck = properties.getHealthCheck();
        
        // 测试默认值
        assertTrue(healthCheck.isEnabled());
        assertEquals(Duration.ofSeconds(30), healthCheck.getDefaultInterval());
        assertEquals(Duration.ofSeconds(5), healthCheck.getDefaultTimeout());
        assertEquals(3, healthCheck.getMaxRetry());
        
        // 测试设置值
        healthCheck.setEnabled(false);
        healthCheck.setDefaultInterval(Duration.ofSeconds(60));
        healthCheck.setDefaultTimeout(Duration.ofSeconds(10));
        healthCheck.setMaxRetry(5);
        
        assertFalse(healthCheck.isEnabled());
        assertEquals(Duration.ofSeconds(60), healthCheck.getDefaultInterval());
        assertEquals(Duration.ofSeconds(10), healthCheck.getDefaultTimeout());
        assertEquals(5, healthCheck.getMaxRetry());
    }

    @Test
    void testSecurityConfiguration() {
        var security = properties.getSecurity();
        
        // 测试默认值
        assertFalse(security.isEnabled());
        assertEquals("hsc-registry-default-key-2024", security.getApiKey());
        assertEquals("X-Registry-API-Key", security.getHeaderName());
        assertEquals("api_key", security.getQueryParamName());
        
        // 测试设置值
        security.setEnabled(true);
        security.setApiKey("custom-api-key-12345678");
        security.setHeaderName("X-Custom-Key");
        
        assertTrue(security.isEnabled());
        assertEquals("custom-api-key-12345678", security.getApiKey());
        assertEquals("X-Custom-Key", security.getHeaderName());
    }

    @Test
    void testCustomValidation() {
        // 测试集群模式启用但没有节点的情况
        properties.getCluster().setEnabled(true);
        properties.getCluster().setNodes(List.of()); // 空节点列表
        
        assertThrows(IllegalArgumentException.class, () -> properties.validate());
        
        // 测试安全认证启用但没有API密钥的情况
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setApiKey(""); // 空API密钥
        
        assertThrows(IllegalArgumentException.class, () -> properties.validate());
    }

    @Test
    void testPropertyBinding() {
        // 测试从配置源绑定属性
        Map<String, Object> configMap = Map.of(
            "hsc.registry.server.port", 9090,
            "hsc.registry.server.context-path", "/test",
            "hsc.registry.server.cluster.enabled", true,
            "hsc.registry.server.cluster.nodes", List.of("node1:8761", "node2:8762"),
            "hsc.registry.server.security.enabled", true,
            "hsc.registry.server.security.api-key", "test-key-1234567890123456"
        );
        
        ConfigurationPropertySource source = new MapConfigurationPropertySource(configMap);
        Binder binder = new Binder(source);
        
        RegistryServerProperties boundProperties = binder.bind("hsc.registry.server", RegistryServerProperties.class)
            .orElseThrow(() -> new RuntimeException("Failed to bind properties"));
        
        assertEquals(9090, boundProperties.getPort());
        assertEquals("/test", boundProperties.getContextPath());
        assertTrue(boundProperties.getCluster().isEnabled());
        assertEquals(2, boundProperties.getCluster().getNodes().size());
        assertTrue(boundProperties.getSecurity().isEnabled());
        assertEquals("test-key-1234567890123456", boundProperties.getSecurity().getApiKey());
    }

    @Test
    void testToString() {
        String str = properties.toString();
        assertNotNull(str);
        assertTrue(str.contains("port=8761"));
        assertTrue(str.contains("contextPath='/'"));
    }

    @Test
    void testValidationConstraints() {
        // 测试健康检查配置的验证约束
        var healthCheck = properties.getHealthCheck();
        
        // 测试无效的重试次数
        healthCheck.setMaxRetry(-1);
        Set<ConstraintViolation<RegistryServerProperties>> violations = validator.validate(properties);
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("重试次数不能为负数")));
        
        healthCheck.setMaxRetry(15);
        violations = validator.validate(properties);
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("重试次数不能超过10次")));
        
        // 测试存储类型验证
        properties.getStorage().setType("invalid");
        violations = validator.validate(properties);
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("存储类型必须是memory、redis或database")));
    }

    @Test
    void testSecurityApiKeyValidation() {
        var security = properties.getSecurity();
        
        // 测试API密钥长度验证
        security.setApiKey("short"); // 少于16位
        Set<ConstraintViolation<RegistryServerProperties>> violations = validator.validate(properties);
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("API密钥长度不能少于16位")));
        
        // 测试有效的API密钥
        security.setApiKey("valid-api-key-1234567890");
        violations = validator.validate(properties);
        assertFalse(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("apiKey")));
    }
}