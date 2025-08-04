package io.github.alvin.hsc.registry.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registry Server Configuration
 * 注册中心服务器配置类
 * 
 * @author Alvin
 */
@Configuration
@EnableConfigurationProperties(RegistryServerProperties.class)
public class RegistryServerConfiguration {

    // 基础配置类，后续任务会添加更多配置
}