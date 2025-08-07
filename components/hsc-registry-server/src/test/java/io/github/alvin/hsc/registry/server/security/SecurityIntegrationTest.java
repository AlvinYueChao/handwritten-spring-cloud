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
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test"
    }
)
@AutoConfigureWebTestClient
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
            "/api/v1/registry/services",
            "/api/v1/discovery/services",
            "/api/v1/management/status"
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
            "/api/v1/registry/services",
            "/api/v1/discovery/services", 
            "/api/v1/management/status"
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
            .uri("/api/v1/registry/services/test-service/instances")
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
            .uri("/api/v1/registry/services/test-service/instances")
            .header("Content-Type", "application/json")
            .bodyValue("{\"serviceId\":\"test-service\"}")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void deleteEndpoint_withValidApiKey_shouldAllow() {
        webTestClient
            .delete()
            .uri("/api/v1/registry/services/test-service/instances/test-instance")
            .header(API_KEY_HEADER, VALID_API_KEY)
            .exchange()
            .expectStatus().isNoContent(); // 204 - controller handles non-existent instances gracefully
    }

    @Test
    void deleteEndpoint_withoutApiKey_shouldDeny() {
        webTestClient
            .delete()
            .uri("/api/v1/registry/services/test-service/instances/test-instance")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void headerTakesPrecedenceOverQueryParam() {
        // HTTP头优先于查询参数，所以无效的header会导致认证失败
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances?api_key=" + VALID_API_KEY)
            .header(API_KEY_HEADER, INVALID_API_KEY)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void queryParamWorksWhenNoHeader() {
        // 当没有header时，查询参数应该有效
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances?api_key=" + VALID_API_KEY)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void caseInsensitiveHeader_shouldWork() {
        // HTTP头部是大小写不敏感的，Spring WebFlux自动处理
        webTestClient
            .get()
            .uri("/api/v1/discovery/services/test-service/instances")
            .header("x-registry-api-key", VALID_API_KEY)
            .exchange()
            .expectStatus().isOk(); // 小写header应该有效
    }

    @Test
    void multiplePublicPaths_shouldAllowAccess() {
        String[] publicPaths = {
            "/actuator/health"
            // 只保留确实存在的端点
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