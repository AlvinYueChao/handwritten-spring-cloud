package io.github.alvin.hsc.registry.server.config;

import io.github.alvin.hsc.registry.server.controller.ServiceEventWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket Configuration
 * WebSocket 配置类
 * 
 * 配置 WebSocket 路由和处理器，支持：
 * - 服务事件实时推送
 * - 路径参数解析
 * - 连接管理
 * 
 * @author Alvin
 */
@Configuration
public class WebSocketConfig {
    
    /**
     * 配置 WebSocket 路由映射
     * 
     * @param serviceEventHandler 服务事件处理器
     * @return HandlerMapping
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping(ServiceEventWebSocketHandler serviceEventHandler) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        
        // 服务事件推送路径：/ws/services/{serviceId}/events
        map.put("/ws/services/*/events", serviceEventHandler);
        
        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setUrlMap(map);
        handlerMapping.setOrder(1);
        
        return handlerMapping;
    }
    
    /**
     * WebSocket 处理器适配器
     * 
     * @return WebSocketHandlerAdapter
     */
    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}