package io.github.alvin.hsc.registry.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.Validator;

/**
 * Registry Server Configuration
 * 注册中心服务器配置类
 * 
 * 启用配置属性绑定和验证功能
 * 
 * @author Alvin
 */
@Configuration
@EnableConfigurationProperties({
    RegistryServerProperties.class,
    HealthCheckProperties.class
})
public class RegistryServerConfiguration {

    // Bean definitions removed to avoid conflicts with Spring Boot auto-configuration
    // Components are now annotated with @Component for auto-detection
}