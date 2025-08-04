package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServiceInstance model
 * 
 * @author Alvin
 */
class ServiceInstanceTest {

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
    void testValidServiceInstance() {
        ServiceInstance instance = new ServiceInstance("test-service", "instance-1", "localhost", 8080);
        
        Set<ConstraintViolation<ServiceInstance>> violations = validator.validate(instance);
        assertTrue(violations.isEmpty(), "Valid service instance should have no validation errors");
    }

    @Test
    void testServiceInstanceWithBlankServiceId() {
        ServiceInstance instance = new ServiceInstance("", "instance-1", "localhost", 8080);
        
        Set<ConstraintViolation<ServiceInstance>> violations = validator.validate(instance);
        assertEquals(1, violations.size());
        assertEquals("Service ID cannot be blank", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceInstanceWithBlankInstanceId() {
        ServiceInstance instance = new ServiceInstance("test-service", "", "localhost", 8080);
        
        Set<ConstraintViolation<ServiceInstance>> violations = validator.validate(instance);
        assertEquals(1, violations.size());
        assertEquals("Instance ID cannot be blank", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceInstanceWithBlankHost() {
        ServiceInstance instance = new ServiceInstance("test-service", "instance-1", "", 8080);
        
        Set<ConstraintViolation<ServiceInstance>> violations = validator.validate(instance);
        assertEquals(1, violations.size());
        assertEquals("Host cannot be blank", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceInstanceWithInvalidPort() {
        ServiceInstance instance = new ServiceInstance("test-service", "instance-1", "localhost", -1);
        
        Set<ConstraintViolation<ServiceInstance>> violations = validator.validate(instance);
        assertEquals(1, violations.size());
        assertEquals("Port must be positive", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceInstanceWithZeroPort() {
        ServiceInstance instance = new ServiceInstance("test-service", "instance-1", "localhost", 0);
        
        Set<ConstraintViolation<ServiceInstance>> violations = validator.validate(instance);
        assertEquals(1, violations.size());
        assertEquals("Port must be positive", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceInstanceGetUri() {
        ServiceInstance instance = new ServiceInstance("test-service", "instance-1", "localhost", 8080);
        assertEquals("http://localhost:8080", instance.getUri());
        
        instance.setSecure(true);
        assertEquals("https://localhost:8080", instance.getUri());
    }

    @Test
    void testServiceInstanceUpdateHeartbeat() {
        ServiceInstance instance = new ServiceInstance("test-service", "instance-1", "localhost", 8080);
        Instant originalHeartbeat = instance.getLastHeartbeat();
        
        // Wait a bit to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        instance.updateHeartbeat();
        assertTrue(instance.getLastHeartbeat().isAfter(originalHeartbeat));
    }

    @Test
    void testServiceInstanceWithMetadata() {
        ServiceInstance instance = new ServiceInstance("test-service", "instance-1", "localhost", 8080);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        metadata.put("zone", "us-east-1");
        instance.setMetadata(metadata);
        
        assertEquals("1.0.0", instance.getMetadata().get("version"));
        assertEquals("us-east-1", instance.getMetadata().get("zone"));
    }

    @Test
    void testServiceInstanceWithHealthCheck() {
        ServiceInstance instance = new ServiceInstance("test-service", "instance-1", "localhost", 8080);
        HealthCheckConfig healthCheck = new HealthCheckConfig();
        healthCheck.setPath("/health");
        instance.setHealthCheck(healthCheck);
        
        Set<ConstraintViolation<ServiceInstance>> violations = validator.validate(instance);
        assertTrue(violations.isEmpty());
        assertEquals("/health", instance.getHealthCheck().getPath());
    }

    @Test
    void testServiceInstanceJsonSerialization() throws Exception {
        ServiceInstance instance = new ServiceInstance("test-service", "instance-1", "localhost", 8080);
        instance.setSecure(true);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        instance.setMetadata(metadata);
        
        String json = objectMapper.writeValueAsString(instance);
        assertNotNull(json);
        assertTrue(json.contains("test-service"));
        assertTrue(json.contains("instance-1"));
        assertTrue(json.contains("localhost"));
        assertTrue(json.contains("8080"));
        assertTrue(json.contains("true"));
        assertTrue(json.contains("1.0.0"));
    }

    @Test
    void testServiceInstanceJsonDeserialization() throws Exception {
        String json = """
            {
                "serviceId": "test-service",
                "instanceId": "instance-1",
                "host": "localhost",
                "port": 8080,
                "secure": true,
                "status": "UP",
                "metadata": {
                    "version": "1.0.0"
                }
            }
            """;
        
        ServiceInstance instance = objectMapper.readValue(json, ServiceInstance.class);
        assertEquals("test-service", instance.getServiceId());
        assertEquals("instance-1", instance.getInstanceId());
        assertEquals("localhost", instance.getHost());
        assertEquals(8080, instance.getPort());
        assertTrue(instance.isSecure());
        assertEquals(InstanceStatus.UP, instance.getStatus());
        assertEquals("1.0.0", instance.getMetadata().get("version"));
    }

    @Test
    void testServiceInstanceToString() {
        ServiceInstance instance = new ServiceInstance("test-service", "instance-1", "localhost", 8080);
        String toString = instance.toString();
        assertTrue(toString.contains("test-service"));
        assertTrue(toString.contains("instance-1"));
        assertTrue(toString.contains("localhost"));
        assertTrue(toString.contains("8080"));
        assertTrue(toString.contains("UP"));
    }
}