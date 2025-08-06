package io.github.alvin.hsc.registry.server.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler
 * 全局异常处理器
 * 
 * 提供统一的异常处理和错误响应格式
 * 
 * @author Alvin
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理参数验证异常 (WebFlux)
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(WebExchangeBindException ex) {
        logger.warn("Validation error: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        for (FieldError error : ex.getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_001",
                "Request parameter validation failed",
                Instant.now().toString(),
                ex.getMethodParameter() != null ? ex.getMethodParameter().toString() : "unknown",
                details
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * 处理参数验证异常 (Spring MVC)
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        logger.warn("Validation error: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_001",
                "Request parameter validation failed",
                Instant.now().toString(),
                ex.getParameter().toString(),
                details
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * 处理输入参数异常
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleInputException(ServerWebInputException ex) {
        logger.warn("Input error: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_001",
                "Invalid input parameters",
                Instant.now().toString(),
                ex.getMethodParameter() != null ? ex.getMethodParameter().toString() : "unknown",
                Map.of("reason", ex.getReason())
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.warn("Illegal argument: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_001",
                ex.getMessage(),
                Instant.now().toString(),
                "unknown",
                Map.of("type", "IllegalArgumentException")
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * 处理服务注册异常
     */
    @ExceptionHandler(ServiceRegistrationException.class)
    public ResponseEntity<ErrorResponse> handleServiceRegistrationException(ServiceRegistrationException ex) {
        logger.error("Service registration error: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                "REGISTRY_001",
                ex.getMessage(),
                Instant.now().toString(),
                "unknown",
                Map.of("serviceId", ex.getServiceId(), "instanceId", ex.getInstanceId())
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * 处理服务实例不存在异常
     */
    @ExceptionHandler(ServiceInstanceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleServiceInstanceNotFoundException(ServiceInstanceNotFoundException ex) {
        logger.warn("Service instance not found: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                "REGISTRY_002",
                ex.getMessage(),
                Instant.now().toString(),
                "unknown",
                Map.of("serviceId", ex.getServiceId(), "instanceId", ex.getInstanceId())
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected error occurred", ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                Instant.now().toString(),
                "unknown",
                Map.of("type", ex.getClass().getSimpleName())
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * 错误响应模型
     */
    public static class ErrorResponse {
        private String code;
        private String message;
        private String timestamp;
        private String path;
        private Map<String, Object> details;

        public ErrorResponse(String code, String message, String timestamp, String path, Map<String, Object> details) {
            this.code = code;
            this.message = message;
            this.timestamp = timestamp;
            this.path = path;
            this.details = details;
        }

        // Getters and Setters
        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Map<String, Object> getDetails() {
            return details;
        }

        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }
    }

    /**
     * 服务注册异常
     */
    public static class ServiceRegistrationException extends RuntimeException {
        private final String serviceId;
        private final String instanceId;

        public ServiceRegistrationException(String serviceId, String instanceId, String message) {
            super(message);
            this.serviceId = serviceId;
            this.instanceId = instanceId;
        }

        public ServiceRegistrationException(String serviceId, String instanceId, String message, Throwable cause) {
            super(message, cause);
            this.serviceId = serviceId;
            this.instanceId = instanceId;
        }

        public String getServiceId() {
            return serviceId;
        }

        public String getInstanceId() {
            return instanceId;
        }
    }

    /**
     * 服务实例不存在异常
     */
    public static class ServiceInstanceNotFoundException extends RuntimeException {
        private final String serviceId;
        private final String instanceId;

        public ServiceInstanceNotFoundException(String serviceId, String instanceId) {
            super(String.format("Service instance not found: serviceId=%s, instanceId=%s", serviceId, instanceId));
            this.serviceId = serviceId;
            this.instanceId = instanceId;
        }

        public String getServiceId() {
            return serviceId;
        }

        public String getInstanceId() {
            return instanceId;
        }
    }
}