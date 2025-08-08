package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.model.ServiceEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cluster Sync Service Interface
 * 集群同步服务接口
 * 
 * 专注于集群间的数据同步和事件传播
 * 
 * @author Alvin
 */
public interface ClusterSyncService {

    /**
     * 同步事件到集群中的其他节点
     * 
     * @param event 服务事件
     * @return 同步结果
     */
    Mono<Void> syncToCluster(ServiceEvent event);

    /**
     * 从集群中的其他节点接收事件
     * 
     * @return 服务事件流
     */
    Flux<ServiceEvent> receiveFromCluster();

    /**
     * 启动集群同步服务
     * 
     * @return 启动结果
     */
    Mono<Void> startSync();

    /**
     * 停止集群同步服务
     * 
     * @return 停止结果
     */
    Mono<Void> stopSync();

    /**
     * 处理从其他节点接收到的集群事件
     * 
     * @param event 服务事件
     */
    void handleClusterEvent(ServiceEvent event);
}