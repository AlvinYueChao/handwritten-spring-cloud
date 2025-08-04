package io.github.alvin.hsc.registry.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * HSC Registry Server Application
 * 服务注册中心启动类
 * 
 * @author Alvin
 */
@SpringBootApplication
@EnableScheduling
public class RegistryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistryServerApplication.class, args);
    }
}