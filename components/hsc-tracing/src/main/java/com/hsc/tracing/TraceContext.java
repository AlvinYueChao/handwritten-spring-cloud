package com.hsc.tracing;

import io.opentracing.Span;
import io.opentracing.SpanContext;

/**
 * 追踪上下文管理器
 */
public class TraceContext {
    
    private static final ThreadLocal<Span> CURRENT_SPAN = new ThreadLocal<>();
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    
    /**
     * 设置当前Span
     */
    public void setCurrentSpan(Span span) {
        CURRENT_SPAN.set(span);
        if (span != null) {
            SpanContext spanContext = span.context();
            TRACE_ID.set(spanContext.toTraceId());
        }
    }
    
    /**
     * 获取当前Span
     */
    public Span getCurrentSpan() {
        return CURRENT_SPAN.get();
    }
    
    /**
     * 获取当前TraceId
     */
    public String getCurrentTraceId() {
        return TRACE_ID.get();
    }
    
    /**
     * 清理当前上下文
     */
    public void clear() {
        CURRENT_SPAN.remove();
        TRACE_ID.remove();
    }
    
    /**
     * 检查是否有活跃的追踪
     */
    public boolean hasActiveTrace() {
        return CURRENT_SPAN.get() != null;
    }
}