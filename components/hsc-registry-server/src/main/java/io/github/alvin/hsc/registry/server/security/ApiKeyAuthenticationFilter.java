package io.github.alvin.hsc.registry.server.security;

import io.github.alvin.hsc.registry.server.config.SecurityConfig;
import io.github.alvin.hsc.registry.server.controller.GlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * API Key Authentication Filter
 * API 密钥认证过滤器
 * 
 * 提供基础的API密钥认证功能，用于保护注册中心的API端点
 * 
 * @author Alvin
 */
@Component
@Order(-100) // 高优先级，在其他过滤器之前执行
public class ApiKeyAuthenticationFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    
    @Autowired
    private SecurityConfig.SecurityProperties securityProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 如果安全认证未启用，直接通过
        if (!securityProperties.isEnabled()) {
            return chain.filter(exchange);
        }
        
        String path = exchange.getRequest().getPath().value();
        
        // 检查是否为公开路径
        if (isPublicPath(path)) {
            logger.debug("Allowing access to public path: {}", path);
            return chain.filter(exchange);
        }
        
        // 获取API密钥
        String apiKey = getApiKey(exchange);
        
        // 验证API密钥
        if (!isValidApiKey(apiKey)) {
            logger.warn("Invalid API key for path: {}, remote address: {}", 
                    path, getClientIpAddress(exchange));
            return handleUnauthorized(exchange);
        }
        
        logger.debug("API key authentication successful for path: {}", path);
        return chain.filter(exchange);
    }

    /**
     * 检查是否为公开路径
     */
    private boolean isPublicPath(String path) {
        return securityProperties.getPublicPaths().stream().anyMatch(path::startsWith);
    }

    /**
     * 获取API密钥
     */
    private String getApiKey(ServerWebExchange exchange) {
        // 首先检查HTTP头
        String apiKey = exchange.getRequest().getHeaders().getFirst(securityProperties.getHeaderName());
        
        // 如果头部没有，检查查询参数
        if (!StringUtils.hasText(apiKey)) {
            apiKey = exchange.getRequest().getQueryParams().getFirst(securityProperties.getQueryParamName());
        }
        
        return apiKey;
    }

    /**
     * 验证API密钥
     */
    private boolean isValidApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }
        
        // 简单的字符串比较 - 在实际项目中应该使用更安全的验证方式
        return securityProperties.getApiKey().equals(apiKey);
    }

    /**
     * 处理未授权请求
     */
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange) {
        GlobalExceptionHandler.ErrorResponse errorResponse = 
            new GlobalExceptionHandler.ErrorResponse(
                securityProperties.getAuthErrorCode(),
                securityProperties.getAuthErrorMessage(),
                Instant.now().toString(),
                exchange.getRequest().getPath().value(),
                Map.of(
                    "hint", "Provide API key via " + securityProperties.getHeaderName() + " header or " + securityProperties.getQueryParamName() + " query parameter",
                    "remote_address", getClientIpAddress(exchange)
                )
            );

        // 设置响应状态和内容类型
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        
        // 写入响应体
        String responseBody = String.format(
            "{\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\",\"path\":\"%s\",\"details\":%s}",
            errorResponse.getCode(),
            errorResponse.getMessage(),
            errorResponse.getTimestamp(),
            errorResponse.getPath(),
            formatDetails(errorResponse.getDetails())
        );
        
        var dataBuffer = exchange.getResponse().bufferFactory().wrap(responseBody.getBytes());
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }
        
        return exchange.getRequest().getRemoteAddress() != null ? 
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    /**
     * 格式化详细信息为JSON字符串
     */
    private String formatDetails(Map<String, Object> details) {
        StringBuilder sb = new StringBuilder("{");
        details.forEach((key, value) -> {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"").append(key).append("\":\"").append(value).append("\"");
        });
        sb.append("}");
        return sb.toString();
    }
}