package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.model.ServiceEventType;
import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.service.DiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServiceEventController
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class ServiceEventControllerTest {
    
    @Mock
    private DiscoveryService discoveryService;
    
    private ServiceEventController controller;
    private ServiceInstance testInstance;
    private ServiceEvent testEvent;
    
    @BeforeEach
    void setUp() {
        controller = new ServiceEventController(discoveryService);
        
        // 创建测试数据
        testInstance = new ServiceInstance("test-service", "instance-1", "192.168.1.100", 8080);
        testInstance.setStatus(InstanceStatus.UP);
        
        testEvent = new ServiceEvent(ServiceEventType.REGISTER, testInstance);
    }
    
    @Test
    void getWebSocketInfo_ShouldReturnCorrectInfo_WhenValidServiceId() {
        // Given
        String serviceId = "test-service";
        
        // When
        Map<String, Object> result = controller.getWebSocketInfo(serviceId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("serviceId")).isEqualTo(serviceId);
        assertThat(result.get("websocketUrl")).isEqualTo("/ws/services/test-service/events");
        assertThat(result.get("protocol")).isEqualTo("ws");
        assertThat(result.get("description")).isNotNull();
        assertThat(result.get("usage")).isNotNull();
        
        verifyNoInteractions(discoveryService);
    }
    
    @Test
    void streamServiceEvents_ShouldReturnEventStream_WhenValidServiceId() {
        // Given
        String serviceId = "test-service";
        when(discoveryService.watchService(serviceId))
                .thenReturn(Flux.just(testEvent));
        
        // When & Then
        StepVerifier.create(controller.streamServiceEvents(serviceId))
                .expectNext(testEvent)
                .verifyComplete();
        
        verify(discoveryService).watchService(serviceId);
    }
    
    @Test
    void streamServiceEvents_ShouldHandleErrors_WhenDiscoveryServiceFails() {
        // Given
        String serviceId = "test-service";
        when(discoveryService.watchService(serviceId))
                .thenReturn(Flux.error(new RuntimeException("Service error")));
        
        // When & Then
        StepVerifier.create(controller.streamServiceEvents(serviceId))
                .verifyError(RuntimeException.class); // 应该传播错误，因为 onErrorContinue 只处理元素级别的错误
        
        verify(discoveryService).watchService(serviceId);
    }
    
    @Test
    void streamServiceEvents_ShouldContinueOnEventError() {
        // Given
        String serviceId = "test-service";
        ServiceEvent errorEvent = new ServiceEvent();
        errorEvent.setServiceId(serviceId);
        
        when(discoveryService.watchService(serviceId))
                .thenReturn(Flux.just(errorEvent, testEvent));
        
        // When & Then
        StepVerifier.create(controller.streamServiceEvents(serviceId))
                .expectNext(errorEvent)
                .expectNext(testEvent)
                .verifyComplete();
        
        verify(discoveryService).watchService(serviceId);
    }
    
    @Test
    void getEventEndpoints_ShouldReturnAllEndpoints() {
        // When
        Map<String, Object> result = controller.getEventEndpoints();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).containsKeys("websocket", "sse", "info");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> websocketInfo = (Map<String, Object>) result.get("websocket");
        assertThat(websocketInfo.get("path")).isEqualTo("/ws/services/{serviceId}/events");
        assertThat(websocketInfo.get("protocol")).isEqualTo("WebSocket");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> sseInfo = (Map<String, Object>) result.get("sse");
        assertThat(sseInfo.get("path")).isEqualTo("/api/v1/events/services/{serviceId}/stream");
        assertThat(sseInfo.get("protocol")).isEqualTo("HTTP/SSE");
        assertThat(sseInfo.get("contentType")).isEqualTo("text/event-stream");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> infoEndpoint = (Map<String, Object>) result.get("info");
        assertThat(infoEndpoint.get("path")).isEqualTo("/api/v1/events/services/{serviceId}/websocket-info");
        assertThat(infoEndpoint.get("protocol")).isEqualTo("HTTP/REST");
        
        verifyNoInteractions(discoveryService);
    }
}