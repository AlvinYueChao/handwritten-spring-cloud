package io.github.alvin.hsc.registry.server.config;

import io.github.alvin.hsc.registry.server.security.ApiKeyAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityConfig Test
 * 安全配置测试
 * 
 * @author Alvin
 */
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void apiKeyAuthenticationFilter_shouldCreateBean() {
        // Act
        ApiKeyAuthenticationFilter filter = securityConfig.apiKeyAuthenticationFilter();

        // Assert
        assertNotNull(filter);
        assertInstanceOf(ApiKeyAuthenticationFilter.class, filter);
    }

    @Test
    void securityProperties_shouldCreateBean() {
        // Act
        SecurityConfig.SecurityProperties properties = securityConfig.securityProperties();

        // Assert
        assertNotNull(properties);
        assertInstanceOf(SecurityConfig.SecurityProperties.class, properties);
    }

    @Test
    void securityProperties_defaultValues_shouldBeValid() {
        // Arrange
        SecurityConfig.SecurityProperties properties = new SecurityConfig.SecurityProperties();

        // Act
        Set<ConstraintViolation<SecurityConfig.SecurityProperties>> violations = 
            validator.validate(properties);

        // Assert
        assertTrue(violations.isEmpty(), "Default properties should be valid");
        assertFalse(properties.isEnabled());
        assertEquals("hsc-registry-default-key-2024", properties.getApiKey());
        assertEquals("X-Registry-API-Key", properties.getHeaderName());
        assertEquals("api_key", properties.getQueryParamName());
        assertEquals("AUTH_001", properties.getAuthErrorCode());
        assertEquals("API key authentication required", properties.getAuthErrorMessage());
        assertNotNull(properties.getPublicPaths());
        assertTrue(properties.getPublicPaths().contains("/actuator/health"));
    }

    @Test
    void securityProperties_bindFromConfiguration_shouldWork() {
        // Arrange
        Map<String, Object> properties = new HashMap<>();
        properties.put("hsc.registry.security.enabled", true);
        properties.put("hsc.registry.security.api-key", "custom-key-123");
        properties.put("hsc.registry.security.header-name", "X-Custom-API-Key");
        properties.put("hsc.registry.security.query-param-name", "custom_key");
        properties.put("hsc.registry.security.auth-error-code", "CUSTOM_AUTH_001");
        properties.put("hsc.registry.security.auth-error-message", "Custom authentication required");
        properties.put("hsc.registry.security.public-paths", "/custom/health,/custom/info");

        MapConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        // Act
        SecurityConfig.SecurityProperties securityProperties = 
            binder.bind("hsc.registry.security", SecurityConfig.SecurityProperties.class)
                  .orElseThrow(() -> new RuntimeException("Failed to bind security properties"));

        // Assert
        assertTrue(securityProperties.isEnabled());
        assertEquals("custom-key-123", securityProperties.getApiKey());
        assertEquals("X-Custom-API-Key", securityProperties.getHeaderName());
        assertEquals("custom_key", securityProperties.getQueryParamName());
        assertEquals("CUSTOM_AUTH_001", securityProperties.getAuthErrorCode());
        assertEquals("Custom authentication required", securityProperties.getAuthErrorMessage());
    }

    @Test
    void securityProperties_blankApiKey_shouldFailValidation() {
        // Arrange
        SecurityConfig.SecurityProperties properties = new SecurityConfig.SecurityProperties();
        properties.setEnabled(true);
        properties.setApiKey("");

        // Act
        Set<ConstraintViolation<SecurityConfig.SecurityProperties>> violations = 
            validator.validate(properties);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("API key cannot be blank when security is enabled")));
    }

    @Test
    void securityProperties_nullApiKey_shouldFailValidation() {
        // Arrange
        SecurityConfig.SecurityProperties properties = new SecurityConfig.SecurityProperties();
        properties.setEnabled(true);
        properties.setApiKey(null);

        // Act
        Set<ConstraintViolation<SecurityConfig.SecurityProperties>> violations = 
            validator.validate(properties);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("API key cannot be blank when security is enabled")));
    }

    @Test
    void securityProperties_blankHeaderName_shouldFailValidation() {
        // Arrange
        SecurityConfig.SecurityProperties properties = new SecurityConfig.SecurityProperties();
        properties.setHeaderName("");

        // Act
        Set<ConstraintViolation<SecurityConfig.SecurityProperties>> violations = 
            validator.validate(properties);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("API key header name cannot be blank")));
    }

    @Test
    void securityProperties_blankQueryParamName_shouldFailValidation() {
        // Arrange
        SecurityConfig.SecurityProperties properties = new SecurityConfig.SecurityProperties();
        properties.setQueryParamName("");

        // Act
        Set<ConstraintViolation<SecurityConfig.SecurityProperties>> violations = 
            validator.validate(properties);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("API key query parameter name cannot be blank")));
    }

    @Test
    void securityProperties_nullPublicPaths_shouldFailValidation() {
        // Arrange
        SecurityConfig.SecurityProperties properties = new SecurityConfig.SecurityProperties();
        properties.setPublicPaths(null);

        // Act
        Set<ConstraintViolation<SecurityConfig.SecurityProperties>> violations = 
            validator.validate(properties);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Public paths cannot be null")));
    }

    @Test
    void securityProperties_blankAuthErrorCode_shouldFailValidation() {
        // Arrange
        SecurityConfig.SecurityProperties properties = new SecurityConfig.SecurityProperties();
        properties.setAuthErrorCode("");

        // Act
        Set<ConstraintViolation<SecurityConfig.SecurityProperties>> violations = 
            validator.validate(properties);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Authentication error code cannot be blank")));
    }

    @Test
    void securityProperties_blankAuthErrorMessage_shouldFailValidation() {
        // Arrange
        SecurityConfig.SecurityProperties properties = new SecurityConfig.SecurityProperties();
        properties.setAuthErrorMessage("");

        // Act
        Set<ConstraintViolation<SecurityConfig.SecurityProperties>> violations = 
            validator.validate(properties);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Authentication error message cannot be blank")));
    }

    @Test
    void securityProperties_allPropertiesSet_shouldBeValid() {
        // Arrange
        SecurityConfig.SecurityProperties properties = new SecurityConfig.SecurityProperties();
        properties.setEnabled(true);
        properties.setApiKey("valid-key");
        properties.setHeaderName("X-Valid-Header");
        properties.setQueryParamName("valid_param");
        properties.setPublicPaths(Set.of("/valid/path"));
        properties.setAuthErrorCode("VALID_001");
        properties.setAuthErrorMessage("Valid error message");

        // Act
        Set<ConstraintViolation<SecurityConfig.SecurityProperties>> violations = 
            validator.validate(properties);

        // Assert
        assertTrue(violations.isEmpty(), "All properties should be valid");
    }

    @Test
    void securityProperties_gettersAndSetters_shouldWork() {
        // Arrange
        SecurityConfig.SecurityProperties properties = new SecurityConfig.SecurityProperties();

        // Act & Assert
        properties.setEnabled(true);
        assertTrue(properties.isEnabled());

        properties.setApiKey("test-key");
        assertEquals("test-key", properties.getApiKey());

        properties.setHeaderName("X-Test-Header");
        assertEquals("X-Test-Header", properties.getHeaderName());

        properties.setQueryParamName("test_param");
        assertEquals("test_param", properties.getQueryParamName());

        Set<String> publicPaths = Set.of("/test/path");
        properties.setPublicPaths(publicPaths);
        assertEquals(publicPaths, properties.getPublicPaths());

        properties.setAuthErrorCode("TEST_001");
        assertEquals("TEST_001", properties.getAuthErrorCode());

        properties.setAuthErrorMessage("Test error message");
        assertEquals("Test error message", properties.getAuthErrorMessage());
    }
}