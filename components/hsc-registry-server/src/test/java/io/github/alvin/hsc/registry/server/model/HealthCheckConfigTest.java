package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HealthCheckConfig model
 * 
 * @author Alvin
 */
class HealthCheckConfigTest {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testValidHealthCheckConfig() {
        HealthCheckConfig config = new HealthCheckConfig();
        
        Set<ConstraintViolation<HealthCheckConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "Valid health check config should have no validation errors");
    }

    @Test
    void testHealthCheckConfigWithBlankPath() {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setPath("");
        
        Set<ConstraintViolation<HealthCheckConfig>> violations = validator.validate(config);
        assertEquals(1, violations.size());
        assertEquals("Health check path cannot be blank", violations.iterator().next().getMessage());
    }

    @Test
    void testHealthCheckConfigWithNullPath() {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setPath(null);
        
        Set<ConstraintViolation<HealthCheckConfig>> violations = validator.validate(config);
        assertEquals(1, violations.size());
        assertEquals("Health check path cannot be blank", violations.iterator().next().getMessage());
    }

    @Test
    void testHealthCheckConfigWithNullInterval() {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setInterval(null);
        
        Set<ConstraintViolation<HealthCheckConfig>> violations = validator.validate(config);
        assertEquals(1, violations.size());
        assertEquals("Health check interval cannot be null", violations.iterator().next().getMessage());
    }

    @Test
    void testHealthCheckConfigWithNullTimeout() {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setTimeout(null);
        
        Set<ConstraintViolation<HealthCheckConfig>> violations = validator.validate(config);
        assertEquals(1, violations.size());
        assertEquals("Health check timeout cannot be null", violations.iterator().next().getMessage());
    }

    @Test
    void testHealthCheckConfigWithInvalidRetryCount() {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setRetryCount(0);
        
        Set<ConstraintViolation<HealthCheckConfig>> violations = validator.validate(config);
        assertEquals(1, violations.size());
        assertEquals("Retry count must be at least 1", violations.iterator().next().getMessage());
    }

    @Test
    void testHealthCheckConfigWithNegativeRetryCount() {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setRetryCount(-1);
        
        Set<ConstraintViolation<HealthCheckConfig>> violations = validator.validate(config);
        assertEquals(1, violations.size());
        assertEquals("Retry count must be at least 1", violations.iterator().next().getMessage());
    }

    @Test
    void testHealthCheckConfigWithNullType() {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setType(null);
        
        Set<ConstraintViolation<HealthCheckConfig>> violations = validator.validate(config);
        assertEquals(1, violations.size());
        assertEquals("Health check type cannot be null", violations.iterator().next().getMessage());
    }

    @Test
    void testHealthCheckConfigDefaultValues() {
        HealthCheckConfig config = new HealthCheckConfig();
        
        assertTrue(config.isEnabled());
        assertEquals("/actuator/health", config.getPath());
        assertEquals(Duration.ofSeconds(30), config.getInterval());
        assertEquals(Duration.ofSeconds(5), config.getTimeout());
        assertEquals(3, config.getRetryCount());
        assertEquals(HealthCheckType.HTTP, config.getType());
    }

    @Test
    void testHealthCheckConfigConstructorWithParameters() {
        Duration interval = Duration.ofSeconds(60);
        Duration timeout = Duration.ofSeconds(10);
        HealthCheckConfig config = new HealthCheckConfig(true, "/health", interval, timeout);
        
        assertTrue(config.isEnabled());
        assertEquals("/health", config.getPath());
        assertEquals(interval, config.getInterval());
        assertEquals(timeout, config.getTimeout());
        assertEquals(3, config.getRetryCount()); // default value
        assertEquals(HealthCheckType.HTTP, config.getType()); // default value
    }

    @Test
    void testHealthCheckConfigWithCustomValues() {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(false);
        config.setPath("/custom/health");
        config.setInterval(Duration.ofSeconds(45));
        config.setTimeout(Duration.ofSeconds(8));
        config.setRetryCount(5);
        config.setType(HealthCheckType.TCP);
        
        assertFalse(config.isEnabled());
        assertEquals("/custom/health", config.getPath());
        assertEquals(Duration.ofSeconds(45), config.getInterval());
        assertEquals(Duration.ofSeconds(8), config.getTimeout());
        assertEquals(5, config.getRetryCount());
        assertEquals(HealthCheckType.TCP, config.getType());
    }

    @Test
    void testHealthCheckConfigJsonSerialization() throws Exception {
        HealthCheckConfig config = new HealthCheckConfig();
        config.setEnabled(true);
        config.setPath("/health");
        config.setInterval(Duration.ofSeconds(30));
        config.setTimeout(Duration.ofSeconds(5));
        config.setRetryCount(3);
        config.setType(HealthCheckType.HTTP);
        
        String json = objectMapper.writeValueAsString(config);
        assertNotNull(json);
        assertTrue(json.contains("\"enabled\":true"));
        assertTrue(json.contains("\"/health\""));
        assertTrue(json.contains("\"interval\":30"));
        assertTrue(json.contains("\"timeout\":5"));
        assertTrue(json.contains("\"retryCount\":3"));
        assertTrue(json.contains("\"HTTP\""));
    }

    @Test
    void testHealthCheckConfigJsonDeserialization() throws Exception {
        String json = """
            {
                "enabled": true,
                "path": "/health",
                "interval": "PT30S",
                "timeout": "PT5S",
                "retryCount": 3,
                "type": "HTTP"
            }
            """;
        
        HealthCheckConfig config = objectMapper.readValue(json, HealthCheckConfig.class);
        assertTrue(config.isEnabled());
        assertEquals("/health", config.getPath());
        assertEquals(Duration.ofSeconds(30), config.getInterval());
        assertEquals(Duration.ofSeconds(5), config.getTimeout());
        assertEquals(3, config.getRetryCount());
        assertEquals(HealthCheckType.HTTP, config.getType());
    }

    @Test
    void testHealthCheckConfigToString() {
        HealthCheckConfig config = new HealthCheckConfig();
        String toString = config.toString();
        assertTrue(toString.contains("true"));
        assertTrue(toString.contains("/actuator/health"));
        assertTrue(toString.contains("PT30S"));
        assertTrue(toString.contains("PT5S"));
        assertTrue(toString.contains("HTTP"));
    }

    @Test
    void testHealthCheckConfigWithDifferentTypes() {
        HealthCheckConfig httpConfig = new HealthCheckConfig();
        httpConfig.setType(HealthCheckType.HTTP);
        assertEquals(HealthCheckType.HTTP, httpConfig.getType());
        
        HealthCheckConfig tcpConfig = new HealthCheckConfig();
        tcpConfig.setType(HealthCheckType.TCP);
        assertEquals(HealthCheckType.TCP, tcpConfig.getType());
        
        HealthCheckConfig scriptConfig = new HealthCheckConfig();
        scriptConfig.setType(HealthCheckType.SCRIPT);
        assertEquals(HealthCheckType.SCRIPT, scriptConfig.getType());
    }
}