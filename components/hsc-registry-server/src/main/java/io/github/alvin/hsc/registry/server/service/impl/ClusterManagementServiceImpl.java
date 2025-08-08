package io.github.alvin.hsc.registry.server.service.impl;

import io.github.alvin.hsc.registry.server.cache.ClusterNodeCache;
import io.github.alvin.hsc.registry.server.config.RegistryServerProperties;
import io.github.alvin.hsc.registry.server.model.ClusterNode;
import io.github.alvin.hsc.registry.server.model.ClusterStatus;
import io.github.alvin.hsc.registry.server.model.NodeStatus;
import io.github.alvin.hsc.registry.server.service.ClusterManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cluster Management Service Implementation
 * 集群管理服务实现
 * 
 * 功能特性：
 * - 集群状态管理和监控
 * - 节点健康检查和故障检测
 * - 自动故障转移和主节点选举
 * - 集群拓扑管理
 * 
 * @author Alvin
 */
@Service
@ConditionalOnProperty(prefix = "hsc.registry.server.cluster", name = "enabled", havingValue = "true")
public class ClusterManagementServiceImpl implements ClusterManagementService {

    private static final Logger logger = LoggerFactory.getLogger(ClusterManagementServiceImpl.class);

    private final RegistryServerProperties properties;
    private final WebClient webClient;
    private final ClusterNodeCache clusterNodeCache;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final AtomicReference<ClusterNode> currentLeader = new AtomicReference<>();
    
    private ClusterNode currentNode;
    private String clusterId;
    private volatile boolean isRunning = false;

