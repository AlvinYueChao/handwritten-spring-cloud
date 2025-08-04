package com.hsc.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 熔断器切面
 */
@Aspect
@Component
public class CircuitBreakerAspect {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Around("@annotation(hscCircuitBreaker)")
    public Object circuitBreakerAround(ProceedingJoinPoint joinPoint, HscCircuitBreaker hscCircuitBreaker) throws Throwable {
        String name = hscCircuitBreaker.name().isEmpty() ? 
            joinPoint.getSignature().toShortString() : hscCircuitBreaker.name();
        
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        
        Supplier<Object> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        });
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            if (!hscCircuitBreaker.fallbackMethod().isEmpty()) {
                // 这里可以实现fallback方法调用逻辑
                throw new RuntimeException("Circuit breaker is open, fallback not implemented yet", e);
            }
            throw e;
        }
    }
}