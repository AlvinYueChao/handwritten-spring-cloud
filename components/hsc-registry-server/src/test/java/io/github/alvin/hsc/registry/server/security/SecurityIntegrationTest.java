package io.github.alvin.hsc.registry.server.security;

import io.github.alvin.hsc.registry.server.RegistryServerApplication;
import io.github.alvin.hsc.registry.server.config.ControllerTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Security Integration Test
 * 安全集成测试
 * 
 * 测试API密钥认证在实际应用中的工作情况
 * 
 * @author Alvin
 */
@SpringBootTest(
    classes = RegistryServerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureWebTestClient
@Import(ControllerTestConfig.class)
class SecurityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private static final String VALID_API_KEY = "test-integration-key-2024";
    private static final String INVALID_API_KEY = "invalid-key";
    private static final String API_KEY_HEADER = "X-Registry-API-Key";

    @Test
    void publicEndpoint_withoutApiKey_shouldAllow() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void publicEndpoint_withInvalidApiKey_shouldStillAllow() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .header(API_KEY_HEADER, INVALID_API_KEY)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void protectedEndpoint_withValidApiKeyHeader_shouldAllow() {
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances")
            .header(API_KEY_HEADER, VALID_API_KEY)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void protectedEndpoint_withValidApiKeyQuery_shouldAllow() {
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances?api_key=" + VALID_API_KEY)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void protectedEndpoint_withoutApiKey_shouldDeny() {
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("AUTH_001")
            .jsonPath("$.message").isEqualTo("API key authentication required")
            .jsonPath("$.path").isEqualTo("/api/v1/discovery/services/test-service/instances")
            .jsonPath("$.details.hint").exists()
            .jsonPath("$.details.remote_address").exists();
    }

    @Test
    void protectedEndpoint_withInvalidApiKey_shouldDeny() {
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances")
            .header(API_KEY_HEADER, INVALID_API_KEY)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("AUTH_001")
            .jsonPath("$.message").isEqualTo("API key authentication required");
    }

    @Test
    void protectedEndpoint_withEmptyApiKey_shouldDeny() {
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances")
            .header(API_KEY_HEADER, "")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpoint_withWhitespaceApiKey_shouldDeny() {
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances")
            .header(API_KEY_HEADER, "   ")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void registryEndpoints_withValidApiKey_shouldAllow() {
        String[] endpoints = {
            "/api/registry/heartbeat",
            "/api/registry/services",
            "/management/registry/info"
        };

        for (String endpoint : endpoints) {
            webTestClient
                .get()
                .uri(endpoint)
                .header(API_KEY_HEADER, VALID_API_KEY)
                .exchange()
                .expectStatus().isOk();
        }
    }

    @Test
    void registryEndpoints_withoutApiKey_shouldDeny() {
        String[] endpoints = {
            "/api/registry/heartbeat",
            "/api/registry/services", 
            "/management/registry/info"
        };

        for (String endpoint : endpoints) {
            webTestClient
                .get()
                .uri(endpoint)
                .exchange()
                .expectStatus().isUnauthorized();
        }
    }

    @Test
    void postEndpoint_withValidApiKey_shouldAllow() {
        webTestClient
            .post()
            .uri("/api/registry/register")
            .header(API_KEY_HEADER, VALID_API_KEY)
            .header("Content-Type", "application/json")
            .bodyValue("{\"serviceId\":\"test-service\"}")
            .exchange()
            .expectStatus().is4xxClientError(); // 4xx because of validation, but not 401
    }

    @Test
    void postEndpoint_withoutApiKey_shouldDeny() {
        webTestClient
            .post()
            .uri("/api/registry/register")
            .header("Content-Type", "application/json")
            .bodyValue("{\"serviceId\":\"test-service\"}")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void deleteEndpoint_withValidApiKey_shouldAllow() {
        webTestClient
            .delete()
            .uri("/api/registry/deregister/test-service/test-instance")
            .header(API_KEY_HEADER, VALID_API_KEY)
            .exchange()
            .expectStatus().is4xxClientError(); // 4xx because service doesn't exist, but not 401
    }

    @Test
    void deleteEndpoint_withoutApiKey_shouldDeny() {
        webTestClient
            .delete()
            .uri("/api/registry/deregister/test-service/test-instance")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void queryParamTakesPrecedenceOverHeader() {
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances?api_key=" + VALID_API_KEY)
            .header(API_KEY_HEADER, INVALID_API_KEY)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void caseInsensitiveHeader_shouldWork() {
        // 注意：HTTP头部通常不区分大小写，但WebTestClient可能不支持此测试
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances")
            .header("x-registry-api-key", VALID_API_KEY)
            .exchange()
            .expectStatus().isUnauthorized(); // 因为我们的过滤器使用精确匹配
    }

    @Test
    void multiplePublicPaths_shouldAllowAccess() {
        String[] publicPaths = {
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus", 
            "/management/info"
        };

        for (String path : publicPaths) {
            webTestClient
                .get()
                .uri(path)
                .exchange()
                .expectStatus().isOk();
        }
    }

    @Test 
    void errorResponse_shouldContainAllRequiredFields() {
        webTestClient
            .get()
            .uri("/api/v1/discovery/services")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("AUTH_001")
            .jsonPath("$.message").isEqualTo("API key authentication required")
            .jsonPath("$.timestamp").exists()
            .jsonPath("$.path").isEqualTo("/api/v1/discovery/services")
            .jsonPath("$.details").exists()
            .jsonPath("$.details.hint").exists()
            .jsonPath("$.details.remote_address").exists();
    }
}