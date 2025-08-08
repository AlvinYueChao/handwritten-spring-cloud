package io.github.alvin.hsc.registry.server.service.impl;

import io.github.alvin.hsc.registry.server.cache.ClusterNodeCache;
import io.github.alvin.hsc.registry.server.config.RegistryServerProperties;
import io.github.alvin.hsc.registry.server.model.ClusterNode;
import io.github.alvin.hsc.registry.server.model.ClusterStatus;
import io.github.alvin.hsc.registry.server.model.NodeStatus;
import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.service.ClusterSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Cluster Sync Service Implementation
 * 集群同步服务实现
 * 
 * 功能特性：
 * - 支持集群节点发现和管理
 * - 实现服务事件的集群同步
 * - 提供节点健康检查和故障检测
 * - 支持动态节点加入和离开
 * 
 * @author Alvin
 */
@Service
@ConditionalOnProperty(prefix = "hsc.registry.server.cluster", name = "enabled", havingValue = "true")
public class ClusterSyncServiceImpl implements ClusterSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ClusterSyncServiceImpl.class);

    private final RegistryServerProperties properties;
    private final WebClient webClient;
    private final ClusterNodeCache clusterNodeCache;
    private final Sinks.Many<ServiceEvent> eventSink = Sinks.many().multicast().onBackpressureBuffer();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private ClusterNode currentNode;
    private String clusterId;

    @Autowired
    public ClusterSyncServiceImpl(RegistryServerProperties properties, WebClient.Builder webClientBuilder, ClusterNodeCache clusterNodeCache) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        this.clusterNodeCache = clusterNodeCache;
    }

    @PostConstruct
    public void initialize() {
        if (!properties.getCluster().isEnabled()) {
            logger.info("Cluster mode is disabled, skipping cluster sync service initialization");
            return;
        }

        try {
            initializeCurrentNode();
            initializeClusterNodes();
            startNodeHealthCheck();
            startEventSync();
            
            logger.info("Cluster sync service initialized successfully. Current node: {}, Cluster nodes: {}", 
                    currentNode, clusterNodeCache.getNodeCount());
        } catch (Exception e) {
            logger.error("Failed to initialize cluster sync service", e);
            throw new RuntimeException("Cluster sync service initialization failed", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down cluster sync service");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        eventSink.tryEmitComplete();
    }

    @Override
    public Mono<Void> syncToCluster(ServiceEvent event) {
        if (!properties.getCluster().isEnabled() || clusterNodeCache.getNodeCount() == 0) {
            return Mono.empty();
        }

        logger.debug("Syncing event to cluster: {}", event);

        List<Mono<Void>> syncTasks = clusterNodeCache.getHealthyNodes().stream()
                .filter(node -> !node.getNodeId().equals(currentNode.getNodeId()))
                .map(node -> syncEventToNode(event, node))
                .collect(Collectors.toList());

        return Flux.merge(syncTasks)
                .then()
                .doOnSuccess(v -> logger.debug("Event synced to cluster successfully: {}", event.getEventId()))
                .doOnError(e -> logger.warn("Failed to sync event to some cluster nodes: {}", event.getEventId(), e));
    }

    @Override
    public Flux<ServiceEvent> receiveFromCluster() {
        return eventSink.asFlux()
                .doOnSubscribe(s -> logger.debug("Client subscribed to cluster events"))
                .doOnCancel(() -> logger.debug("Client unsubscribed from cluster events"));
    }

    @Override
    public Mono<Void> startSync() {
        return Mono.fromRunnable(() -> {
            if (!properties.getCluster().isEnabled()) {
                logger.info("Cluster mode is disabled, skipping sync service start");
                return;
            }
            logger.info("Cluster sync service started successfully");
        });
    }

    @Override
    public Mono<Void> stopSync() {
        return Mono.fromRunnable(() -> {
            logger.info("Cluster sync service stopped successfully");
        });
    }

    /**
     * 初始化当前节点信息
     */
    private void initializeCurrentNode() throws UnknownHostException {
        String nodeId = generateNodeId();
        String host = InetAddress.getLocalHost().getHostAddress();
        int port = properties.getPort();
        
        currentNode = new ClusterNode(nodeId, host, port);
        currentNode.setStatus(NodeStatus.UP);
        
        // 生成集群ID（如果是第一个节点）
        clusterId = generateClusterId();
        
        logger.info("Current node initialized: {}", currentNode);
    }

    /**
     * 初始化集群节点
     */
    private void initializeClusterNodes() {
        List<String> configuredNodes = properties.getCluster().getNodes();
        
        for (String nodeAddress : configuredNodes) {
            try {
                String[] parts = nodeAddress.split(":");
                if (parts.length != 2) {
                    logger.warn("Invalid node address format: {}, expected format: host:port", nodeAddress);
                    continue;
                }
                
                String host = parts[0].trim();
                int port = Integer.parseInt(parts[1].trim());
                String nodeId = generateNodeId(host, port);
                
                // 跳过当前节点
                if (nodeId.equals(currentNode.getNodeId())) {
                    continue;
                }
                
                ClusterNode node = new ClusterNode(nodeId, host, port);
                node.setStatus(NodeStatus.UNKNOWN);
                clusterNodeCache.putNode(node);
                
                logger.info("Added cluster node: {}", node);
            } catch (Exception e) {
                logger.warn("Failed to parse node address: {}", nodeAddress, e);
            }
        }
        
        // 将当前节点也加入集群节点列表
        clusterNodeCache.putNode(currentNode);
    }

    /**
     * 启动节点健康检查
     */
    private void startNodeHealthCheck() {
        Duration interval = properties.getCluster().getSyncInterval();
        
        scheduler.scheduleWithFixedDelay(
                this::performNodeHealthCheck,
                interval.toSeconds(),
                interval.toSeconds(),
                TimeUnit.SECONDS
        );
        
        logger.info("Node health check started with interval: {}", interval);
    }

    /**
     * 启动事件同步
     */
    private void startEventSync() {
        // 这里可以实现定期的状态同步逻辑
        // 目前主要依赖实时的事件推送
        logger.info("Event sync mechanism started");
    }

    /**
     * 执行节点健康检查
     */
    private void performNodeHealthCheck() {
        logger.debug("Performing cluster node health check");
        
        clusterNodeCache.getAllNodes().parallelStream()
                .filter(node -> !node.getNodeId().equals(currentNode.getNodeId()))
                .forEach(this::checkNodeHealth);
    }

    /**
     * 检查单个节点健康状态
     */
    private void checkNodeHealth(ClusterNode node) {
        String healthUrl = String.format("http://%s:%d/actuator/health", node.getHost(), node.getPort());
        
        webClient.get()
                .uri(healthUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .subscribe(
                        response -> {
                            if (node.getStatus() != NodeStatus.UP) {
                                logger.info("Node {} is back online", node.getNodeId());
                            }
                            clusterNodeCache.updateNodeStatus(node.getNodeId(), NodeStatus.UP);
                        },
                        error -> {
                            if (node.getStatus() == NodeStatus.UP) {
                                logger.warn("Node {} is down: {}", node.getNodeId(), error.getMessage());
                            }
                            clusterNodeCache.updateNodeStatus(node.getNodeId(), NodeStatus.DOWN);
                        }
                );
    }

    /**
     * 同步事件到指定节点
     */
    private Mono<Void> syncEventToNode(ServiceEvent event, ClusterNode node) {
        String syncUrl = String.format("http://%s:%d/api/v1/cluster/events", node.getHost(), node.getPort());
        
        return webClient.post()
                .uri(syncUrl)
                .bodyValue(event)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(error -> {
                    logger.debug("Failed to sync event to node {}: {}", node.getNodeId(), error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 生成节点ID
     */
    private String generateNodeId() {
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            int port = properties.getPort();
            return generateNodeId(host, port);
        } catch (UnknownHostException e) {
            return "node-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * 生成节点ID
     */
    private String generateNodeId(String host, int port) {
        return String.format("node-%s-%d", host.replace(".", "-"), port);
    }

    /**
     * 生成集群ID
     */
    private String generateClusterId() {
        return "cluster-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 处理从其他节点接收到的事件
     * 这个方法会被集群事件接收端点调用
     */
    @Override
    public void handleClusterEvent(ServiceEvent event) {
        logger.debug("Received cluster event: {}", event);
        eventSink.tryEmitNext(event);
    }

    /**
     * 获取当前节点信息
     */
    public ClusterNode getCurrentNode() {
        return currentNode;
    }

    /**
     * 获取所有集群节点
     */
    public Map<String, ClusterNode> getClusterNodes() {
        return clusterNodeCache.getNodesMap();
    }
}