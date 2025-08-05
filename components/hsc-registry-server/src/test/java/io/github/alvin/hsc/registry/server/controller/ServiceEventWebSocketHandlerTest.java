package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.model.ServiceEventType;
import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.model.InstanceStatus;
import io.github.alvin.hsc.registry.server.service.DiscoveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServiceEventWebSocketHandler
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class ServiceEventWebSocketHandlerTest {
    
    @Mock
    private DiscoveryService discoveryService;
    
    @Mock
    private WebSocketSession session;
    
    @Mock
    private HandshakeInfo handshakeInfo;
    
    @Mock
    private WebSocketMessage webSocketMessage;
    
    private ObjectMapper objectMapper;
    private ServiceEventWebSocketHandler handler;
    private ServiceInstance testInstance;
    private ServiceEvent testEvent;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new ServiceEventWebSocketHandler(discoveryService, objectMapper);
        
        // 创建测试数据
        testInstance = new ServiceInstance("test-service", "instance-1", "192.168.1.100", 8080);
        testInstance.setStatus(InstanceStatus.UP);
        
        testEvent = new ServiceEvent(ServiceEventType.REGISTER, testInstance);
    }
    
    @Test
    void handle_ShouldProcessEventsSuccessfully_WhenValidServiceId() {
        // Given
        URI uri = URI.create("/ws/services/test-service/events");
        when(session.getHandshakeInfo()).thenReturn(handshakeInfo);
        when(handshakeInfo.getUri()).thenReturn(uri);
        when(handshakeInfo.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        
        when(discoveryService.watchService("test-service"))
                .thenReturn(Flux.just(testEvent));

        when(session.send(any(Flux.class))).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(handler.handle(session))
                .verifyComplete();
        
        verify(discoveryService).watchService("test-service");
        verify(session).send(any(Flux.class));
    }
    
    @Test
    void handle_ShouldCloseConnection_WhenInvalidServiceId() {
        // Given
        URI uri = URI.create("/ws/invalid/path");
        when(session.getHandshakeInfo()).thenReturn(handshakeInfo);
        when(handshakeInfo.getUri()).thenReturn(uri);
        when(session.close()).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(handler.handle(session))
                .verifyComplete();
        
        verify(session).close();
        verifyNoInteractions(discoveryService);
    }
    
    @Test
    void handle_ShouldCloseConnection_WhenEmptyServiceId() {
        // Given
        URI uri = URI.create("/ws/services//events");
        when(session.getHandshakeInfo()).thenReturn(handshakeInfo);
        when(handshakeInfo.getUri()).thenReturn(uri);
        when(session.close()).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(handler.handle(session))
                .verifyComplete();
        
        verify(session).close();
        verifyNoInteractions(discoveryService);
    }
    
    @Test
    void handle_ShouldCloseConnection_WhenNullUri() {
        // Given
        when(session.getHandshakeInfo()).thenReturn(handshakeInfo);
        when(handshakeInfo.getUri()).thenReturn(null);
        when(session.close()).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(handler.handle(session))
                .verifyComplete();
        
        verify(session).close();
        verifyNoInteractions(discoveryService);
    }
    
    @Test
    void handle_ShouldContinueOnError_WhenEventProcessingFails() {
        // Given
        URI uri = URI.create("/ws/services/test-service/events");
        when(session.getHandshakeInfo()).thenReturn(handshakeInfo);
        when(handshakeInfo.getUri()).thenReturn(uri);
        when(handshakeInfo.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        
        // 创建一个会导致序列化错误的事件
        ServiceEvent errorEvent = new ServiceEvent();
        errorEvent.setServiceId("test-service");
        
        when(discoveryService.watchService("test-service"))
                .thenReturn(Flux.just(errorEvent, testEvent));
        
        when(session.send(any(Flux.class))).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(handler.handle(session))
                .verifyComplete();
        
        verify(discoveryService).watchService("test-service");
        verify(session).send(any(Flux.class));
    }
    
    @Test
    void handle_ShouldHandleDiscoveryServiceError() {
        // Given
        URI uri = URI.create("/ws/services/test-service/events");
        when(session.getHandshakeInfo()).thenReturn(handshakeInfo);
        when(handshakeInfo.getUri()).thenReturn(uri);
        when(handshakeInfo.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        
        when(discoveryService.watchService("test-service"))
                .thenReturn(Flux.error(new RuntimeException("Discovery service error")));
        
        when(session.send(any(Flux.class))).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(handler.handle(session))
                .verifyComplete();
        
        verify(discoveryService).watchService("test-service");
    }
}