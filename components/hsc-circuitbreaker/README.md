# HSC Circuit Breaker - 熔断器组件

## 功能介绍

HSC Circuit Breaker 是手写Spring Cloud框架中的熔断器组件，提供服务调用的容错保护机制。

## 主要特性

- 基于 Resilience4j 实现的熔断器功能
- 支持注解式熔断器配置
- 可配置的失败率阈值和恢复时间
- 集成监控指标收集
- 支持降级方法配置

## 使用方式

### 1. 添加依赖

```gradle
implementation project(':components:hsc-circuitbreaker')
```

### 2. 使用注解

```java
@Service
public class UserService {
    
    @HscCircuitBreaker(
        name = "getUserInfo",
        failureRateThreshold = 50,
        waitDurationInOpenState = 60000
    )
    public User getUserInfo(Long userId) {
        // 业务逻辑
        return userRepository.findById(userId);
    }
}
```

## 配置参数

- `name`: 熔断器名称
- `failureRateThreshold`: 失败率阈值（默认50%）
- `waitDurationInOpenState`: 熔断器打开状态等待时间（默认60秒）
- `fallbackMethod`: 降级方法名称

## 监控指标

组件集成了 Micrometer 监控指标，可以监控：
- 熔断器状态变化
- 调用成功/失败次数
- 响应时间统计