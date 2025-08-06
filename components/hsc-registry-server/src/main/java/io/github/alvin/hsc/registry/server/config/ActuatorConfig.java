package io.github.alvin.hsc.registry.server.config;

import io.github.alvin.hsc.registry.server.service.DiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator Configuration
 * Actuator 配置 - 配置健康检查和监控端点
 * 
 * @author Alvin
 */
@Configuration
public class ActuatorConfig {

    private final DiscoveryService discoveryService;

    @Autowired
    public ActuatorConfig(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * 注册中心健康检查指示器
     * 需求: 7.2
     */
    @Bean
    public HealthIndicator registryHealthIndicator() {
        return () -> {
            try {
                // 检查服务目录是否可用
                return discoveryService.getCatalog()
                    .map(catalog -> {
                        int totalServices = catalog.getServices().size();
                        int totalInstances = catalog.getServices().values().stream()
                            .mapToInt(instances -> instances.size())
                            .sum();
                        
                        return Health.up()
                            .withDetail("status", "Registry is operational")
                            .withDetail("totalServices", totalServices)
                            .withDetail("totalInstances", totalInstances)
                            .withDetail("timestamp", System.currentTimeMillis())
                            .build();
                    })
                    .onErrorReturn(
                        Health.down()
                            .withDetail("status", "Registry service unavailable")
                            .withDetail("error", "Failed to access service catalog")
                            .withDetail("timestamp", System.currentTimeMillis())
                            .build()
                    )
                    .block(); // 阻塞获取结果，因为HealthIndicator是同步的
            } catch (Exception e) {
                return Health.down()
                    .withDetail("status", "Registry health check failed")
                    .withDetail("error", e.getMessage())
                    .withDetail("timestamp", System.currentTimeMillis())
                    .build();
            }
        };
    }

    /**
     * 存储健康检查指示器
     * 需求: 7.2
     */
    @Bean
    public HealthIndicator storageHealthIndicator() {
        return () -> {
            try {
                // 简单的存储健康检查 - 检查是否能访问服务目录
                discoveryService.getCatalog().block();
                
                return Health.up()
                    .withDetail("status", "Storage is accessible")
                    .withDetail("type", "in-memory")
                    .withDetail("timestamp", System.currentTimeMillis())
                    .build();
            } catch (Exception e) {
                return Health.down()
                    .withDetail("status", "Storage is not accessible")
                    .withDetail("error", e.getMessage())
                    .withDetail("timestamp", System.currentTimeMillis())
                    .build();
            }
        };
    }
}