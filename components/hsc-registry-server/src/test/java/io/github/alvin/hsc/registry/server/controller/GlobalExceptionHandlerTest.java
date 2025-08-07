package io.github.alvin.hsc.registry.server.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GlobalExceptionHandler Test
 * 全局异常处理器测试
 * 
 * @author Alvin
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void handleValidationException_shouldReturnBadRequest() {
        // Arrange
        WebExchangeBindException ex = mock(WebExchangeBindException.class);
        FieldError fieldError = new FieldError("testObject", "testField", "Test error message");
        when(ex.getFieldErrors()).thenReturn(List.of(fieldError));
        when(ex.getMessage()).thenReturn("Validation failed");

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            exceptionHandler.handleValidationException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_001", response.getBody().getCode());
        assertEquals("Request parameter validation failed", response.getBody().getMessage());
        assertTrue(response.getBody().getDetails().containsKey("testField"));
        assertEquals("Test error message", response.getBody().getDetails().get("testField"));
    }

    @Test
    void handleMethodArgumentNotValidException_shouldReturnBadRequest() {
        // Arrange
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("testObject", "testField", "Test error message");
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        when(ex.getMessage()).thenReturn("Validation failed");
        when(ex.getParameter()).thenReturn(mock(org.springframework.core.MethodParameter.class));

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            exceptionHandler.handleMethodArgumentNotValidException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_001", response.getBody().getCode());
        assertEquals("Request parameter validation failed", response.getBody().getMessage());
        assertTrue(response.getBody().getDetails().containsKey("testField"));
        assertEquals("Test error message", response.getBody().getDetails().get("testField"));
    }

    @Test
    void handleInputException_shouldReturnBadRequest() {
        // Arrange
        ServerWebInputException ex = mock(ServerWebInputException.class);
        when(ex.getMessage()).thenReturn("Invalid input");
        when(ex.getReason()).thenReturn("Invalid format");

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            exceptionHandler.handleInputException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_001", response.getBody().getCode());
        assertEquals("Invalid input parameters", response.getBody().getMessage());
        assertTrue(response.getBody().getDetails().containsKey("reason"));
        assertEquals("Invalid format", response.getBody().getDetails().get("reason"));
    }

    @Test
    void handleIllegalArgumentException_shouldReturnBadRequest() {
        // Arrange
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument provided");

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            exceptionHandler.handleIllegalArgumentException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_001", response.getBody().getCode());
        assertEquals("Invalid argument provided", response.getBody().getMessage());
        assertTrue(response.getBody().getDetails().containsKey("type"));
        assertEquals("IllegalArgumentException", response.getBody().getDetails().get("type"));
    }

    @Test
    void handleServiceRegistrationException_shouldReturnInternalServerError() {
        // Arrange
        GlobalExceptionHandler.ServiceRegistrationException ex = 
            new GlobalExceptionHandler.ServiceRegistrationException(
                "test-service", "test-instance", "Registration failed");

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            exceptionHandler.handleServiceRegistrationException(ex);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("REGISTRY_001", response.getBody().getCode());
        assertEquals("Registration failed", response.getBody().getMessage());
        assertEquals("test-service", response.getBody().getDetails().get("serviceId"));
        assertEquals("test-instance", response.getBody().getDetails().get("instanceId"));
    }

    @Test
    void handleServiceInstanceNotFoundException_shouldReturnNotFound() {
        // Arrange
        GlobalExceptionHandler.ServiceInstanceNotFoundException ex = 
            new GlobalExceptionHandler.ServiceInstanceNotFoundException("test-service", "test-instance");

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            exceptionHandler.handleServiceInstanceNotFoundException(ex);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("REGISTRY_002", response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Service instance not found"));
        assertEquals("test-service", response.getBody().getDetails().get("serviceId"));
        assertEquals("test-instance", response.getBody().getDetails().get("instanceId"));
    }

    @Test
    void handleGenericException_shouldReturnInternalServerError() {
        // Arrange
        RuntimeException ex = new RuntimeException("Unexpected error");

        // Act
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = 
            exceptionHandler.handleGenericException(ex);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().getCode());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
        assertTrue(response.getBody().getDetails().containsKey("type"));
        assertEquals("RuntimeException", response.getBody().getDetails().get("type"));
    }

    @Test
    void errorResponse_shouldHaveCorrectFields() {
        // Arrange & Act
        GlobalExceptionHandler.ErrorResponse errorResponse = 
            new GlobalExceptionHandler.ErrorResponse(
                "TEST_001", 
                "Test message", 
                "2023-01-01T00:00:00Z", 
                "/test/path", 
                java.util.Map.of("key", "value")
            );

        // Assert
        assertEquals("TEST_001", errorResponse.getCode());
        assertEquals("Test message", errorResponse.getMessage());
        assertEquals("2023-01-01T00:00:00Z", errorResponse.getTimestamp());
        assertEquals("/test/path", errorResponse.getPath());
        assertEquals("value", errorResponse.getDetails().get("key"));
    }

    @Test
    void serviceRegistrationException_shouldHaveCorrectFields() {
        // Arrange & Act
        GlobalExceptionHandler.ServiceRegistrationException ex = 
            new GlobalExceptionHandler.ServiceRegistrationException(
                "test-service", "test-instance", "Registration failed");

        // Assert
        assertEquals("test-service", ex.getServiceId());
        assertEquals("test-instance", ex.getInstanceId());
        assertEquals("Registration failed", ex.getMessage());
    }

    @Test
    void serviceInstanceNotFoundException_shouldHaveCorrectFields() {
        // Arrange & Act
        GlobalExceptionHandler.ServiceInstanceNotFoundException ex = 
            new GlobalExceptionHandler.ServiceInstanceNotFoundException("test-service", "test-instance");

        // Assert
        assertEquals("test-service", ex.getServiceId());
        assertEquals("test-instance", ex.getInstanceId());
        assertTrue(ex.getMessage().contains("Service instance not found"));
        assertTrue(ex.getMessage().contains("test-service"));
        assertTrue(ex.getMessage().contains("test-instance"));
    }
}