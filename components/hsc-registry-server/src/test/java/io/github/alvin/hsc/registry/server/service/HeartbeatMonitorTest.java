package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.repository.RegistryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HeartbeatMonitor
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class HeartbeatMonitorTest {
    
    @Mock
    private RegistryStorage registryStorage;
    
    @Mock
    private InstanceLifecycleManager lifecycleManager;
    
    private HeartbeatMonitor heartbeatMonitor;
    
    @BeforeEach
    void setUp() {
        heartbeatMonitor = new HeartbeatMonitor(registryStorage, lifecycleManager);
    }
    
    @Test
    void checkHeartbeatTimeouts_WithHealthyStorage_ShouldProcessInstances() {
        // Given
        when(registryStorage.isHealthy()).thenReturn(true);
        
        ServiceInstance healthyInstance = createServiceInstance("service-1", "instance-1");
        healthyInstance.setLastHeartbeat(Instant.now()); // Recent heartbeat
        
        ServiceInstance timeoutInstance = createServiceInstance("service-1", "instance-2");
        timeoutInstance.setLastHeartbeat(Instant.now().minus(Duration.ofMinutes(2))); // Old heartbeat
        
        Map<String, List<ServiceInstance>> allInstances = new HashMap<>();
        allInstances.put("service-1", Arrays.asList(healthyInstance, timeoutInstance));
        
        when(registryStorage.getAllInstances()).thenReturn(allInstances);
        when(lifecycleManager.isHeartbeatTimeout(eq(healthyInstance), any(Duration.class))).thenReturn(false);
        when(lifecycleManager.isHeartbeatTimeout(eq(timeoutInstance), any(Duration.class))).thenReturn(true);
        
        // When
        heartbeatMonitor.checkHeartbeatTimeouts();
        
        // Then
        verify(registryStorage).isHealthy();
        verify(registryStorage).getAllInstances();
        verify(lifecycleManager).isHeartbeatTimeout(eq(healthyInstance), any(Duration.class));
        verify(lifecycleManager).isHeartbeatTimeout(eq(timeoutInstance), any(Duration.class));
        verify(lifecycleManager).handleHeartbeatTimeout(timeoutInstance);
        verify(lifecycleManager, never()).handleHeartbeatTimeout(healthyInstance);
    }
    
    @Test
    void checkHeartbeatTimeouts_WithUnhealthyStorage_ShouldSkip() {
        // Given
        when(registryStorage.isHealthy()).thenReturn(false);
        
        // When
        heartbeatMonitor.checkHeartbeatTimeouts();
        
        // Then
        verify(registryStorage).isHealthy();
        verify(registryStorage, never()).getAllInstances();
        verify(lifecycleManager, never()).isHeartbeatTimeout(any(), any());
        verify(lifecycleManager, never()).handleHeartbeatTimeout(any());
    }
    
    @Test
    void checkHeartbeatTimeouts_WithEmptyRegistry_ShouldComplete() {
        // Given
        when(registryStorage.isHealthy()).thenReturn(true);
        when(registryStorage.getAllInstances()).thenReturn(new HashMap<>());
        
        // When
        heartbeatMonitor.checkHeartbeatTimeouts();
        
        // Then
        verify(registryStorage).isHealthy();
        verify(registryStorage).getAllInstances();
        verify(lifecycleManager, never()).isHeartbeatTimeout(any(), any());
        verify(lifecycleManager, never()).handleHeartbeatTimeout(any());
    }
    
    @Test
    void checkHeartbeatTimeouts_WithException_ShouldHandleGracefully() {
        // Given
        when(registryStorage.isHealthy()).thenReturn(true);
        when(registryStorage.getAllInstances()).thenThrow(new RuntimeException("Storage error"));
        
        // When
        heartbeatMonitor.checkHeartbeatTimeouts();
        
        // Then
        verify(registryStorage).isHealthy();
        verify(registryStorage).getAllInstances();
        // Should not propagate exception
    }
    
    @Test
    void performHeartbeatCheck_ShouldReturnCorrectResult() {
        // Given
        ServiceInstance healthyInstance = createServiceInstance("service-1", "instance-1");
        ServiceInstance timeoutInstance1 = createServiceInstance("service-1", "instance-2");
        ServiceInstance timeoutInstance2 = createServiceInstance("service-2", "instance-3");
        
        Map<String, List<ServiceInstance>> allInstances = new HashMap<>();
        allInstances.put("service-1", Arrays.asList(healthyInstance, timeoutInstance1));
        allInstances.put("service-2", Collections.singletonList(timeoutInstance2));
        
        when(registryStorage.getAllInstances()).thenReturn(allInstances);
        when(lifecycleManager.isHeartbeatTimeout(eq(healthyInstance), any(Duration.class))).thenReturn(false);
        when(lifecycleManager.isHeartbeatTimeout(eq(timeoutInstance1), any(Duration.class))).thenReturn(true);
        when(lifecycleManager.isHeartbeatTimeout(eq(timeoutInstance2), any(Duration.class))).thenReturn(true);
        
        // When
        HeartbeatMonitor.HeartbeatCheckResult result = heartbeatMonitor.performHeartbeatCheck();
        
        // Then
        assertThat(result.checkedInstances()).isEqualTo(3);
        assertThat(result.timeoutInstances()).isEqualTo(2);
        
        verify(lifecycleManager).handleHeartbeatTimeout(timeoutInstance1);
        verify(lifecycleManager).handleHeartbeatTimeout(timeoutInstance2);
        verify(lifecycleManager, never()).handleHeartbeatTimeout(healthyInstance);
    }
    
    @Test
    void performHeartbeatCheck_WithNoTimeouts_ShouldReturnZeroTimeouts() {
        // Given
        ServiceInstance healthyInstance1 = createServiceInstance("service-1", "instance-1");
        ServiceInstance healthyInstance2 = createServiceInstance("service-1", "instance-2");
        
        Map<String, List<ServiceInstance>> allInstances = new HashMap<>();
        allInstances.put("service-1", Arrays.asList(healthyInstance1, healthyInstance2));
        
        when(registryStorage.getAllInstances()).thenReturn(allInstances);
        when(lifecycleManager.isHeartbeatTimeout(any(ServiceInstance.class), any(Duration.class))).thenReturn(false);
        
        // When
        HeartbeatMonitor.HeartbeatCheckResult result = heartbeatMonitor.performHeartbeatCheck();
        
        // Then
        assertThat(result.checkedInstances()).isEqualTo(2);
        assertThat(result.timeoutInstances()).isEqualTo(0);
        
        verify(lifecycleManager, never()).handleHeartbeatTimeout(any());
    }
    
    @Test
    void getStats_ShouldReturnCorrectStatistics() {
        // When
        HeartbeatMonitor.HeartbeatMonitorStats stats = heartbeatMonitor.getStats();
        
        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.totalChecks()).isEqualTo(0);
        assertThat(stats.totalTimeouts()).isEqualTo(0);
        assertThat(stats.heartbeatTimeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(stats.getTimeoutRate()).isEqualTo(0.0);
    }
    
    @Test
    void resetStats_ShouldResetCounters() {
        // When
        heartbeatMonitor.resetStats();
        HeartbeatMonitor.HeartbeatMonitorStats stats = heartbeatMonitor.getStats();
        
        // Then
        assertThat(stats.totalChecks()).isEqualTo(0);
        assertThat(stats.totalTimeouts()).isEqualTo(0);
    }
    
    @Test
    void heartbeatCheckResult_ToString_ShouldFormatCorrectly() {
        // Given
        HeartbeatMonitor.HeartbeatCheckResult result = new HeartbeatMonitor.HeartbeatCheckResult(10, 3);
        
        // When
        String toString = result.toString();
        
        // Then
        assertThat(toString).contains("checked=10");
        assertThat(toString).contains("timeouts=3");
    }
    
    @Test
    void heartbeatMonitorStats_ToString_ShouldFormatCorrectly() {
        // Given
        HeartbeatMonitor.HeartbeatMonitorStats stats = new HeartbeatMonitor.HeartbeatMonitorStats(
            100, 5, Duration.ofSeconds(90));
        
        // When
        String toString = stats.toString();
        
        // Then
        assertThat(toString).contains("checks=100");
        assertThat(toString).contains("timeouts=5");
        assertThat(toString).contains("rate=5.00%");
        assertThat(toString).contains("timeout=90s");
    }
    
    @Test
    void heartbeatMonitorStats_TimeoutRate_ShouldCalculateCorrectly() {
        // Given
        HeartbeatMonitor.HeartbeatMonitorStats stats1 = new HeartbeatMonitor.HeartbeatMonitorStats(
            100, 10, Duration.ofSeconds(90));
        HeartbeatMonitor.HeartbeatMonitorStats stats2 = new HeartbeatMonitor.HeartbeatMonitorStats(
            0, 0, Duration.ofSeconds(90));
        
        // Then
        assertThat(stats1.getTimeoutRate()).isEqualTo(0.1);
        assertThat(stats2.getTimeoutRate()).isEqualTo(0.0);
    }
    
    private ServiceInstance createServiceInstance(String serviceId, String instanceId) {
        ServiceInstance instance = new ServiceInstance(serviceId, instanceId, "localhost", 8080);
        instance.setStatus(InstanceStatus.UP);
        instance.setLastHeartbeat(Instant.now());
        return instance;
    }
}