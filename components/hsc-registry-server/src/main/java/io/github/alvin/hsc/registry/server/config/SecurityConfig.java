package io.github.alvin.hsc.registry.server.config;

import io.github.alvin.hsc.registry.server.security.ApiKeyAuthenticationFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Security Configuration
 * 安全配置
 * 
 * 配置API密钥认证相关的安全设置
 * 
 * @author Alvin
 */
@Configuration
public class SecurityConfig {

    /**
     * 创建API密钥认证过滤器Bean
     */
    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter() {
        return new ApiKeyAuthenticationFilter();
    }

    /**
     * 安全属性配置
     */
    @Bean
    @ConfigurationProperties(prefix = "hsc.registry.server.security")
    @Validated
    public SecurityProperties securityProperties() {
        return new SecurityProperties();
    }

    /**
     * 安全属性类
     */
    public static class SecurityProperties {
        
        /**
         * 是否启用API密钥认证
         */
        private boolean enabled = false;
        
        /**
         * API密钥值
         */
        @NotBlank(message = "API key cannot be blank when security is enabled")
        private String apiKey = "hsc-registry-default-key-2024";
        
        /**
         * API密钥请求头名称
         */
        @NotBlank(message = "API key header name cannot be blank")
        private String headerName = "X-Registry-API-Key";
        
        /**
         * API密钥查询参数名称
         */
        @NotBlank(message = "API key query parameter name cannot be blank")
        private String queryParamName = "api_key";
        
        /**
         * 不需要认证的公开路径
         */
        @NotNull(message = "Public paths cannot be null")
        private Set<String> publicPaths = Set.of(
            "/actuator/health",
            "/actuator/info", 
            "/actuator/prometheus",
            "/management/info"
        );
        
        /**
         * 认证失败时的错误代码
         */
        @NotBlank(message = "Authentication error code cannot be blank")
        private String authErrorCode = "AUTH_001";
        
        /**
         * 认证失败时的错误消息
         */
        @NotBlank(message = "Authentication error message cannot be blank")
        private String authErrorMessage = "API key authentication required";

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getQueryParamName() {
            return queryParamName;
        }

        public void setQueryParamName(String queryParamName) {
            this.queryParamName = queryParamName;
        }

        public Set<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(Set<String> publicPaths) {
            this.publicPaths = publicPaths;
        }

        public String getAuthErrorCode() {
            return authErrorCode;
        }

        public void setAuthErrorCode(String authErrorCode) {
            this.authErrorCode = authErrorCode;
        }

        public String getAuthErrorMessage() {
            return authErrorMessage;
        }

        public void setAuthErrorMessage(String authErrorMessage) {
            this.authErrorMessage = authErrorMessage;
        }
    }
}