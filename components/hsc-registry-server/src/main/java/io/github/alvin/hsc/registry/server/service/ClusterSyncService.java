package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import io.github.alvin.hsc.registry.server.model.ClusterStatus;
import io.github.alvin.hsc.registry.server.model.ClusterNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cluster Sync Service Interface
 * 集群同步服务接口
 * 
 * @author Alvin
 */
public interface ClusterSyncService {

    /**
     * 同步事件到集群
     * 
     * @param event 服务事件
     * @return 同步结果
     */
    Mono<Void> syncToCluster(ServiceEvent event);

    /**
     * 从集群接收事件
     * 
     * @return 服务事件流
     */
    Flux<ServiceEvent> receiveFromCluster();

    /**
     * 获取集群状态
     * 
     * @return 集群状态
     */
    Mono<ClusterStatus> getClusterStatus();

    /**
     * 加入集群
     * 
     * @param node 集群节点
     * @return 加入结果
     */
    Mono<Void> joinCluster(ClusterNode node);
}