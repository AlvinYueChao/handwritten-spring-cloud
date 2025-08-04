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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServiceRegistration model
 * 
 * @author Alvin
 */
class ServiceRegistrationTest {

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
    void testValidServiceRegistration() {
        ServiceRegistration registration = new ServiceRegistration("test-service", "instance-1", "localhost", 8080);
        
        Set<ConstraintViolation<ServiceRegistration>> violations = validator.validate(registration);
        assertTrue(violations.isEmpty(), "Valid service registration should have no validation errors");
    }

    @Test
    void testServiceRegistrationWithBlankServiceId() {
        ServiceRegistration registration = new ServiceRegistration("", "instance-1", "localhost", 8080);
        
        Set<ConstraintViolation<ServiceRegistration>> violations = validator.validate(registration);
        assertEquals(1, violations.size());
        assertEquals("Service ID cannot be blank", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceRegistrationWithNullServiceId() {
        ServiceRegistration registration = new ServiceRegistration();
        registration.setInstanceId("instance-1");
        registration.setHost("localhost");
        registration.setPort(8080);
        
        Set<ConstraintViolation<ServiceRegistration>> violations = validator.validate(registration);
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("Service ID cannot be blank")));
    }

    @Test
    void testServiceRegistrationWithBlankInstanceId() {
        ServiceRegistration registration = new ServiceRegistration("test-service", "", "localhost", 8080);
        
        Set<ConstraintViolation<ServiceRegistration>> violations = validator.validate(registration);
        assertEquals(1, violations.size());
        assertEquals("Instance ID cannot be blank", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceRegistrationWithBlankHost() {
        ServiceRegistration registration = new ServiceRegistration("test-service", "instance-1", "", 8080);
        
        Set<ConstraintViolation<ServiceRegistration>> violations = validator.validate(registration);
        assertEquals(1, violations.size());
        assertEquals("Host cannot be blank", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceRegistrationWithNullPort() {
        ServiceRegistration registration = new ServiceRegistration();
        registration.setServiceId("test-service");
        registration.setInstanceId("instance-1");
        registration.setHost("localhost");
        
        Set<ConstraintViolation<ServiceRegistration>> violations = validator.validate(registration);
        assertEquals(1, violations.size());
        assertEquals("Port cannot be null", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceRegistrationWithInvalidPort() {
        ServiceRegistration registration = new ServiceRegistration("test-service", "instance-1", "localhost", -1);
        
        Set<ConstraintViolation<ServiceRegistration>> violations = validator.validate(registration);
        assertEquals(1, violations.size());
        assertEquals("Port must be positive", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceRegistrationWithZeroPort() {
        ServiceRegistration registration = new ServiceRegistration("test-service", "instance-1", "localhost", 0);
        
        Set<ConstraintViolation<ServiceRegistration>> violations = validator.validate(registration);
        assertEquals(1, violations.size());
        assertEquals("Port must be positive", violations.iterator().next().getMessage());
    }

    @Test
    void testServiceRegistrationToServiceInstance() {
        ServiceRegistration registration = new ServiceRegistration("test-service", "instance-1", "localhost", 8080);
        registration.setSecure(true);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        registration.setMetadata(metadata);
        
        HealthCheckConfig healthCheck = new HealthCheckConfig();
        healthCheck.setPath("/health");
        registration.setHealthCheck(healthCheck);
        
        ServiceInstance instance = registration.toServiceInstance();
        
        assertEquals("test-service", instance.getServiceId());
        assertEquals("instance-1", instance.getInstanceId());
        assertEquals("localhost", instance.getHost());
        assertEquals(8080, instance.getPort());
        assertTrue(instance.isSecure());
        assertEquals("1.0.0", instance.getMetadata().get("version"));
        assertEquals("/health", instance.getHealthCheck().getPath());
        assertEquals(InstanceStatus.UP, instance.getStatus());
        assertNotNull(instance.getRegistrationTime());
        assertNotNull(instance.getLastHeartbeat());
    }

    @Test
    void testServiceRegistrationWithCustomLeaseDuration() {
        ServiceRegistration registration = new ServiceRegistration("test-service", "instance-1", "localhost", 8080);
        Duration customLease = Duration.ofSeconds(120);
        registration.setLeaseDuration(customLease);
        
        assertEquals(customLease, registration.getLeaseDuration());
    }

    @Test
    void testServiceRegistrationDefaultValues() {
        ServiceRegistration registration = new ServiceRegistration("test-service", "instance-1", "localhost", 8080);
        
        assertFalse(registration.isSecure());
        assertEquals(Duration.ofSeconds(90), registration.getLeaseDuration());
    }

    @Test
    void testServiceRegistrationJsonSerialization() throws Exception {
        ServiceRegistration registration = new ServiceRegistration("test-service", "instance-1", "localhost", 8080);
        registration.setSecure(true);
        registration.setLeaseDuration(Duration.ofSeconds(120));
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        registration.setMetadata(metadata);
        
        String json = objectMapper.writeValueAsString(registration);
        assertNotNull(json);
        assertTrue(json.contains("test-service"));
        assertTrue(json.contains("instance-1"));
        assertTrue(json.contains("localhost"));
        assertTrue(json.contains("8080"));
        assertTrue(json.contains("true"));
        assertTrue(json.contains("1.0.0"));
    }

    @Test
    void testServiceRegistrationJsonDeserialization() throws Exception {
        String json = """
            {
                "serviceId": "test-service",
                "instanceId": "instance-1",
                "host": "localhost",
                "port": 8080,
                "secure": true,
                "metadata": {
                    "version": "1.0.0"
                },
                "leaseDuration": "PT2M"
            }
            """;
        
        ServiceRegistration registration = objectMapper.readValue(json, ServiceRegistration.class);
        assertEquals("test-service", registration.getServiceId());
        assertEquals("instance-1", registration.getInstanceId());
        assertEquals("localhost", registration.getHost());
        assertEquals(8080, registration.getPort().intValue());
        assertTrue(registration.isSecure());
        assertEquals("1.0.0", registration.getMetadata().get("version"));
        assertEquals(Duration.ofMinutes(2), registration.getLeaseDuration());
    }

    @Test
    void testServiceRegistrationToString() {
        ServiceRegistration registration = new ServiceRegistration("test-service", "instance-1", "localhost", 8080);
        String toString = registration.toString();
        assertTrue(toString.contains("test-service"));
        assertTrue(toString.contains("instance-1"));
        assertTrue(toString.contains("localhost"));
        assertTrue(toString.contains("8080"));
    }
}