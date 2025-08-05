package io.github.alvin.hsc.registry.server.config;

import io.github.alvin.hsc.registry.server.repository.RegistryStorage;
import io.github.alvin.hsc.registry.server.repository.MemoryRegistryStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registry Storage Configuration
 * 注册表存储配置
 * 
 * 提供注册表存储的配置和Bean定义。
 * 默认使用内存存储实现，可以通过提供自定义Bean来覆盖。
 * 
 * @author Alvin
 */
@Configuration
public class RegistryStorageConfig {
    
    /**
     * 提供默认的注册表存储实现
     * 
     * @param memoryStorage 内存存储实现
     * @return 注册表存储接口
     */
    @Bean
    @ConditionalOnMissingBean(RegistryStorage.class)
    public RegistryStorage registryStorage(MemoryRegistryStorage memoryStorage) {
        return memoryStorage;
    }
}