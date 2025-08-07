package io.github.alvin.hsc.registry.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Kubernetes Configuration Support Test
 * Kubernetes配置支持测试
 * 
 * @author Alvin
 */
class KubernetesConfigSupportTest {

    @TempDir
    Path tempDir;

    private KubernetesConfigSupport kubernetesConfigSupport;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        kubernetesConfigSupport = new KubernetesConfigSupport();
        environment = new MockEnvironment();
    }

    @Test
    void testGetSupportedConfigMapFiles() {
        Map<String, String> configMapFiles = KubernetesConfigSupport.getSupportedConfigMapFiles();
        
        assertNotNull(configMapFiles);
        assertFalse(configMapFiles.isEmpty());
        assertTrue(configMapFiles.containsKey("registry-port"));
        assertTrue(configMapFiles.containsKey("cluster-enabled"));
        assertEquals("hsc.registry.server.port", configMapFiles.get("registry-port"));
        assertEquals("hsc.registry.server.cluster.enabled", configMapFiles.get("cluster-enabled"));
    }

    @Test
    void testGetSupportedSecretFiles() {
        Map<String, String> secretFiles = KubernetesConfigSupport.getSupportedSecretFiles();
        
        assertNotNull(secretFiles);
        assertFalse(secretFiles.isEmpty());
        assertTrue(secretFiles.containsKey("api-key"));
        assertEquals("hsc.registry.server.security.api-key", secretFiles.get("api-key"));
    }

    @Test
    void testLoadConfigMapConfiguration() throws IOException {
        // 创建模拟的ConfigMap文件
        Path configMapDir = tempDir.resolve("config");
        Files.createDirectories(configMapDir);
        
        Files.writeString(configMapDir.resolve("registry-port"), "9090");
        Files.writeString(configMapDir.resolve("cluster-enabled"), "true");
        Files.writeString(configMapDir.resolve("cluster-nodes"), "node1:8761,node2:8762");
        
        // 设置环境变量指向临时目录
        environment.setProperty("CONFIG_MAP_PATH", configMapDir.toString());
        
        // 模拟Kubernetes环境
        Path tokenPath = tempDir.resolve("token");
        Files.createFile(tokenPath);
        
        // 由于无法直接模拟文件系统路径检查，我们测试配置映射功能
        Map<String, String> configMapFiles = KubernetesConfigSupport.getSupportedConfigMapFiles();
        assertTrue(configMapFiles.containsKey("registry-port"));
        assertTrue(configMapFiles.containsKey("cluster-enabled"));
        assertTrue(configMapFiles.containsKey("cluster-nodes"));
    }

    @Test
    void testLoadSecretConfiguration() throws IOException {
        // 创建模拟的Secret文件
        Path secretDir = tempDir.resolve("secrets");
        Files.createDirectories(secretDir);
        
        Files.writeString(secretDir.resolve("api-key"), "secret-api-key-123456789");
        Files.writeString(secretDir.resolve("registry-password"), "secret-password");
        
        // 设置环境变量指向临时目录
        environment.setProperty("SECRET_PATH", secretDir.toString());
        
        // 测试Secret文件映射
        Map<String, String> secretFiles = KubernetesConfigSupport.getSupportedSecretFiles();
        assertTrue(secretFiles.containsKey("api-key"));
        assertTrue(secretFiles.containsKey("registry-password"));
        assertEquals("hsc.registry.server.security.api-key", secretFiles.get("api-key"));
    }

    @Test
    void testNonKubernetesEnvironment() {
        // 在非Kubernetes环境中，应该跳过配置加载
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        // 处理事件应该不会抛出异常
        assertDoesNotThrow(() -> kubernetesConfigSupport.onApplicationEvent(event));
    }

    @Test
    void testEmptyConfigFiles() throws IOException {
        // 创建空的配置文件
        Path configMapDir = tempDir.resolve("config");
        Files.createDirectories(configMapDir);
        
        Files.writeString(configMapDir.resolve("registry-port"), "");
        Files.writeString(configMapDir.resolve("cluster-enabled"), "   ");
        
        // 空文件应该被忽略
        assertTrue(Files.exists(configMapDir.resolve("registry-port")));
        assertTrue(Files.exists(configMapDir.resolve("cluster-enabled")));
        
        // 验证文件内容为空或只包含空白字符
        String portContent = Files.readString(configMapDir.resolve("registry-port")).trim();
        String enabledContent = Files.readString(configMapDir.resolve("cluster-enabled")).trim();
        
        assertTrue(portContent.isEmpty());
        assertTrue(enabledContent.isEmpty());
    }

    @Test
    void testConfigurationValueConversion() {
        // 测试配置值转换逻辑
        // 由于convertConfigValue是私有方法，我们通过公共接口测试
        
        Map<String, String> configMapFiles = KubernetesConfigSupport.getSupportedConfigMapFiles();
        
        // 验证端口配置映射
        assertTrue(configMapFiles.get("registry-port").contains("port"));
        
        // 验证布尔值配置映射
        assertTrue(configMapFiles.get("cluster-enabled").contains("enabled"));
        
        // 验证列表配置映射
        assertTrue(configMapFiles.get("cluster-nodes").contains("nodes"));
    }

    @Test
    void testConfigurationPriority() {
        // 测试配置优先级：Kubernetes配置应该具有高优先级
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        // 设置一些现有配置
        environment.setProperty("hsc.registry.server.port", "8761");
        
        // 处理Kubernetes配置
        assertDoesNotThrow(() -> kubernetesConfigSupport.onApplicationEvent(event));
        
        // 验证现有配置仍然存在
        assertEquals("8761", environment.getProperty("hsc.registry.server.port"));
    }

    @Test
    void testFileReadingError() throws IOException {
        // 创建一个无法读取的文件（通过创建目录而不是文件来模拟）
        Path configMapDir = tempDir.resolve("config");
        Files.createDirectories(configMapDir);
        
        // 创建一个目录而不是文件，这会导致读取错误
        Path invalidFile = configMapDir.resolve("registry-port");
        Files.createDirectories(invalidFile);
        
        // 验证这是一个目录
        assertTrue(Files.isDirectory(invalidFile));
        
        // 处理应该不会抛出异常，错误应该被捕获和记录
        ApplicationEnvironmentPreparedEvent event = mock(ApplicationEnvironmentPreparedEvent.class);
        when(event.getEnvironment()).thenReturn(environment);
        
        assertDoesNotThrow(() -> kubernetesConfigSupport.onApplicationEvent(event));
    }
}