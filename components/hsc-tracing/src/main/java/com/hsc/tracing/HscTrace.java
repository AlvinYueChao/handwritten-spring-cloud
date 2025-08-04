package com.hsc.tracing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HSC分布式追踪注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HscTrace {
    
    /**
     * 操作名称
     */
    String operationName() default "";
    
    /**
     * 组件名称
     */
    String component() default "hsc";
    
    /**
     * 是否记录参数
     */
    boolean logArgs() default false;
    
    /**
     * 是否记录返回值
     */
    boolean logResult() default false;
}