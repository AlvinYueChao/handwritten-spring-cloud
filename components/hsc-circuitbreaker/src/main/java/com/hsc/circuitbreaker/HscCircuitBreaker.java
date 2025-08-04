package com.hsc.circuitbreaker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HSC熔断器注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HscCircuitBreaker {
    
    /**
     * 熔断器名称
     */
    String name() default "";
    
    /**
     * 降级方法名称
     */
    String fallbackMethod() default "";
    
    /**
     * 失败率阈值
     */
    float failureRateThreshold() default 50;
    
    /**
     * 等待时间（毫秒）
     */
    long waitDurationInOpenState() default 60000;
}