package com.hsc.tracing;

import io.opentracing.Span;
import io.opentracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 分布式追踪切面
 */
@Aspect
@Component
public class TracingAspect {

    @Autowired
    private Tracer tracer;
    
    @Autowired
    private TraceContext traceContext;

    @Around("@annotation(hscTrace)")
    public Object traceAround(ProceedingJoinPoint joinPoint, HscTrace hscTrace) throws Throwable {
        String operationName = hscTrace.operationName().isEmpty() ? 
            joinPoint.getSignature().toShortString() : hscTrace.operationName();
        
        Span span = tracer.buildSpan(operationName)
                .withTag("component", hscTrace.component())
                .withTag("method", joinPoint.getSignature().getName())
                .withTag("class", joinPoint.getTarget().getClass().getSimpleName())
                .start();
        
        traceContext.setCurrentSpan(span);
        
        try {
            Object result = joinPoint.proceed();
            span.setTag("success", true);
            return result;
        } catch (Exception e) {
            span.setTag("success", false);
            span.setTag("error", true);
            span.setTag("error.message", e.getMessage());
            throw e;
        } finally {
            span.finish();
            traceContext.clear();
        }
    }
}