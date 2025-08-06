package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.service.MetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.mockito.Mockito.when;

/**
 * Metrics Controller Test
 * 监控指标控制器测试
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class MetricsControllerTest {

    @Mock
    private MetricsService metricsService;

    private MeterRegistry meterRegistry;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        
        // 添加一些测试指标
        meterRegistry.counter("registry.service.registrations.total").increment(10);
        meterRegistry.counter("registry.service.deregistrations.total").increment(2);
        meterRegistry.timer("registry.service.registration.duration").record(Duration.ofMillis(100));
        
        MetricsController controller = new MetricsController(metricsService, meterRegistry);
        
        webTestClient = WebTestClient.bindToController(controller)
            .configureClient()
            .responseTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Test
    void testGetRegistryMetrics() {
        // Mock MetricsService 返回值
        when(metricsService.getTotalServices()).thenReturn(5.0);
        when(metricsService.getTotalInstances()).thenReturn(15.0);
        when(metricsService.getHealthyInstances()).thenReturn(12.0);
        when(metricsService.getUnhealthyInstances()).thenReturn(3.0);
        when(metricsService.getLastRegistrationTime()).thenReturn(1234567890000.0);
        when(metricsService.getLastHeartbeatTime()).thenReturn(1234567890000.0);

        webTestClient.get()
            .uri("/api/v1/metrics/registry")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalServices").isEqualTo(5.0)
            .jsonPath("$.totalInstances").isEqualTo(15.0)
            .jsonPath("$.healthyInstances").isEqualTo(12.0)
            .jsonPath("$.unhealthyInstances").isEqualTo(3.0)
            .jsonPath("$.counters").exists()
            .jsonPath("$.counters['registry.service.registrations.total']").isEqualTo(10.0);
    }

    @Test
    void testGetPerformanceMetrics() {
        webTestClient.get()
            .uri("/api/v1/metrics/performance")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.timers").exists()
            .jsonPath("$.timers['registry.service.registration.duration']").exists()
            .jsonPath("$.timers['registry.service.registration.duration'].count").isEqualTo(1.0);
    }

    @Test
    void testGetMetricNames() {
        webTestClient.get()
            .uri("/api/v1/metrics/names")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.metricsByType").exists()
            .jsonPath("$.totalMetrics").exists();
    }

    @Test
    void testGetMetricDetails() {
        webTestClient.get()
            .uri("/api/v1/metrics/registry.service.registrations.total")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("registry.service.registrations.total")
            .jsonPath("$.type").isEqualTo("CumulativeCounter")
            .jsonPath("$.count").isEqualTo(10.0);
    }

    @Test
    void testGetMetricDetailsNotFound() {
        webTestClient.get()
            .uri("/api/v1/metrics/nonexistent.metric")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void testResetMetric() {
        webTestClient.post()
            .uri("/api/v1/metrics/registry.service.registrations.total/reset")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.metricName").isEqualTo("registry.service.registrations.total")
            .jsonPath("$.operation").isEqualTo("reset")
            .jsonPath("$.status").isEqualTo("acknowledged");
    }

    @Test
    void testGetHealthMetrics() {
        when(metricsService.getHealthyInstances()).thenReturn(12.0);
        when(metricsService.getUnhealthyInstances()).thenReturn(3.0);

        webTestClient.get()
            .uri("/api/v1/metrics/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.healthyInstances").isEqualTo(12.0)
            .jsonPath("$.unhealthyInstances").isEqualTo(3.0);
    }
}