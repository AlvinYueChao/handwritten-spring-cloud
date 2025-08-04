package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Cluster Status Model
 * 集群状态模型
 * 
 * @author Alvin
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterStatus {

    private String clusterId;
    private List<ClusterNode> nodes;
    private ClusterNode currentNode;
    private int totalNodes;
    private int healthyNodes;

    // Constructors
    public ClusterStatus() {}

    public ClusterStatus(String clusterId, List<ClusterNode> nodes, ClusterNode currentNode) {
        this.clusterId = clusterId;
        this.nodes = nodes;
        this.currentNode = currentNode;
        this.totalNodes = nodes != null ? nodes.size() : 0;
        this.healthyNodes = nodes != null ? (int) nodes.stream()
                .filter(node -> node.getStatus() == NodeStatus.UP)
                .count() : 0;
    }

    // Getters and Setters
    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public List<ClusterNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<ClusterNode> nodes) {
        this.nodes = nodes;
        this.totalNodes = nodes != null ? nodes.size() : 0;
        this.healthyNodes = nodes != null ? (int) nodes.stream()
                .filter(node -> node.getStatus() == NodeStatus.UP)
                .count() : 0;
    }

    public ClusterNode getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(ClusterNode currentNode) {
        this.currentNode = currentNode;
    }

    public int getTotalNodes() {
        return totalNodes;
    }

    public void setTotalNodes(int totalNodes) {
        this.totalNodes = totalNodes;
    }

    public int getHealthyNodes() {
        return healthyNodes;
    }

    public void setHealthyNodes(int healthyNodes) {
        this.healthyNodes = healthyNodes;
    }

    /**
     * 检查集群是否健康
     */
    public boolean isHealthy() {
        return healthyNodes > totalNodes / 2;
    }

    @Override
    public String toString() {
        return String.format("ClusterStatus{clusterId='%s', totalNodes=%d, healthyNodes=%d}", 
                clusterId, totalNodes, healthyNodes);
    }
}