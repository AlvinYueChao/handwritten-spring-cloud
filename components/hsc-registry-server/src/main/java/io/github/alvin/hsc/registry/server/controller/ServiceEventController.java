package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.service.DiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Map;

/**
 * Service Event Controller 服务事件控制器
 *
 * <p>提供服务事件相关的 REST API，包括： - 获取 WebSocket 连接信息 - 提供 Server-Sent Events (SSE) 作为 WebSocket 的替代方案 -
 * 服务事件查询接口
 *
 * @author Alvin
 */
@RestController
@RequestMapping("/api/v1/events")
@Validated
public class ServiceEventController {

  private static final Logger logger = LoggerFactory.getLogger(ServiceEventController.class);

  private final DiscoveryService discoveryService;

  public ServiceEventController(DiscoveryService discoveryService) {
    this.discoveryService = discoveryService;
    logger.info("Service event controller initialized");
  }

  /**
   * 获取 WebSocket 连接信息
   *
   * @param serviceId 服务ID
   * @return WebSocket 连接信息
   */
  @GetMapping("/services/{serviceId}/websocket-info")
  public Map<String, Object> getWebSocketInfo(@PathVariable @NotBlank String serviceId) {
    logger.debug("Getting WebSocket info for service: {}", serviceId);

    return Map.of(
        "serviceId", serviceId,
        "websocketUrl", "/ws/services/" + serviceId + "/events",
        "protocol", "ws",
        "description", "WebSocket endpoint for real-time service events",
        "usage", "Connect to this endpoint to receive real-time service change events");
  }

  /**
   * 通过 Server-Sent Events (SSE) 推送服务事件 作为 WebSocket 的替代方案
   *
   * @param serviceId 服务ID
   * @return 服务事件流
   */
  @GetMapping(value = "/services/{serviceId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServiceEvent> streamServiceEvents(@PathVariable @NotBlank String serviceId) {
    logger.info("Starting SSE stream for service: {}", serviceId);

    return discoveryService
        .watchService(serviceId)
        .doOnSubscribe(
            subscription -> {
              /*
              当客户端订阅 SSE 流时触发
              具体来说，当 HTTP 客户端发起对 /api/v1/events/services/{serviceId}/stream 的 GET 请求并建立连接时触发
               */
              logger.debug("SSE subscription started for service: {}", serviceId);
            })
        .doOnNext(
            event -> {
              /*
              每当有新的 ServiceEvent 事件产生并发送给客户端时触发
              事件包括服务注册(REGISTER)、注销(DEREGISTER)、状态变更(STATUS_CHANGE)等
               */
              logger.debug("Sending SSE event for service {}: {}", serviceId, event.getType());
            })
        .doOnError(
            throwable -> {
              /*
              当事件流发生错误时触发
              例如网络异常、服务器内部错误等导致流无法继续
               */
              logger.error(
                  "SSE stream error for service {}: {}", serviceId, throwable.getMessage());
            })
        .doOnComplete(
            () -> {
              /*
              当事件流正常完成时触发
              这种情况在实际使用中较少见，因为服务事件流通常是持续的
               */
              logger.debug("SSE stream completed for service: {}", serviceId);
            })
        .onErrorContinue(
            (throwable, obj) -> {
              /*
              当处理单个事件时发生错误时触发，但不会终止整个流
              例如某个事件处理失败，但流会继续处理后续事件
               */
              logger.warn(
                  "Error in SSE stream for service {}: {}", serviceId, throwable.getMessage());
            });
  }

  /**
   * 获取所有可用的事件流端点信息
   *
   * @return 端点信息列表
   */
  @GetMapping("/endpoints")
  public Map<String, Object> getEventEndpoints() {
    logger.debug("Getting event endpoints information");

    return Map.of(
        "websocket",
            Map.of(
                "path", "/ws/services/{serviceId}/events",
                "description", "WebSocket endpoint for real-time service events",
                "protocol", "WebSocket"),
        "sse",
            Map.of(
                "path", "/api/v1/events/services/{serviceId}/stream",
                "description", "Server-Sent Events endpoint for service events",
                "protocol", "HTTP/SSE",
                "contentType", "text/event-stream"),
        "info",
            Map.of(
                "path", "/api/v1/events/services/{serviceId}/websocket-info",
                "description", "Get WebSocket connection information",
                "protocol", "HTTP/REST"));
  }
}
