package io.github.alvin.hsc.registry.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Configuration Validator Test
 * 配置验证器测试
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationValidatorTest {

    @Mock
    private Validator validator;

    private RegistryServerProperties registryServerProperties;
    private HealthCheckProperties healthCheckProperties;
    private ConfigurationValidator configurationValidator;

    @BeforeEach
    void setUp() {
        registryServerProperties = new RegistryServerProperties();
        healthCheckProperties = new HealthCheckProperties();
        configurationValidator = new ConfigurationValidator(
            registryServerProperties, healthCheckProperties, validator);
    }

    @Test
    void testValidateConfigurationSuccess() {
        // 模拟验证成功
        lenient().when(validator.validate(any(RegistryServerProperties.class))).thenReturn(Set.of());
        lenient().when(validator.validate(any(HealthCheckProperties.class))).thenReturn(Set.of());
        
        // 验证配置应该成功
        assertDoesNotThrow(() -> configurationValidator.validateConfiguration());
        
        // 验证方法被调用
        verify(validator).validate(registryServerProperties);
        verify(validator).validate(healthCheckProperties);
    }

    @Test
    void testValidateConfigurationWithViolations() {
        // 创建模拟的约束违反
        ConstraintViolation<RegistryServerProperties> violation = mock(ConstraintViolation.class);
        lenient().when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        lenient().when(violation.getMessage()).thenReturn("端口号无效");
        
        lenient().when(validator.validate(registryServerProperties)).thenReturn(Set.of(violation));
        lenient().when(validator.validate(healthCheckProperties)).thenReturn(Set.of());
        
        // 验证配置应该失败
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> configurationValidator.validateConfiguration());
        
        assertTrue(exception.getMessage().contains("注册中心配置验证失败"));
        assertTrue(exception.getMessage().contains("端口号无效"));
    }

    @Test
    void testValidateHealthCheckConfigurationWithViolations() {
        // 创建健康检查配置的约束违反
        ConstraintViolation<HealthCheckProperties> violation = mock(ConstraintViolation.class);
        lenient().when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        lenient().when(violation.getMessage()).thenReturn("检查间隔无效");
        
        lenient().when(validator.validate(registryServerProperties)).thenReturn(Set.of());
        lenient().when(validator.validate(healthCheckProperties)).thenReturn(Set.of(violation));
        
        // 验证配置应该失败
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> configurationValidator.validateConfiguration());
        
        assertTrue(exception.getMessage().contains("健康检查配置验证失败"));
        assertTrue(exception.getMessage().contains("检查间隔无效"));
    }

    @Test
    void testCustomValidationClusterWithoutNodes() {
        // 启用集群但不配置节点
        registryServerProperties.getCluster().setEnabled(true);
        registryServerProperties.getCluster().setNodes(List.of());
        
        lenient().when(validator.validate(registryServerProperties)).thenReturn(Set.of());
        lenient().when(validator.validate(healthCheckProperties)).thenReturn(Set.of());
        
        // 验证应该失败
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> configurationValidator.validateConfiguration());
        
        assertTrue(exception.getMessage().contains("集群模式启用时必须配置集群节点"));
    }

    @Test
    void testCustomValidationSecurityWithoutApiKey() {
        // 启用安全认证但不配置API密钥
        registryServerProperties.getSecurity().setEnabled(true);
        registryServerProperties.getSecurity().setApiKey("");
        
        lenient().when(validator.validate(registryServerProperties)).thenReturn(Set.of());
        lenient().when(validator.validate(healthCheckProperties)).thenReturn(Set.of());
        
        // 验证应该失败
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> configurationValidator.validateConfiguration());
        
        assertTrue(exception.getMessage().contains("安全认证启用时必须配置API密钥"));
    }

    @Test
    void testGetConfigurationSummary() {
        String summary = configurationValidator.getConfigurationSummary();
        
        assertNotNull(summary);
        assertTrue(summary.contains("注册中心配置摘要"));
        assertTrue(summary.contains("服务端口"));
        assertTrue(summary.contains("集群模式"));
        assertTrue(summary.contains("安全认证"));
        assertTrue(summary.contains("健康检查"));
        assertTrue(summary.contains("存储类型"));
        assertTrue(summary.contains("监控指标"));
    }

    @Test
    void testConfigurationSummaryWithClusterEnabled() {
        // 启用集群模式
        registryServerProperties.getCluster().setEnabled(true);
        registryServerProperties.getCluster().setNodes(List.of("node1:8761", "node2:8762"));
        
        String summary = configurationValidator.getConfigurationSummary();
        
        assertTrue(summary.contains("集群模式: 启用"));
    }

    @Test
    void testConfigurationSummaryWithSecurityEnabled() {
        // 启用安全认证
        registryServerProperties.getSecurity().setEnabled(true);
        registryServerProperties.getSecurity().setApiKey("test-key-1234567890123456");
        
        String summary = configurationValidator.getConfigurationSummary();
        
        assertTrue(summary.contains("安全认证: 启用"));
    }

    @Test
    void testConfigurationSummaryWithHealthCheckDisabled() {
        // 禁用健康检查
        healthCheckProperties.setEnabled(false);
        
        String summary = configurationValidator.getConfigurationSummary();
        
        assertTrue(summary.contains("健康检查: 禁用"));
    }

    @Test
    void testConfigurationSummaryWithCustomPort() {
        // 设置自定义端口
        registryServerProperties.setPort(9090);
        
        String summary = configurationValidator.getConfigurationSummary();
        
        assertTrue(summary.contains("服务端口: 9090"));
    }

    @Test
    void testConfigurationSummaryWithCustomStorageType() {
        // 设置自定义存储类型
        registryServerProperties.getStorage().setType("redis");
        
        String summary = configurationValidator.getConfigurationSummary();
        
        assertTrue(summary.contains("存储类型: redis"));
    }

    @Test
    void testConfigurationSummaryWithMonitoringDisabled() {
        // 禁用监控指标
        registryServerProperties.getMonitoring().setMetricsEnabled(false);
        
        String summary = configurationValidator.getConfigurationSummary();
        
        assertTrue(summary.contains("监控指标: 禁用"));
    }

    @Test
    void testValidateConfigurationWithAllFeaturesEnabled() {
        // 启用所有功能
        registryServerProperties.setPort(8080);
        registryServerProperties.getCluster().setEnabled(true);
        registryServerProperties.getCluster().setNodes(List.of("node1:8761", "node2:8762"));
        registryServerProperties.getSecurity().setEnabled(true);
        registryServerProperties.getSecurity().setApiKey("secure-key-1234567890123456");
        healthCheckProperties.setEnabled(true);
        healthCheckProperties.setDefaultInterval(Duration.ofSeconds(30));
        healthCheckProperties.setDefaultTimeout(Duration.ofSeconds(5));
        
        when(validator.validate(any(RegistryServerProperties.class))).thenReturn(Set.of());
        when(validator.validate(any(HealthCheckProperties.class))).thenReturn(Set.of());
        
        // 验证配置应该成功
        assertDoesNotThrow(() -> configurationValidator.validateConfiguration());
        
        String summary = configurationValidator.getConfigurationSummary();
        assertTrue(summary.contains("服务端口: 8080"));
        assertTrue(summary.contains("集群模式: 启用"));
        assertTrue(summary.contains("安全认证: 启用"));
        assertTrue(summary.contains("健康检查: 启用"));
    }
}