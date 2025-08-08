package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.ClusterNode;
import io.github.alvin.hsc.registry.server.model.ClusterStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cluster Management Service Interface
 * 集群管理服务接口
 * 
 * @author Alvin
 */
public interface ClusterManagementService {

    /**
     * 启动集群管理
     * 
     * @return 启动结果
     */
    Mono<Void> startClusterManagement();

    /**
     * 停止集群管理
     * 
     * @return 停止结果
     */
    Mono<Void> stopClusterManagement();

    /**
     * 获取集群状态
     * 
     * @return 集群状态
     */
    Mono<ClusterStatus> getClusterStatus();

    /**
     * 添加节点到集群
     * 
     * @param node 集群节点
     * @return 添加结果
     */
    Mono<Void> addNode(ClusterNode node);

    /**
     * 从集群移除节点
     * 
     * @param nodeId 节点ID
     * @return 移除结果
     */
    Mono<Void> removeNode(String nodeId);

    /**
     * 获取所有集群节点
     * 
     * @return 集群节点流
     */
    Flux<ClusterNode> getAllNodes();

    /**
     * 获取健康的集群节点
     * 
     * @return 健康节点流
     */
    Flux<ClusterNode> getHealthyNodes();

    /**
     * 执行故障转移
     * 
     * @param failedNodeId 故障节点ID
     * @return 故障转移结果
     */
    Mono<Void> performFailover(String failedNodeId);

    /**
     * 检查是否需要故障转移
     * 
     * @return 是否需要故障转移
     */
    Mono<Boolean> needsFailover();

    /**
     * 选举新的主节点
     * 
     * @return 新的主节点
     */
    Mono<ClusterNode> electLeader();

    /**
     * 获取当前节点信息
     * 
     * @return 当前节点
     */
    ClusterNode getCurrentNode();
}