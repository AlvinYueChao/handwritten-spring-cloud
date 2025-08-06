package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.service.MetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Metrics Controller
 * 监控指标控制器 - 提供自定义监控指标的REST接口
 * 
 * @author Alvin
 */
@RestController
@RequestMapping("/api/v1/metrics")
@CrossOrigin(origins = "*")
public class MetricsController {

    private final MetricsService metricsService;
    private final MeterRegistry meterRegistry;

    @Autowired
    public MetricsController(MetricsService metricsService, MeterRegistry meterRegistry) {
        this.metricsService = metricsService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 获取注册中心核心指标
     * 需求: 6.4, 7.2
     */
    @GetMapping("/registry")
    public Mono<ResponseEntity<Map<String, Object>>> getRegistryMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 服务和实例统计
        metrics.put("totalServices", metricsService.getTotalServices());
        metrics.put("totalInstances", metricsService.getTotalInstances());
        metrics.put("healthyInstances", metricsService.getHealthyInstances());
        metrics.put("unhealthyInstances", metricsService.getUnhealthyInstances());
        
        // 时间戳信息
        metrics.put("lastRegistrationTime", metricsService.getLastRegistrationTime());
        metrics.put("lastHeartbeatTime", metricsService.getLastHeartbeatTime());
        
        // 操作计数
        Map<String, Double> counters = new HashMap<>();
        meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().startsWith("registry."))
            .filter(meter -> meter instanceof io.micrometer.core.instrument.Counter)
            .forEach(meter -> {
                counters.put(meter.getId().getName(), 
                    ((io.micrometer.core.instrument.Counter) meter).count());
            });
        metrics.put("counters", counters);
        
        return Mono.just(ResponseEntity.ok(metrics));
    }

    /**
     * 获取性能指标
     * 需求: 7.2
     */
    @GetMapping("/performance")
    public Mono<ResponseEntity<Map<String, Object>>> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 计时器指标
        Map<String, Map<String, Double>> timers = new HashMap<>();
        meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().startsWith("registry."))
            .filter(meter -> meter instanceof io.micrometer.core.instrument.Timer)
            .forEach(meter -> {
                io.micrometer.core.instrument.Timer timer = (io.micrometer.core.instrument.Timer) meter;
                Map<String, Double> timerStats = new HashMap<>();
                timerStats.put("count", (double) timer.count());
                timerStats.put("totalTime", timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
                timerStats.put("mean", timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
                timerStats.put("max", timer.max(java.util.concurrent.TimeUnit.MILLISECONDS));
                timers.put(meter.getId().getName(), timerStats);
            });
        metrics.put("timers", timers);
        
        return Mono.just(ResponseEntity.ok(metrics));
    }

    /**
     * 获取所有可用指标的名称列表
     * 需求: 6.4
     */
    @GetMapping("/names")
    public Mono<ResponseEntity<Map<String, Object>>> getMetricNames() {
        Map<String, Object> response = new HashMap<>();
        
        // 按类型分组的指标名称
        Map<String, java.util.List<String>> metricsByType = meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().startsWith("registry."))
            .collect(Collectors.groupingBy(
                meter -> meter.getClass().getSimpleName(),
                Collectors.mapping(meter -> meter.getId().getName(), Collectors.toList())
            ));
        
        response.put("metricsByType", metricsByType);
        response.put("totalMetrics", meterRegistry.getMeters().size());
        
        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * 获取指定指标的详细信息
     * 需求: 6.4
     */
    @GetMapping("/{metricName}")
    public Mono<ResponseEntity<Map<String, Object>>> getMetricDetails(@PathVariable String metricName) {
        return meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().equals(metricName))
            .findFirst()
            .map(meter -> {
                Map<String, Object> details = new HashMap<>();
                details.put("name", meter.getId().getName());
                details.put("description", meter.getId().getDescription());
                details.put("type", meter.getClass().getSimpleName());
                details.put("tags", meter.getId().getTags());
                
                // 根据指标类型添加特定信息
                if (meter instanceof io.micrometer.core.instrument.Counter) {
                    io.micrometer.core.instrument.Counter counter = (io.micrometer.core.instrument.Counter) meter;
                    details.put("count", counter.count());
                } else if (meter instanceof io.micrometer.core.instrument.Gauge) {
                    io.micrometer.core.instrument.Gauge gauge = (io.micrometer.core.instrument.Gauge) meter;
                    details.put("value", gauge.value());
                } else if (meter instanceof io.micrometer.core.instrument.Timer) {
                    io.micrometer.core.instrument.Timer timer = (io.micrometer.core.instrument.Timer) meter;
                    details.put("count", timer.count());
                    details.put("totalTime", timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
                    details.put("mean", timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
                    details.put("max", timer.max(java.util.concurrent.TimeUnit.MILLISECONDS));
                }
                
                return ResponseEntity.ok(details);
            })
            .map(Mono::just)
            .orElse(Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * 重置指定计数器指标
     * 需求: 6.3
     */
    @PostMapping("/{metricName}/reset")
    public Mono<ResponseEntity<Map<String, Object>>> resetMetric(@PathVariable String metricName) {
        // 注意：Micrometer的计数器通常不支持重置，这里提供一个操作确认
        Map<String, Object> response = new HashMap<>();
        response.put("metricName", metricName);
        response.put("operation", "reset");
        response.put("status", "acknowledged");
        response.put("message", "Reset operation acknowledged. Note: Most metrics cannot be reset in Micrometer.");
        response.put("timestamp", System.currentTimeMillis());
        
        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * 获取健康相关指标
     * 需求: 7.2
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> getHealthMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 健康检查相关指标
        meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().contains("health"))
            .forEach(meter -> {
                if (meter instanceof io.micrometer.core.instrument.Counter) {
                    io.micrometer.core.instrument.Counter counter = (io.micrometer.core.instrument.Counter) meter;
                    metrics.put(meter.getId().getName(), counter.count());
                } else if (meter instanceof io.micrometer.core.instrument.Timer) {
                    io.micrometer.core.instrument.Timer timer = (io.micrometer.core.instrument.Timer) meter;
                    Map<String, Double> timerStats = new HashMap<>();
                    timerStats.put("count", (double) timer.count());
                    timerStats.put("mean", timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
                    metrics.put(meter.getId().getName(), timerStats);
                }
            });
        
        // 实例健康状态统计
        metrics.put("healthyInstances", metricsService.getHealthyInstances());
        metrics.put("unhealthyInstances", metricsService.getUnhealthyInstances());
        
        return Mono.just(ResponseEntity.ok(metrics));
    }
}