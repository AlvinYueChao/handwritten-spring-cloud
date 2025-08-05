package io.github.alvin.hsc.registry.server.service.impl;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceCatalog;
import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.model.ServiceEventType;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.repository.RegistryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DiscoveryServiceImpl
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryServiceImplTest {
    
    @Mock
    private RegistryStorage registryStorage;
    
    private DiscoveryServiceImpl discoveryService;
    
    private ServiceInstance healthyInstance1;
    private ServiceInstance healthyInstance2;
    private ServiceInstance unhealthyInstance;
    
    @BeforeEach
    void setUp() {
        discoveryService = new DiscoveryServiceImpl(registryStorage);
        
        // 创建测试数据
        healthyInstance1 = new ServiceInstance("test-service", "instance-1", "192.168.1.100", 8080);
        healthyInstance1.setStatus(InstanceStatus.UP);
        
        healthyInstance2 = new ServiceInstance("test-service", "instance-2", "192.168.1.101", 8080);
        healthyInstance2.setStatus(InstanceStatus.UP);
        
        unhealthyInstance = new ServiceInstance("test-service", "instance-3", "192.168.1.102", 8080);
        unhealthyInstance.setStatus(InstanceStatus.DOWN);
    }
    
    @Test
    void discover_ShouldReturnAllInstances_WhenServiceExists() {
        // Given
        String serviceId = "test-service";
        List<ServiceInstance> instances = Arrays.asList(healthyInstance1, healthyInstance2, unhealthyInstance);
        when(registryStorage.getInstances(serviceId)).thenReturn(instances);
        
        // When & Then
        StepVerifier.create(discoveryService.discover(serviceId))
                .expectNext(healthyInstance1)
                .expectNext(healthyInstance2)
                .expectNext(unhealthyInstance)
                .verifyComplete();
        
        verify(registryStorage).getInstances(serviceId);
    }
    
    @Test
    void discover_ShouldReturnEmpty_WhenServiceNotExists() {
        // Given
        String serviceId = "non-existent-service";
        when(registryStorage.getInstances(serviceId)).thenReturn(Collections.emptyList());
        
        // When & Then
        StepVerifier.create(discoveryService.discover(serviceId))
                .verifyComplete();
        
        verify(registryStorage).getInstances(serviceId);
    }
    
    @Test
    void discover_ShouldThrowException_WhenServiceIdIsNull() {
        // When & Then
        StepVerifier.create(discoveryService.discover(null))
                .expectError(IllegalArgumentException.class)
                .verify();
        
        verifyNoInteractions(registryStorage);
    }
    
    @Test
    void discover_ShouldThrowException_WhenServiceIdIsEmpty() {
        // When & Then
        StepVerifier.create(discoveryService.discover(""))
                .expectError(IllegalArgumentException.class)
                .verify();
        
        verifyNoInteractions(registryStorage);
    }
    
    @Test
    void discover_ShouldThrowException_WhenServiceIdContainsInvalidCharacters() {
        // When & Then
        StepVerifier.create(discoveryService.discover("invalid@service"))
                .expectError(IllegalArgumentException.class)
                .verify();
        
        verifyNoInteractions(registryStorage);
    }
    
    @Test
    void discoverHealthy_ShouldReturnOnlyHealthyInstances_WhenServiceExists() {
        // Given
        String serviceId = "test-service";
        List<ServiceInstance> healthyInstances = Arrays.asList(healthyInstance1, healthyInstance2);
        when(registryStorage.getHealthyInstances(serviceId)).thenReturn(healthyInstances);
        
        // When & Then
        StepVerifier.create(discoveryService.discoverHealthy(serviceId))
                .expectNext(healthyInstance1)
                .expectNext(healthyInstance2)
                .verifyComplete();
        
        verify(registryStorage).getHealthyInstances(serviceId);
    }
    
    @Test
    void discoverHealthy_ShouldReturnEmpty_WhenNoHealthyInstances() {
        // Given
        String serviceId = "test-service";
        when(registryStorage.getHealthyInstances(serviceId)).thenReturn(Collections.emptyList());
        
        // When & Then
        StepVerifier.create(discoveryService.discoverHealthy(serviceId))
                .verifyComplete();
        
        verify(registryStorage).getHealthyInstances(serviceId);
    }
    
    @Test
    void discoverHealthy_ShouldThrowException_WhenServiceIdIsNull() {
        // When & Then
        StepVerifier.create(discoveryService.discoverHealthy(null))
                .expectError(IllegalArgumentException.class)
                .verify();
        
        verifyNoInteractions(registryStorage);
    }
    
    @Test
    void getCatalog_ShouldReturnServiceCatalog_WhenServicesExist() {
        // Given
        Map<String, List<ServiceInstance>> allInstances = new HashMap<>();
        allInstances.put("service-1", Arrays.asList(healthyInstance1));
        allInstances.put("service-2", Arrays.asList(healthyInstance2, unhealthyInstance));
        
        when(registryStorage.getAllInstances()).thenReturn(allInstances);
        
        // When & Then
        StepVerifier.create(discoveryService.getCatalog())
                .assertNext(catalog -> {
                    assertThat(catalog).isNotNull();
                    assertThat(catalog.getTotalServices()).isEqualTo(2);
                    assertThat(catalog.getTotalInstances()).isEqualTo(3);
                    assertThat(catalog.getServices()).hasSize(2);
                    assertThat(catalog.getServices().get("service-1")).hasSize(1);
                    assertThat(catalog.getServices().get("service-2")).hasSize(2);
                })
                .verifyComplete();
        
        verify(registryStorage).getAllInstances();
    }
    
    @Test
    void getCatalog_ShouldReturnEmptyCatalog_WhenNoServicesExist() {
        // Given
        when(registryStorage.getAllInstances()).thenReturn(Collections.emptyMap());
        
        // When & Then
        StepVerifier.create(discoveryService.getCatalog())
                .assertNext(catalog -> {
                    assertThat(catalog).isNotNull();
                    assertThat(catalog.getTotalServices()).isEqualTo(0);
                    assertThat(catalog.getTotalInstances()).isEqualTo(0);
                    assertThat(catalog.getServices()).isEmpty();
                })
                .verifyComplete();
        
        verify(registryStorage).getAllInstances();
    }
    
    @Test
    void getCatalog_ShouldFilterEmptyServiceLists() {
        // Given
        Map<String, List<ServiceInstance>> allInstances = new HashMap<>();
        allInstances.put("service-1", Arrays.asList(healthyInstance1));
        allInstances.put("service-2", Collections.emptyList());
        allInstances.put("service-3", null);
        
        when(registryStorage.getAllInstances()).thenReturn(allInstances);
        
        // When & Then
        StepVerifier.create(discoveryService.getCatalog())
                .assertNext(catalog -> {
                    assertThat(catalog).isNotNull();
                    assertThat(catalog.getTotalServices()).isEqualTo(1);
                    assertThat(catalog.getTotalInstances()).isEqualTo(1);
                    assertThat(catalog.getServices()).hasSize(1);
                    assertThat(catalog.getServices()).containsKey("service-1");
                })
                .verifyComplete();
        
        verify(registryStorage).getAllInstances();
    }
    
    @Test
    void watchService_ShouldReturnEventStream_WhenServiceIdIsValid() {
        // Given
        String serviceId = "test-service";
        
        // When
        Flux<ServiceEvent> eventStream = discoveryService.watchService(serviceId);
        
        // Then
        StepVerifier.create(eventStream.take(Duration.ofMillis(100)))
                .verifyComplete();
    }
    
    @Test
    void watchService_ShouldThrowException_WhenServiceIdIsNull() {
        // When & Then
        StepVerifier.create(discoveryService.watchService(null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
    
    @Test
    void watchService_ShouldThrowException_WhenServiceIdIsEmpty() {
        // When & Then
        StepVerifier.create(discoveryService.watchService(""))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
    
    @Test
    void handleServiceEvent_ShouldPushEventToWatchers() {
        // Given
        String serviceId = "test-service";
        ServiceEvent event = new ServiceEvent(ServiceEventType.REGISTER, healthyInstance1);
        
        // 先创建一个观察者
        Flux<ServiceEvent> eventStream = discoveryService.watchService(serviceId);
        
        // When & Then
        StepVerifier.create(eventStream.take(1))
                .then(() -> {
                    // 模拟事件发生
                    discoveryService.handleServiceEvent(event);
                })
                .expectNext(event)
                .verifyComplete();
    }
    
    @Test
    void handleServiceEvent_ShouldIgnoreNullEvent() {
        // Given
        String serviceId = "test-service";
        
        // 先创建一个观察者
        Flux<ServiceEvent> eventStream = discoveryService.watchService(serviceId);
        
        // When & Then
        StepVerifier.create(eventStream.take(Duration.ofMillis(100)))
                .then(() -> {
                    // 发送null事件
                    discoveryService.handleServiceEvent(null);
                })
                .verifyComplete();
    }
    
    @Test
    void handleServiceEvent_ShouldIgnoreEventWithNullServiceId() {
        // Given
        String serviceId = "test-service";
        ServiceEvent event = new ServiceEvent();
        event.setType(ServiceEventType.REGISTER);
        event.setServiceId(null);
        
        // 先创建一个观察者
        Flux<ServiceEvent> eventStream = discoveryService.watchService(serviceId);
        
        // When & Then
        StepVerifier.create(eventStream.take(Duration.ofMillis(100)))
                .then(() -> {
                    // 发送serviceId为null的事件
                    discoveryService.handleServiceEvent(event);
                })
                .verifyComplete();
    }
}