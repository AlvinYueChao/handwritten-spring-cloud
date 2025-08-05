package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.service.DiscoveryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

/**
 * Service Event WebSocket Handler
 * 服务事件 WebSocket 处理器
 * 
 * 提供实时的服务变更事件推送功能，支持：
 * - 监听指定服务的变更事件
 * - 实时推送服务注册、注销、状态变更等事件
 * - 支持多个客户端同时监听
 * - 自动处理连接管理和错误恢复
 * 
 * WebSocket 连接路径：/ws/services/{serviceId}/events
 * 
 * @author Alvin
 */
@Component
public class ServiceEventWebSocketHandler implements WebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceEventWebSocketHandler.class);
    
    private final DiscoveryService discoveryService;
    private final ObjectMapper objectMapper;
    
    public ServiceEventWebSocketHandler(DiscoveryService discoveryService, ObjectMapper objectMapper) {
        this.discoveryService = discoveryService;
        this.objectMapper = objectMapper;
        logger.info("Service event WebSocket handler initialized");
    }
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String serviceId = extractServiceIdFromPath(session.getHandshakeInfo().getUri());
        
        if (serviceId == null || serviceId.trim().isEmpty()) {
            logger.warn("Invalid service ID in WebSocket path: {}", session.getHandshakeInfo().getUri());
            return session.close();
        }
        
        logger.info("WebSocket connection established for service: {} from {}", 
                   serviceId, session.getHandshakeInfo().getRemoteAddress());
        
        // 监听服务事件并转换为 WebSocket 消息
        return discoveryService.watchService(serviceId)
                .map(this::convertEventToMessage)
                .onErrorContinue((throwable, obj) -> {
                    logger.warn("Error processing service event for service {}: {}", 
                               serviceId, throwable.getMessage());
                })
                .map(session::textMessage)
                .as(session::send)
                .doOnSubscribe(subscription -> {
                    logger.debug("Started watching service events for: {}", serviceId);
                })
                .doOnTerminate(() -> {
                    logger.info("WebSocket connection terminated for service: {}", serviceId);
                })
                .doOnError(throwable -> {
                    logger.error("WebSocket error for service {}: {}", serviceId, throwable.getMessage());
                });
    }
    
    /**
     * 从 WebSocket 路径中提取服务ID
     * 
     * @param uri WebSocket URI
     * @return 服务ID，如果无法提取则返回null
     */
    private String extractServiceIdFromPath(URI uri) {
        if (uri == null || uri.getPath() == null) {
            return null;
        }
        
        String path = uri.getPath();
        // 期望路径格式：/ws/services/{serviceId}/events
        String[] segments = path.split("/");
        
        if (segments.length >= 4 && "ws".equals(segments[1]) && "services".equals(segments[2])) {
            return segments[3];
        }
        
        return null;
    }
    
    /**
     * 将服务事件转换为 JSON 消息
     * 
     * @param event 服务事件
     * @return JSON 字符串
     */
    private String convertEventToMessage(ServiceEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize service event: {}", e.getMessage());
            // 返回一个简单的错误消息
            return String.format("{\"error\":\"Failed to serialize event\",\"eventId\":\"%s\"}", 
                                event.getEventId());
        }
    }
}