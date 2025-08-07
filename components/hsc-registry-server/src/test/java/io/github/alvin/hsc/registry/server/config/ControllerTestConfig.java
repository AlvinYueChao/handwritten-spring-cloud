package io.github.alvin.hsc.registry.server.config;

import io.github.alvin.hsc.registry.server.security.ApiKeyAuthenticationFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Controller测试配置类
 * 为Controller测试提供必要的Bean和配置
 */
@TestConfiguration
@Import(SecurityConfig.class)
public class ControllerTestConfig {

    /**
     * 创建API密钥认证过滤器Bean
     * 用于Controller测试中的安全认证
     */
    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter() {
        return new ApiKeyAuthenticationFilter();
    }
}