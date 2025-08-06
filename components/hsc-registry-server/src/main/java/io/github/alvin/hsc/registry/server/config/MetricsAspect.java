package io.github.alvin.hsc.registry.server.config;

import io.github.alvin.hsc.registry.server.service.MetricsService;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Metrics Aspect
 * 监控指标切面 - 自动收集服务操作的监控指标
 * 
 * @author Alvin
 */
@Aspect
@Component
public class MetricsAspect {

    private final MetricsService metricsService;

    @Autowired
    public MetricsAspect(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * 监控服务注册操作
     * 需求: 6.4, 7.2
     */
    @Around("execution(* io.github.alvin.hsc.registry.server.service.RegistryService.register(..))")
    public Object monitorRegistration(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metricsService.startRegistrationTimer();
        try {
            Object result = joinPoint.proceed();
            metricsService.incrementRegistrations();
            return result;
        } catch (Exception e) {
            // 注册失败时也记录指标
            throw e;
        } finally {
            metricsService.recordRegistrationTime(sample);
        }
    }

    /**
     * 监控服务注销操作
     * 需求: 6.4, 7.2
     */
    @Around("execution(* io.github.alvin.hsc.registry.server.service.RegistryService.deregister(..))")
    public Object monitorDeregistration(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            metricsService.incrementDeregistrations();
            return result;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 监控服务发现操作
     * 需求: 6.4, 7.2
     */
    @Around("execution(* io.github.alvin.hsc.registry.server.service.DiscoveryService.discover*(..))")
    public Object monitorDiscovery(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metricsService.startDiscoveryTimer();
        try {
            Object result = joinPoint.proceed();
            metricsService.incrementDiscoveries();
            return result;
        } catch (Exception e) {
            throw e;
        } finally {
            metricsService.recordDiscoveryTime(sample);
        }
    }

    /**
     * 监控健康检查操作
     * 需求: 6.4, 7.2
     */
    @Around("execution(* io.github.alvin.hsc.registry.server.service.HealthCheckService.checkHealth(..))")
    public Object monitorHealthCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metricsService.startHealthCheckTimer();
        try {
            Object result = joinPoint.proceed();
            metricsService.incrementHealthChecks();
            return result;
        } catch (Exception e) {
            metricsService.incrementHealthCheckFailures();
            throw e;
        } finally {
            metricsService.recordHealthCheckTime(sample);
        }
    }

    /**
     * 监控心跳续约操作
     * 需求: 6.4, 7.2
     */
    @Around("execution(* io.github.alvin.hsc.registry.server.service.RegistryService.renew(..))")
    public Object monitorHeartbeat(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            metricsService.updateLastHeartbeatTime();
            return result;
        } catch (Exception e) {
            throw e;
        }
    }
}