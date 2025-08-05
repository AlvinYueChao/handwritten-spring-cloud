package io.github.alvin.hsc.registry.server.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Health Check Properties Test
 * 健康检查配置属性测试
 * 
 * @author Alvin
 */
class HealthCheckPropertiesTest {

    @Test
    void testDefaultValues() {
        // Given
        HealthCheckProperties properties = new HealthCheckProperties();

        // Then
        assertTrue(properties.isEnabled());
        assertEquals(Duration.ofSeconds(30), properties.getDefaultInterval());
        assertEquals(Duration.ofSeconds(5), properties.getDefaultTimeout());
        assertEquals(3, properties.getMaxRetry());
        assertEquals(Duration.ofSeconds(90), properties.getInstanceExpirationThreshold());
        assertEquals(10, properties.getThreadPoolSize());
        assertEquals(Duration.ofSeconds(30), properties.getExpirationCheckInterval());
        assertEquals(Duration.ofSeconds(60), properties.getTaskSyncInterval());
    }

    @Test
    void testSettersAndGetters() {
        // Given
        HealthCheckProperties properties = new HealthCheckProperties();

        // When
        properties.setEnabled(false);
        properties.setDefaultInterval(Duration.ofSeconds(60));
        properties.setDefaultTimeout(Duration.ofSeconds(10));
        properties.setMaxRetry(5);
        properties.setInstanceExpirationThreshold(Duration.ofSeconds(120));
        properties.setThreadPoolSize(20);
        properties.setExpirationCheckInterval(Duration.ofSeconds(45));
        properties.setTaskSyncInterval(Duration.ofSeconds(90));

        // Then
        assertFalse(properties.isEnabled());
        assertEquals(Duration.ofSeconds(60), properties.getDefaultInterval());
        assertEquals(Duration.ofSeconds(10), properties.getDefaultTimeout());
        assertEquals(5, properties.getMaxRetry());
        assertEquals(Duration.ofSeconds(120), properties.getInstanceExpirationThreshold());
        assertEquals(20, properties.getThreadPoolSize());
        assertEquals(Duration.ofSeconds(45), properties.getExpirationCheckInterval());
        assertEquals(Duration.ofSeconds(90), properties.getTaskSyncInterval());
    }

    @Test
    void testToString() {
        // Given
        HealthCheckProperties properties = new HealthCheckProperties();

        // When
        String result = properties.toString();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("enabled=true"));
        assertTrue(result.contains("defaultInterval=PT30S"));
        assertTrue(result.contains("defaultTimeout=PT5S"));
        assertTrue(result.contains("maxRetry=3"));
    }
}