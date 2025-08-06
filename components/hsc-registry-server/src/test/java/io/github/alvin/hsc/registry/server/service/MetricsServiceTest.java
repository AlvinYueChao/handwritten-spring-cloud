package io.github.alvin.hsc.registry.server.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Metrics Service Test
 * 监控指标服务测试
 * 
 * @author Alvin
 */
class MetricsServiceTest {

    private MetricsService metricsService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    void testIncrementRegistrations() {
        // 初始值应该为0
        assertEquals(0.0, meterRegistry.counter("registry.service.registrations.total").count());
        
        // 增加注册计数
        metricsService.incrementRegistrations();
        
        // 验证计数增加
        assertEquals(1.0, meterRegistry.counter("registry.service.registrations.total").count());
        assertTrue(metricsService.getLastRegistrationTime() > 0);
    }

    @Test
    void testIncrementDeregistrations() {
        metricsService.incrementDeregistrations();
        assertEquals(1.0, meterRegistry.counter("registry.service.deregistrations.total").count());
    }

    @Test
    void testIncrementDiscoveries() {
        metricsService.incrementDiscoveries();
        assertEquals(1.0, meterRegistry.counter("registry.service.discoveries.total").count());
    }

    @Test
    void testIncrementHealthChecks() {
        metricsService.incrementHealthChecks();
        assertEquals(1.0, meterRegistry.counter("registry.health.checks.total").count());
    }

    @Test
    void testIncrementHealthCheckFailures() {
        metricsService.incrementHealthCheckFailures();
        assertEquals(1.0, meterRegistry.counter("registry.health.checks.failures.total").count());
    }

    @Test
    void testRegistrationTimer() {
        Timer.Sample sample = metricsService.startRegistrationTimer();
        
        // 模拟一些处理时间
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        metricsService.recordRegistrationTime(sample);
        
        Timer timer = meterRegistry.timer("registry.service.registration.duration");
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void testDiscoveryTimer() {
        Timer.Sample sample = metricsService.startDiscoveryTimer();
        
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        metricsService.recordDiscoveryTime(sample);
        
        Timer timer = meterRegistry.timer("registry.service.discovery.duration");
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void testHealthCheckTimer() {
        Timer.Sample sample = metricsService.startHealthCheckTimer();
        
        try {
            Thread.sleep(3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        metricsService.recordHealthCheckTime(sample);
        
        Timer timer = meterRegistry.timer("registry.health.check.duration");
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void testUpdateGaugeMetrics() {
        // 更新仪表指标
        metricsService.updateTotalServices(5);
        metricsService.updateTotalInstances(15);
        metricsService.updateHealthyInstances(12);
        metricsService.updateUnhealthyInstances(3);
        
        // 验证仪表值
        assertEquals(5.0, metricsService.getTotalServices());
        assertEquals(15.0, metricsService.getTotalInstances());
        assertEquals(12.0, metricsService.getHealthyInstances());
        assertEquals(3.0, metricsService.getUnhealthyInstances());
    }

    @Test
    void testUpdateLastHeartbeatTime() {
        long beforeTime = System.currentTimeMillis();
        metricsService.updateLastHeartbeatTime();
        long afterTime = System.currentTimeMillis();
        
        double heartbeatTime = metricsService.getLastHeartbeatTime();
        assertTrue(heartbeatTime >= beforeTime && heartbeatTime <= afterTime);
    }

    @Test
    void testCustomMetrics() {
        // 测试自定义指标
        metricsService.recordCustomMetric("test.custom.gauge", "Test custom gauge", 42.0);
        metricsService.incrementCustomCounter("test.custom.counter", "Test custom counter");
        
        // 验证自定义指标存在
        assertNotNull(meterRegistry.find("test.custom.gauge").gauge());
        assertNotNull(meterRegistry.find("test.custom.counter").counter());
        assertEquals(1.0, meterRegistry.counter("test.custom.counter").count());
    }

    @Test
    void testGaugeRegistration() {
        // 验证仪表指标已注册
        assertNotNull(meterRegistry.find("registry.services.total").gauge());
        assertNotNull(meterRegistry.find("registry.instances.total").gauge());
        assertNotNull(meterRegistry.find("registry.instances.healthy").gauge());
        assertNotNull(meterRegistry.find("registry.instances.unhealthy").gauge());
        assertNotNull(meterRegistry.find("registry.last.registration.timestamp").gauge());
        assertNotNull(meterRegistry.find("registry.last.heartbeat.timestamp").gauge());
    }
}