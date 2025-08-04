package io.github.alvin.hsc.registry.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Registry Server Application Test
 * 注册中心应用启动测试
 * 
 * @author Alvin
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RegistryServerApplicationTest {

    @Test
    void contextLoads() {
        // 测试应用上下文是否能正常加载
    }
}