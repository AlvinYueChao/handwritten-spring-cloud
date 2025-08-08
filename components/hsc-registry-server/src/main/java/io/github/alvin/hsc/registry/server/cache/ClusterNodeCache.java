package io.github.alvin.hsc.registry.server.cache;

import io.github.alvin.hsc.registry.server.model.ClusterNode;
import io.github.alvin.hsc.registry.server.model.NodeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Cluster Node Cache
 * 集群节点缓存服务
 * 
 * 提供集群节点的统一缓存管理，确保ClusterManagementService和ClusterSyncService
 * 使用相同的节点数据，避免数据不一致问题。
 * 
 * @author Alvin
 */
@Component
@ConditionalOnProperty(prefix = "hsc.registry.server.cluster", name = "enabled", havingValue = "true")
public class ClusterNodeCache {

    private static final Logger logger = LoggerFactory.getLogger(ClusterNodeCache.class);

    private final Map<String, ClusterNode> clusterNodes = new ConcurrentHashMap<>();

    /**
     * 添加或更新集群节点
     * 
     * @param node 集群节点
     */
    public void putNode(ClusterNode node) {
        if (node == null || node.getNodeId() == null) {
            logger.warn("Attempted to add null node or node with null ID");
            return;
        }
        
        ClusterNode existingNode = clusterNodes.get(node.getNodeId());
        clusterNodes.put(node.getNodeId(), node);
        
        if (existingNode == null) {
            logger.debug("Added new cluster node: {}", node.getNodeId());
        } else {
            logger.debug("Updated cluster node: {}", node.getNodeId());
        }
    }

    /**
     * 获取集群节点
     * 
     * @param nodeId 节点ID
     * @return 集群节点，如果不存在则返回null
     */
    public ClusterNode getNode(String nodeId) {
        return clusterNodes.get(nodeId);
    }

    /**
     * 移除集群节点
     * 
     * @param nodeId 节点ID
     * @return 被移除的节点，如果不存在则返回null
     */
    public ClusterNode removeNode(String nodeId) {
        ClusterNode removedNode = clusterNodes.remove(nodeId);
        if (removedNode != null) {
            logger.debug("Removed cluster node: {}", nodeId);
        }
        return removedNode;
    }

    /**
     * 获取所有集群节点
     * 
     * @return 所有集群节点的副本
     */
    public Collection<ClusterNode> getAllNodes() {
        return clusterNodes.values();
    }

    /**
     * 获取健康的集群节点
     * 
     * @return 健康的集群节点列表
     */
    public Collection<ClusterNode> getHealthyNodes() {
        return clusterNodes.values().stream()
                .filter(node -> node.getStatus() == NodeStatus.UP)
                .collect(Collectors.toList());
    }

    /**
     * 获取集群节点数量
     * 
     * @return 节点数量
     */
    public int getNodeCount() {
        return clusterNodes.size();
    }

    /**
     * 获取健康节点数量
     * 
     * @return 健康节点数量
     */
    public long getHealthyNodeCount() {
        return clusterNodes.values().stream()
                .filter(node -> node.getStatus() == NodeStatus.UP)
                .count();
    }

    /**
     * 检查节点是否存在
     * 
     * @param nodeId 节点ID
     * @return 如果节点存在返回true，否则返回false
     */
    public boolean containsNode(String nodeId) {
        return clusterNodes.containsKey(nodeId);
    }

    /**
     * 清空所有节点
     */
    public void clear() {
        int nodeCount = clusterNodes.size();
        clusterNodes.clear();
        logger.info("Cleared {} cluster nodes from cache", nodeCount);
    }

    /**
     * 获取节点映射的只读副本
     * 
     * @return 节点映射的不可变副本
     */
    public Map<String, ClusterNode> getNodesMap() {
        return Map.copyOf(clusterNodes);
    }

    /**
     * 批量添加节点
     * 
     * @param nodes 节点集合
     */
    public void putAllNodes(Collection<ClusterNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        
        for (ClusterNode node : nodes) {
            putNode(node);
        }
        
        logger.debug("Batch added {} cluster nodes", nodes.size());
    }

    /**
     * 更新节点状态
     * 
     * @param nodeId 节点ID
     * @param status 新状态
     * @return 如果节点存在并且状态更新成功返回true，否则返回false
     */
    public boolean updateNodeStatus(String nodeId, NodeStatus status) {
        ClusterNode node = clusterNodes.get(nodeId);
        if (node != null) {
            NodeStatus oldStatus = node.getStatus();
            node.setStatus(status);
            node.updateLastSeen();
            
            if (oldStatus != status) {
                logger.debug("Updated node {} status from {} to {}", nodeId, oldStatus, status);
            }
            return true;
        }
        return false;
    }
}