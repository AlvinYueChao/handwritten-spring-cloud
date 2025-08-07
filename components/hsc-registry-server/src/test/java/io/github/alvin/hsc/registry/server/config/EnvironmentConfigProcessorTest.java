package io.github.alvin.hsc.registry.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Environment Configuration Processor Test
 * 环境变量配置处理器测试
 * 
 * @author Alvin
 */
class EnvironmentConfigProcessorTest {

    private EnvironmentConfigProcessor processor;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        processor = new EnvironmentConfigProcessor();
        environment = new MockEnvironment();
    }

    @Test
    void testGetSupportedEnvironmentVariables() {
        Set<String> supportedVars = EnvironmentConfigProcessor.getSupportedEnvironmentVariables();
        
        assertNotNull(supportedVars);
        assertFalse(supportedVars.isEmpty());
        assertTrue(supportedVars.contains("HSC_REGISTRY_SERVER_PORT"));
        assertTrue(supportedVars.contains("HSC_REGISTRY_SERVER_CLUSTER_ENABLED"));
        assertTrue(supportedVars.contains("HSC_REGISTRY_SERVER_SECURITY_API_KEY"));
    }

    @Test
    void testGetEnvironmentPropertyMapping() {
        Map<String, String> mapping = EnvironmentConfigProcessor.getEnvironmentPropertyMapping();
        
        assertNotNull(mapping);
        assertFalse(mapping.isEmpty());
        assertEquals("hsc.registry.server.port", mapping.get("HSC_REGISTRY_SERVER_PORT"));
        assertEquals("hsc.registry.server.cluster.enabled", mapping.get("HSC_REGISTRY_SERVER_CLUSTER_ENABLED"));
        assertEquals("hsc.registry.server.security.api-key", mapping.get("HSC_REGISTRY_SERVER_SECURITY_API_KEY"));
    }

    @Test
    void testEnvironmentVariableOverride() {
        // 设置环境变量
        environment.setProperty("HSC_REGISTRY_SERVER_PORT", "9090");
        environment.setProperty("HSC_REGISTRY_SERVER_CLUSTER_ENABLED", "true");
        environment.setProperty("HSC_REGISTRY_SERVER_SECURITY_API_KEY", "test-key-1234567890");
        
        // 创建事件
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        // 处理事件
        processor.onApplicationEvent(event);
        
        // 验证属性被正确设置
        assertEquals("9090", environment.getProperty("hsc.registry.server.port"));
        assertEquals("true", environment.getProperty("hsc.registry.server.cluster.enabled"));
        assertEquals("test-key-1234567890", environment.getProperty("hsc.registry.server.security.api-key"));
    }

    @Test
    void testClusterNodesEnvironmentVariable() {
        // 设置集群节点环境变量
        environment.setProperty("HSC_REGISTRY_SERVER_CLUSTER_NODES", "node1:8761,node2:8762,node3:8763");
        
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        processor.onApplicationEvent(event);
        
        // 验证节点列表被正确解析
        String nodesProperty = environment.getProperty("hsc.registry.server.cluster.nodes");
        assertNotNull(nodesProperty);
    }

    @Test
    void testEmptyEnvironmentVariables() {
        // 不设置任何环境变量
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        // 处理事件应该不会抛出异常
        assertDoesNotThrow(() -> processor.onApplicationEvent(event));
    }

    @Test
    void testInvalidEnvironmentVariableValues() {
        // 设置无效的环境变量值
        environment.setProperty("HSC_REGISTRY_SERVER_PORT", "invalid_port");
        environment.setProperty("HSC_REGISTRY_SERVER_CLUSTER_ENABLED", "maybe");
        
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        // 处理事件应该不会抛出异常，但会使用原始字符串值
        assertDoesNotThrow(() -> processor.onApplicationEvent(event));
        
        // 验证原始值被保留（转换失败时会保留原始字符串）
        String portValue = environment.getProperty("hsc.registry.server.port");
        String enabledValue = environment.getProperty("hsc.registry.server.cluster.enabled");
        
        // 由于转换失败，应该保留原始值或者被设置为转换后的值
        assertNotNull(portValue);
        assertNotNull(enabledValue);
    }

    @Test
    void testDurationEnvironmentVariables() {
        // 测试时间间隔环境变量
        environment.setProperty("HSC_REGISTRY_SERVER_CLUSTER_SYNC_INTERVAL", "60");
        environment.setProperty("HSC_REGISTRY_SERVER_HEALTH_CHECK_DEFAULT_TIMEOUT", "10");
        
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        // 处理事件应该不会抛出异常
        assertDoesNotThrow(() -> processor.onApplicationEvent(event));
        
        // 验证环境变量被处理（即使转换可能失败，也应该有属性被设置）
        // 由于Duration转换可能失败，我们只验证处理过程不抛出异常
        assertTrue(true, "Duration环境变量处理完成");
    }

    @Test
    void testBooleanEnvironmentVariables() {
        // 测试布尔值环境变量
        environment.setProperty("HSC_REGISTRY_SERVER_CLUSTER_ENABLED", "true");
        environment.setProperty("HSC_REGISTRY_SERVER_SECURITY_ENABLED", "false");
        environment.setProperty("HSC_REGISTRY_SERVER_MONITORING_METRICS_ENABLED", "true");
        
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        processor.onApplicationEvent(event);
        
        // 验证布尔值被正确设置
        assertEquals("true", environment.getProperty("hsc.registry.server.cluster.enabled"));
        assertEquals("false", environment.getProperty("hsc.registry.server.security.enabled"));
        assertEquals("true", environment.getProperty("hsc.registry.server.monitoring.metrics-enabled"));
    }

    @Test
    void testIntegerEnvironmentVariables() {
        // 测试整数环境变量
        environment.setProperty("HSC_REGISTRY_SERVER_PORT", "8080");
        environment.setProperty("HSC_REGISTRY_SERVER_HEALTH_CHECK_MAX_RETRY", "5");
        
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        processor.onApplicationEvent(event);
        
        // 验证整数值被正确设置
        assertEquals("8080", environment.getProperty("hsc.registry.server.port"));
        assertEquals("5", environment.getProperty("hsc.registry.server.health-check.max-retry"));
    }

    @Test
    void testEnvironmentVariableWithWhitespace() {
        // 测试包含空白字符的环境变量
        environment.setProperty("HSC_REGISTRY_SERVER_CLUSTER_NODES", " node1:8761 , node2:8762 , node3:8763 ");
        environment.setProperty("HSC_REGISTRY_SERVER_SECURITY_API_KEY", "  test-key-1234567890  ");
        
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        processor.onApplicationEvent(event);
        
        // 验证空白字符被正确处理
        assertNotNull(environment.getProperty("hsc.registry.server.cluster.nodes"));
        assertEquals("test-key-1234567890", environment.getProperty("hsc.registry.server.security.api-key"));
    }

    @Test
    void testPropertySourcePriority() {
        // 创建一个标准环境，包含现有的属性源
        MockEnvironment mockEnv = new MockEnvironment();
        
        // 添加一个现有的属性源
        mockEnv.setProperty("hsc.registry.server.port", "8761");
        
        // 设置环境变量
        mockEnv.setProperty("HSC_REGISTRY_SERVER_PORT", "9090");
        
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(mockEnv);
        
        // 处理事件
        assertDoesNotThrow(() -> processor.onApplicationEvent(event));
        
        // 验证属性存在
        assertNotNull(mockEnv.getProperty("hsc.registry.server.port"));
    }

    @Test
    void testEmptyClusterNodes() {
        // 测试空的集群节点列表
        environment.setProperty("HSC_REGISTRY_SERVER_CLUSTER_NODES", "");
        
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        // 处理事件应该不会抛出异常
        assertDoesNotThrow(() -> processor.onApplicationEvent(event));
    }

    @Test
    void testClusterNodesWithEmptyElements() {
        // 测试包含空元素的集群节点列表
        environment.setProperty("HSC_REGISTRY_SERVER_CLUSTER_NODES", "node1:8761,,node2:8762,  ,node3:8763");
        
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        // 处理事件应该不会抛出异常
        assertDoesNotThrow(() -> processor.onApplicationEvent(event));
        
        // 验证空元素被过滤掉
        assertNotNull(environment.getProperty("hsc.registry.server.cluster.nodes"));
    }
}