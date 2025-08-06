package io.github.alvin.hsc.registry.server.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics Service
 * 监控指标服务 - 收集和管理自定义业务指标
 * 
 * @author Alvin
 */
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    
    // 计数器指标
    private final Counter registrationCounter;
    private final Counter deregistrationCounter;
    private final Counter discoveryCounter;
    private final Counter healthCheckCounter;
    private final Counter healthCheckFailureCounter;
    
    // 计时器指标
    private final Timer registrationTimer;
    private final Timer discoveryTimer;
    private final Timer healthCheckTimer;
    
    // 仪表指标
    private final AtomicInteger totalServices = new AtomicInteger(0);
    private final AtomicInteger totalInstances = new AtomicInteger(0);
    private final AtomicInteger healthyInstances = new AtomicInteger(0);
    private final AtomicInteger unhealthyInstances = new AtomicInteger(0);
    private final AtomicLong lastRegistrationTime = new AtomicLong(0);
    private final AtomicLong lastHeartbeatTime = new AtomicLong(0);

    @Autowired
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化计数器
        this.registrationCounter = Counter.builder("registry.service.registrations.total")
            .description("Total number of service registrations")
            .register(meterRegistry);
            
        this.deregistrationCounter = Counter.builder("registry.service.deregistrations.total")
            .description("Total number of service deregistrations")
            .register(meterRegistry);
            
        this.discoveryCounter = Counter.builder("registry.service.discoveries.total")
            .description("Total number of service discovery requests")
            .register(meterRegistry);
            
        this.healthCheckCounter = Counter.builder("registry.health.checks.total")
            .description("Total number of health checks performed")
            .register(meterRegistry);
            
        this.healthCheckFailureCounter = Counter.builder("registry.health.checks.failures.total")
            .description("Total number of failed health checks")
            .register(meterRegistry);
        
        // 初始化计时器
        this.registrationTimer = Timer.builder("registry.service.registration.duration")
            .description("Time taken to register a service")
            .register(meterRegistry);
            
        this.discoveryTimer = Timer.builder("registry.service.discovery.duration")
            .description("Time taken to discover services")
            .register(meterRegistry);
            
        this.healthCheckTimer = Timer.builder("registry.health.check.duration")
            .description("Time taken to perform health checks")
            .register(meterRegistry);
        
        // 初始化仪表
        Gauge.builder("registry.services.total", this, MetricsService::getTotalServices)
            .description("Total number of registered services")
            .register(meterRegistry);
            
        Gauge.builder("registry.instances.total", this, MetricsService::getTotalInstances)
            .description("Total number of service instances")
            .register(meterRegistry);
            
        Gauge.builder("registry.instances.healthy", this, MetricsService::getHealthyInstances)
            .description("Number of healthy service instances")
            .register(meterRegistry);
            
        Gauge.builder("registry.instances.unhealthy", this, MetricsService::getUnhealthyInstances)
            .description("Number of unhealthy service instances")
            .register(meterRegistry);
            
        Gauge.builder("registry.last.registration.timestamp", this, MetricsService::getLastRegistrationTime)
            .description("Timestamp of last service registration")
            .register(meterRegistry);
            
        Gauge.builder("registry.last.heartbeat.timestamp", this, MetricsService::getLastHeartbeatTime)
            .description("Timestamp of last heartbeat received")
            .register(meterRegistry);
    }

    // 计数器操作方法
    public void incrementRegistrations() {
        registrationCounter.increment();
        lastRegistrationTime.set(System.currentTimeMillis());
    }

    public void incrementDeregistrations() {
        deregistrationCounter.increment();
    }

    public void incrementDiscoveries() {
        discoveryCounter.increment();
    }

    public void incrementHealthChecks() {
        healthCheckCounter.increment();
    }

    public void incrementHealthCheckFailures() {
        healthCheckFailureCounter.increment();
    }

    // 计时器操作方法
    public Timer.Sample startRegistrationTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordRegistrationTime(Timer.Sample sample) {
        sample.stop(registrationTimer);
    }

    public Timer.Sample startDiscoveryTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordDiscoveryTime(Timer.Sample sample) {
        sample.stop(discoveryTimer);
    }

    public Timer.Sample startHealthCheckTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordHealthCheckTime(Timer.Sample sample) {
        sample.stop(healthCheckTimer);
    }

    // 仪表更新方法
    public void updateTotalServices(int count) {
        totalServices.set(count);
    }

    public void updateTotalInstances(int count) {
        totalInstances.set(count);
    }

    public void updateHealthyInstances(int count) {
        healthyInstances.set(count);
    }

    public void updateUnhealthyInstances(int count) {
        unhealthyInstances.set(count);
    }

    public void updateLastHeartbeatTime() {
        lastHeartbeatTime.set(System.currentTimeMillis());
    }

    // 仪表读取方法
    public double getTotalServices() {
        return totalServices.get();
    }

    public double getTotalInstances() {
        return totalInstances.get();
    }

    public double getHealthyInstances() {
        return healthyInstances.get();
    }

    public double getUnhealthyInstances() {
        return unhealthyInstances.get();
    }

    public double getLastRegistrationTime() {
        return lastRegistrationTime.get();
    }

    public double getLastHeartbeatTime() {
        return lastHeartbeatTime.get();
    }

    // 自定义指标方法
    public void recordCustomMetric(String name, String description, double value) {
        Gauge.builder(name, () -> value)
            .description(description)
            .register(meterRegistry);
    }

    public void incrementCustomCounter(String name, String description) {
        Counter.builder(name)
            .description(description)
            .register(meterRegistry)
            .increment();
    }
}