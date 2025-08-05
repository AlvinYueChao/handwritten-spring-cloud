package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.ServiceInstance;
import io.github.alvin.hsc.registry.server.repository.RegistryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Heartbeat Monitor Service 心跳监控服务
 *
 * <p>负责定期检查服务实例的心跳状态，处理心跳超时的实例。 该服务具有以下特性： - 定期扫描所有注册的服务实例 - 检测心跳超时的实例并进行状态转换 - 提供心跳监控的统计信息 -
 * 支持配置化的心跳超时时间
 *
 * @author Alvin
 */
@Service
public class HeartbeatMonitor {

  private static final Logger logger = LoggerFactory.getLogger(HeartbeatMonitor.class);

  private final RegistryStorage registryStorage;
  private final InstanceLifecycleManager lifecycleManager;

  /** 心跳超时时间，默认90秒 */
  private final Duration heartbeatTimeout = Duration.ofSeconds(90);

  /** 监控统计信息 */
  private final AtomicLong totalChecks = new AtomicLong(0);

  private final AtomicLong timeoutInstances = new AtomicLong(0);

  public HeartbeatMonitor(
      RegistryStorage registryStorage, InstanceLifecycleManager lifecycleManager) {
    this.registryStorage = registryStorage;
    this.lifecycleManager = lifecycleManager;
    logger.info(
        "Heartbeat monitor initialized with timeout: {} seconds", heartbeatTimeout.getSeconds());
  }

  /** 定期检查心跳超时 每30秒执行一次心跳检查 */
  @Scheduled(fixedDelay = 30000, initialDelay = 30000)
  public void checkHeartbeatTimeouts() {
    if (!registryStorage.isHealthy()) {
      logger.warn("Registry storage is not healthy, skipping heartbeat check");
      return;
    }

    try {
      logger.debug("Starting heartbeat timeout check");

      Map<String, List<ServiceInstance>> allInstances = registryStorage.getAllInstances();
      int checkedInstances = 0;
      int timeoutCount = 0;

      for (Map.Entry<String, List<ServiceInstance>> serviceEntry : allInstances.entrySet()) {
        String serviceId = serviceEntry.getKey();
        List<ServiceInstance> instances = serviceEntry.getValue();

        for (ServiceInstance instance : instances) {
          checkedInstances++;

          if (lifecycleManager.isHeartbeatTimeout(instance, heartbeatTimeout)) {
            timeoutCount++;
            logger.info(
                "Detected heartbeat timeout for instance: {} - {} (last heartbeat: {})",
                serviceId,
                instance.getInstanceId(),
                instance.getLastHeartbeat());

            // 处理心跳超时
            lifecycleManager.handleHeartbeatTimeout(instance);
          }
        }
      }

      // 更新统计信息
      totalChecks.incrementAndGet();
      timeoutInstances.addAndGet(timeoutCount);

      if (timeoutCount > 0) {
        logger.info(
            "Heartbeat check completed: checked {} instances, found {} timeouts",
            checkedInstances,
            timeoutCount);
      } else {
        logger.debug(
            "Heartbeat check completed: checked {} instances, no timeouts", checkedInstances);
      }

    } catch (Exception e) {
      logger.error("Error during heartbeat timeout check", e);
    }
  }

  /**
   * 手动触发心跳检查 主要用于测试或紧急情况
   *
   * @return 检查结果统计
   */
  public HeartbeatCheckResult performHeartbeatCheck() {
    logger.info("Manual heartbeat check triggered");

    Map<String, List<ServiceInstance>> allInstances = registryStorage.getAllInstances();
    int checkedInstances = 0;
    int timeoutCount = 0;

    for (Map.Entry<String, List<ServiceInstance>> serviceEntry : allInstances.entrySet()) {
      List<ServiceInstance> instances = serviceEntry.getValue();

      for (ServiceInstance instance : instances) {
        checkedInstances++;

        if (lifecycleManager.isHeartbeatTimeout(instance, heartbeatTimeout)) {
          timeoutCount++;
          lifecycleManager.handleHeartbeatTimeout(instance);
        }
      }
    }

    HeartbeatCheckResult result = new HeartbeatCheckResult(checkedInstances, timeoutCount);
    logger.info("Manual heartbeat check completed: {}", result);

    return result;
  }

  /**
   * 获取心跳监控统计信息
   *
   * @return 统计信息
   */
  public HeartbeatMonitorStats getStats() {
    return new HeartbeatMonitorStats(totalChecks.get(), timeoutInstances.get(), heartbeatTimeout);
  }

  /** 重置统计信息 */
  public void resetStats() {
    totalChecks.set(0);
    timeoutInstances.set(0);
    logger.info("Heartbeat monitor statistics reset");
  }

  /** 心跳检查结果 */
  public record HeartbeatCheckResult(int checkedInstances, int timeoutInstances) {

    @Override
    public String toString() {
      return String.format(
          "HeartbeatCheckResult{checked=%d, timeouts=%d}", checkedInstances, timeoutInstances);
    }
  }

  /** 心跳监控统计信息 */
  public record HeartbeatMonitorStats(
      long totalChecks, long totalTimeouts, Duration heartbeatTimeout) {

    public double getTimeoutRate() {
      return totalChecks > 0 ? (double) totalTimeouts / totalChecks : 0.0;
    }

    @Override
    public String toString() {
      return String.format(
          "HeartbeatMonitorStats{checks=%d, timeouts=%d, rate=%.2f%%, timeout=%ds}",
          totalChecks, totalTimeouts, getTimeoutRate() * 100, heartbeatTimeout.getSeconds());
    }
  }
}
