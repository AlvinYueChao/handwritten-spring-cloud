# HSC Tracing - 分布式追踪组件

## 功能介绍

HSC Tracing 是手写Spring Cloud框架中的分布式追踪组件，提供微服务调用链路追踪功能。

## 主要特性

- 基于 OpenTracing 标准实现
- 支持 Jaeger 和 Zipkin 追踪系统
- 注解式追踪配置
- 自动HTTP请求追踪
- 线程本地追踪上下文管理
- 集成监控指标收集

## 使用方式

### 1. 添加依赖

```gradle
implementation project(':components:hsc-tracing')
```

### 2. 配置追踪系统

```properties
hsc.tracing.service-name=my-service
hsc.tracing.jaeger.endpoint=http://localhost:14268/api/traces
```

### 3. 使用注解

```java
@Service
public class OrderService {
    
    @HscTrace(
        operationName = "createOrder",
        component = "order-service",
        logArgs = true
    )
    public Order createOrder(OrderRequest request) {
        // 业务逻辑
        return orderRepository.save(order);
    }
}
```

## 配置参数

- `operationName`: 操作名称
- `component`: 组件名称（默认"hsc"）
- `logArgs`: 是否记录方法参数
- `logResult`: 是否记录返回值

## 追踪上下文

组件提供 `TraceContext` 来管理当前线程的追踪信息：

```java
@Autowired
private TraceContext traceContext;

public void someMethod() {
    String traceId = traceContext.getCurrentTraceId();
    Span currentSpan = traceContext.getCurrentSpan();
}
```

## 支持的追踪系统

- Jaeger
- Zipkin
- 其他兼容 OpenTracing 的系统