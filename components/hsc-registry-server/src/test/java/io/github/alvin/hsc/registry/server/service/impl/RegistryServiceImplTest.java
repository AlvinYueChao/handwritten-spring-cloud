package io.github.alvin.hsc.registry.server.service.impl;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.ServiceRegistration;
import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.model.ServiceEventType;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.repository.RegistryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RegistryServiceImpl
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class RegistryServiceImplTest {
    
    @Mock
    private RegistryStorage registryStorage;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    private RegistryServiceImpl registryService;
    
    @BeforeEach
    void setUp() {
        registryService = new RegistryServiceImpl(registryStorage, eventPublisher);
    }
    
    @Test
    void register_ValidRegistration_ShouldSucceed() {
        // Given
        ServiceRegistration registration = createValidRegistration();
        ServiceInstance expectedInstance = registration.toServiceInstance();
        expectedInstance.setStatus(InstanceStatus.STARTING);
        
        when(registryStorage.register(any(ServiceInstance.class))).thenReturn(expectedInstance);
        
        // When
        Mono<ServiceInstance> result = registryService.register(registration);
        
        // Then
        StepVerifier.create(result)
            .expectNext(expectedInstance)
            .verifyComplete();
        
        verify(registryStorage).register(any(ServiceInstance.class));
        verify(eventPublisher).publishEvent(any(ServiceEvent.class));
        
        // Verify event details
        ArgumentCaptor<ServiceEvent> eventCaptor = ArgumentCaptor.forClass(ServiceEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ServiceEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getType()).isEqualTo(ServiceEventType.REGISTER);
        assertThat(publishedEvent.getServiceId()).isEqualTo(registration.getServiceId());
        assertThat(publishedEvent.getInstanceId()).isEqualTo(registration.getInstanceId());
    }
    
    @Test
    void register_NullRegistration_ShouldFail() {
        // When
        Mono<ServiceInstance> result = registryService.register(null);
        
        // Then
        StepVerifier.create(result)
            .expectError(IllegalArgumentException.class)
            .verify();
        
        verify(registryStorage, never()).register(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    void register_InvalidServiceId_ShouldFail() {
        // Given
        ServiceRegistration registration = createValidRegistration();
        registration.setServiceId("");
        
        // When
        Mono<ServiceInstance> result = registryService.register(registration);
        
        // Then
        StepVerifier.create(result)
            .expectError(IllegalArgumentException.class)
            .verify();
        
        verify(registryStorage, never()).register(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    void register_InvalidPort_ShouldFail() {
        // Given
        ServiceRegistration registration = createValidRegistration();
        registration.setPort(0);
        
        // When
        Mono<ServiceInstance> result = registryService.register(registration);
        
        // Then
        StepVerifier.create(result)
            .expectError(IllegalArgumentException.class)
            .verify();
        
        verify(registryStorage, never()).register(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    void register_StorageException_ShouldFail() {
        // Given
        ServiceRegistration registration = createValidRegistration();
        when(registryStorage.register(any(ServiceInstance.class)))
            .thenThrow(new RuntimeException("Storage error"));
        
        // When
        Mono<ServiceInstance> result = registryService.register(registration);
        
        // Then
        StepVerifier.create(result)
            .expectError(RuntimeException.class)
            .verify();
        
        verify(registryStorage).register(any(ServiceInstance.class));
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    void deregister_ValidParameters_ShouldSucceed() {
        // Given
        String serviceId = "test-service";
        String instanceId = "test-instance";
        ServiceInstance deregisteredInstance = createServiceInstance(serviceId, instanceId);
        
        when(registryStorage.deregister(serviceId, instanceId)).thenReturn(deregisteredInstance);
        
        // When
        Mono<Void> result = registryService.deregister(serviceId, instanceId);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(registryStorage).deregister(serviceId, instanceId);
        verify(eventPublisher).publishEvent(any(ServiceEvent.class));
        
        // Verify event details
        ArgumentCaptor<ServiceEvent> eventCaptor = ArgumentCaptor.forClass(ServiceEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ServiceEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getType()).isEqualTo(ServiceEventType.DEREGISTER);
        assertThat(publishedEvent.getServiceId()).isEqualTo(serviceId);
        assertThat(publishedEvent.getInstanceId()).isEqualTo(instanceId);
    }
    
    @Test
    void deregister_NonExistentInstance_ShouldSucceedWithoutEvent() {
        // Given
        String serviceId = "test-service";
        String instanceId = "test-instance";
        
        when(registryStorage.deregister(serviceId, instanceId)).thenReturn(null);
        
        // When
        Mono<Void> result = registryService.deregister(serviceId, instanceId);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(registryStorage).deregister(serviceId, instanceId);
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    void deregister_InvalidServiceId_ShouldFail() {
        // When
        Mono<Void> result = registryService.deregister("", "instance-1");
        
        // Then
        StepVerifier.create(result)
            .expectError(IllegalArgumentException.class)
            .verify();
        
        verify(registryStorage, never()).deregister(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    void renew_ValidParameters_ShouldSucceed() {
        // Given
        String serviceId = "test-service";
        String instanceId = "test-instance";
        ServiceInstance renewedInstance = createServiceInstance(serviceId, instanceId);
        
        when(registryStorage.renew(serviceId, instanceId)).thenReturn(renewedInstance);
        
        // When
        Mono<ServiceInstance> result = registryService.renew(serviceId, instanceId);
        
        // Then
        StepVerifier.create(result)
            .expectNext(renewedInstance)
            .verifyComplete();
        
        verify(registryStorage).renew(serviceId, instanceId);
        verify(eventPublisher).publishEvent(any(ServiceEvent.class));
        
        // Verify event details
        ArgumentCaptor<ServiceEvent> eventCaptor = ArgumentCaptor.forClass(ServiceEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ServiceEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getType()).isEqualTo(ServiceEventType.RENEW);
        assertThat(publishedEvent.getServiceId()).isEqualTo(serviceId);
        assertThat(publishedEvent.getInstanceId()).isEqualTo(instanceId);
    }
    
    @Test
    void renew_NonExistentInstance_ShouldFail() {
        // Given
        String serviceId = "test-service";
        String instanceId = "test-instance";
        
        when(registryStorage.renew(serviceId, instanceId)).thenReturn(null);
        
        // When
        Mono<ServiceInstance> result = registryService.renew(serviceId, instanceId);
        
        // Then
        StepVerifier.create(result)
            .expectError(IllegalArgumentException.class)
            .verify();
        
        verify(registryStorage).renew(serviceId, instanceId);
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    void getInstances_ValidServiceId_ShouldReturnInstances() {
        // Given
        String serviceId = "test-service";
        List<ServiceInstance> instances = Arrays.asList(
            createServiceInstance(serviceId, "instance-1"),
            createServiceInstance(serviceId, "instance-2")
        );
        
        when(registryStorage.getInstances(serviceId)).thenReturn(instances);
        
        // When
        Flux<ServiceInstance> result = registryService.getInstances(serviceId);
        
        // Then
        StepVerifier.create(result)
            .expectNext(instances.get(0))
            .expectNext(instances.get(1))
            .verifyComplete();
        
        verify(registryStorage).getInstances(serviceId);
    }
    
    @Test
    void getInstances_NonExistentService_ShouldReturnEmpty() {
        // Given
        String serviceId = "non-existent-service";
        
        when(registryStorage.getInstances(serviceId)).thenReturn(Collections.emptyList());
        
        // When
        Flux<ServiceInstance> result = registryService.getInstances(serviceId);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(registryStorage).getInstances(serviceId);
    }
    
    @Test
    void getInstances_InvalidServiceId_ShouldFail() {
        // When
        Flux<ServiceInstance> result = registryService.getInstances("");
        
        // Then
        StepVerifier.create(result)
            .expectError(IllegalArgumentException.class)
            .verify();
        
        verify(registryStorage, never()).getInstances(any());
    }
    
    @Test
    void getServices_ShouldReturnAllServices() {
        // Given
        HashSet<String> services = new HashSet<>(Arrays.asList("service-1", "service-2", "service-3"));
        
        when(registryStorage.getServices()).thenReturn(services);
        
        // When
        Flux<String> result = registryService.getServices();
        
        // Then
        StepVerifier.create(result)
            .expectNextCount(3)
            .verifyComplete();
        
        verify(registryStorage).getServices();
    }
    
    @Test
    void getServices_EmptyRegistry_ShouldReturnEmpty() {
        // Given
        when(registryStorage.getServices()).thenReturn(new HashSet<>());
        
        // When
        Flux<String> result = registryService.getServices();
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(registryStorage).getServices();
    }
    
    private ServiceRegistration createValidRegistration() {
        ServiceRegistration registration = new ServiceRegistration();
        registration.setServiceId("test-service");
        registration.setInstanceId("test-instance");
        registration.setHost("localhost");
        registration.setPort(8080);
        return registration;
    }
    
    private ServiceInstance createServiceInstance(String serviceId, String instanceId) {
        ServiceInstance instance = new ServiceInstance(serviceId, instanceId, "localhost", 8080);
        instance.setStatus(InstanceStatus.UP);
        return instance;
    }
}