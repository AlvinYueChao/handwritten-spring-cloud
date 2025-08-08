package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.model.ClusterNode;
import io.github.alvin.hsc.registry.server.model.ClusterStatus;
import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.service.ClusterSyncService;
import io.github.alvin.hsc.registry.server.service.ClusterManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;

/**
 * Cluster Management Controller
 * 集群管理控制器
 * 
 * 提供集群相关的REST API：
 * - 接收其他节点的事件同步
 * - 查询集群状态信息
 * - 节点加入和离开管理
 * 
 * @author Alvin
 */
@RestController
@RequestMapping("/api/v1/cluster")
@ConditionalOnProperty(prefix = "hsc.registry.server.cluster", name = "enabled", havingValue = "true")
public class ClusterController {

    private static final Logger logger = LoggerFactory.getLogger(ClusterController.class);

    private final ClusterSyncService clusterSyncService;
    private final ClusterManagementService clusterManagementService;

    @Autowired
    public ClusterController(ClusterSyncService clusterSyncService, ClusterManagementService clusterManagementService) {
        this.clusterSyncService = clusterSyncService;
        this.clusterManagementService = clusterManagementService;
    }

    /**
     * 接收来自其他集群节点的事件
     * 
     * @param event 服务事件
     * @return 处理结果
     */
    @PostMapping("/events")
    public Mono<ResponseEntity<Void>> receiveClusterEvent(@Valid @RequestBody ServiceEvent event) {
        logger.debug("Received cluster event from remote node: {}", event);
        
        return Mono.fromRunnable(() -> clusterSyncService.handleClusterEvent(event))
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .doOnSuccess(v -> logger.debug("Cluster event processed successfully: {}", event.getEventId()))
                .doOnError(e -> logger.error("Failed to process cluster event: {}", event.getEventId(), e));
    }

    /**
     * 获取集群状态
     * 
     * @return 集群状态信息
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<ClusterStatus>> getClusterStatus() {
        return clusterManagementService.getClusterStatus()
                .map(ResponseEntity::ok)
                .doOnSuccess(v -> logger.debug("Cluster status retrieved successfully"))
                .doOnError(e -> logger.error("Failed to retrieve cluster status", e));
    }

    /**
     * 获取当前节点信息
     * 
     * @return 当前节点信息
     */
    @GetMapping("/current-node")
    public ResponseEntity<ClusterNode> getCurrentNode() {
        ClusterNode currentNode = clusterManagementService.getCurrentNode();
        return ResponseEntity.ok(currentNode);
    }

    /**
     * 获取所有集群节点
     * 
     * @return 集群节点列表
     */
    @GetMapping("/nodes")
    public Flux<ClusterNode> getClusterNodes() {
        return clusterManagementService.getAllNodes();
    }

    /**
     * 节点加入集群
     * 
     * @param node 要加入的节点信息
     * @return 加入结果
     */
    @PostMapping("/join")
    public Mono<ResponseEntity<Void>> joinCluster(@Valid @RequestBody ClusterNode node) {
        logger.info("Node requesting to join cluster: {}", node);
        
        return clusterManagementService.addNode(node)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .doOnSuccess(v -> logger.info("Node joined cluster successfully: {}", node.getNodeId()))
                .doOnError(e -> logger.error("Failed to join node to cluster: {}", node.getNodeId(), e));
    }

    /**
     * 订阅集群事件流
     * 
     * @return 事件流
     */
    @GetMapping(value = "/events/stream", produces = "text/event-stream")
    public Flux<ServiceEvent> streamClusterEvents() {
        logger.debug("Client subscribed to cluster event stream");
        
        return clusterSyncService.receiveFromCluster()
                .doOnSubscribe(s -> logger.debug("Started streaming cluster events"))
                .doOnCancel(() -> logger.debug("Client unsubscribed from cluster event stream"))
                .doOnError(e -> logger.error("Error in cluster event stream", e));
    }

    /**
     * 健康检查端点
     * 供其他集群节点检查当前节点状态
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Object> health() {
        return ResponseEntity.ok().body(java.util.Map.of(
                "status", "UP",
                "timestamp", java.time.Instant.now(),
                "node", clusterManagementService.getCurrentNode()
        ));
    }
}