    @Autowired
    public ClusterManagementServiceImpl(RegistryServerProperties properties, WebClient.Builder webClientBuilder, ClusterNodeCache clusterNodeCache) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        this.clusterNodeCache = clusterNodeCache;
    }

    @PostConstruct
    public void initialize() {
        if (!properties.getCluster().isEnabled()) {
            logger.info("Cluster mode is disabled, skipping cluster management service initialization");
            return;
        }

        try {
            initializeCurrentNode();
            initializeClusterNodes();
            startClusterManagement().subscribe();
            
            logger.info("Cluster management service initialized successfully. Current node: {}", currentNode);
        } catch (Exception e) {
            logger.error("Failed to initialize cluster management service", e);
            throw new RuntimeException("Cluster management service initialization failed", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        stopClusterManagement().subscribe();
    }

    @Override
    public Mono<Void> startClusterManagement() {
        return Mono.fromRunnable(() -> {
            if (isRunning) {
                logger.warn("Cluster management is already running");
                return;
            }

            isRunning = true;
            startHealthCheckScheduler();
            startFailoverMonitor();
            startLeaderElection();
            
            logger.info("Cluster management started successfully");
        });
    }

    @Override
    public Mono<Void> stopClusterManagement() {
        return Mono.fromRunnable(() -> {
            if (!isRunning) {
                logger.warn("Cluster management is not running");
                return;
            }

            isRunning = false;
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.info("Cluster management stopped successfully");
        });
    }

    @Override
    public Mono<ClusterStatus> getClusterStatus() {
        List<ClusterNode> nodes = List.copyOf(clusterNodeCache.getAllNodes());
        ClusterStatus status = new ClusterStatus(clusterId, nodes, currentNode);
        return Mono.just(status);
    }

    @Override
    public Mono<Void> addNode(ClusterNode node) {
        if (node == null || node.getNodeId() == null) {
            return Mono.error(new IllegalArgumentException("Invalid cluster node"));
        }

        return Mono.fromRunnable(() -> {
            node.updateLastSeen();
            clusterNodeCache.putNode(node);
            logger.info("Node added to cluster: {}", node);
        });
    }

    @Override
    public Mono<Void> removeNode(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Node ID cannot be null or empty"));
        }

        return Mono.fromRunnable(() -> {
            ClusterNode removedNode = clusterNodeCache.removeNode(nodeId);
            if (removedNode != null) {
                logger.info("Node removed from cluster: {}", removedNode);
                
                // 如果移除的是主节点，触发重新选举
                if (currentLeader.get() != null && nodeId.equals(currentLeader.get().getNodeId())) {
                    currentLeader.set(null);
                    electLeader().subscribe();
                }
            }
        });
    }

    @Override
    public Flux<ClusterNode> getAllNodes() {
        return Flux.fromIterable(clusterNodeCache.getAllNodes());
    }

    @Override
    public Flux<ClusterNode> getHealthyNodes() {
        return Flux.fromIterable(clusterNodeCache.getHealthyNodes());
    }

    @Override
    public Mono<Void> performFailover(String failedNodeId) {
        return Mono.fromRunnable(() -> {
            ClusterNode failedNode = clusterNodeCache.getNode(failedNodeId);
            if (failedNode != null) {
                clusterNodeCache.updateNodeStatus(failedNodeId, NodeStatus.DOWN);
                logger.warn("Performing failover for failed node: {}", failedNodeId);
                
                // 如果故障节点是主节点，选举新的主节点
                if (currentLeader.get() != null && failedNodeId.equals(currentLeader.get().getNodeId())) {
                    electLeader().subscribe();
                }
            }
        });
    }

    @Override
    public Mono<Boolean> needsFailover() {
        return Mono.fromCallable(() -> {
            long healthyCount = clusterNodeCache.getHealthyNodeCount();
            long totalCount = clusterNodeCache.getNodeCount();
            // 如果健康节点数量少于总节点数量的一半，需要故障转移
            return healthyCount < totalCount / 2;
        });
    }

    @Override
    public Mono<ClusterNode> electLeader() {
        return getHealthyNodes()
                .collectList()
                .flatMap(healthyNodes -> {
                    if (healthyNodes.isEmpty()) {
                        logger.warn("No healthy nodes available for leader election");
                        return Mono.empty();
                    }

                    // 选择节点ID最小的作为主节点（简单的选举算法）
                    ClusterNode newLeader = healthyNodes.stream()
                            .min(Comparator.comparing(ClusterNode::getNodeId))
                            .orElse(null);

                    if (newLeader != null) {
                        currentLeader.set(newLeader);
                        logger.info("New leader elected: {}", newLeader.getNodeId());
                        return Mono.just(newLeader);
                    }

                    return Mono.empty();
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
        
        // 生成集群ID
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
     * 启动健康检查调度器
     */
    private void startHealthCheckScheduler() {
        Duration interval = properties.getCluster().getSyncInterval();
        
        scheduler.scheduleWithFixedDelay(
                this::performClusterHealthCheck,
                interval.toSeconds(),
                interval.toSeconds(),
                TimeUnit.SECONDS
        );
        
        logger.info("Cluster health check scheduler started with interval: {}", interval);
    }

    /**
     * 启动故障转移监控器
     */
    private void startFailoverMonitor() {
        Duration interval = Duration.ofSeconds(10); // 故障转移检查间隔
        
        scheduler.scheduleWithFixedDelay(
                this::checkFailoverNeeded,
                interval.toSeconds(),
                interval.toSeconds(),
                TimeUnit.SECONDS
        );
        
        logger.info("Failover monitor started with interval: {}", interval);
    }

    /**
     * 启动主节点选举
     */
    private void startLeaderElection() {
        // 初始选举
        electLeader().subscribe();
        
        // 定期检查主节点状态
        Duration interval = Duration.ofSeconds(30);
        
        scheduler.scheduleWithFixedDelay(
                this::checkLeaderStatus,
                interval.toSeconds(),
                interval.toSeconds(),
                TimeUnit.SECONDS
        );
        
        logger.info("Leader election started");
    }

    /**
     * 执行集群健康检查
     */
    private void performClusterHealthCheck() {
        logger.debug("Performing cluster health check");
        
        clusterNodeCache.getAllNodes().parallelStream()
                .filter(node -> !node.getNodeId().equals(currentNode.getNodeId()))
                .forEach(this::checkNodeHealth);
    }

    /**
     * 检查单个节点健康状态
     */
    private void checkNodeHealth(ClusterNode node) {
        String healthUrl = String.format("http://%s:%d/api/v1/cluster/health", node.getHost(), node.getPort());
        
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
                                logger.warn("Node {} health check failed: {}", node.getNodeId(), error.getMessage());
                                clusterNodeCache.updateNodeStatus(node.getNodeId(), NodeStatus.DOWN);
                                
                                // 触发故障转移检查
                                performFailover(node.getNodeId()).subscribe();
                            }
                        }
                );
    }

    /**
     * 检查是否需要故障转移
     */
    private void checkFailoverNeeded() {
        needsFailover().subscribe(needsFailover -> {
            if (needsFailover) {
                logger.warn("Cluster needs failover - insufficient healthy nodes");
                // 这里可以实现更复杂的故障转移逻辑
            }
        });
    }

    /**
     * 检查主节点状态
     */
    private void checkLeaderStatus() {
        ClusterNode leader = currentLeader.get();
        if (leader == null || leader.getStatus() != NodeStatus.UP) {
            logger.info("Leader is unavailable, triggering re-election");
            electLeader().subscribe();
        }
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
     * 获取当前主节点
     */
    public ClusterNode getCurrentLeader() {
        return currentLeader.get();
    }

    /**
     * 获取当前节点
     */
    public ClusterNode getCurrentNode() {
        return currentNode;
    }
}