package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;

/**
 * Cluster Node Model
 * 集群节点模型
 * 
 * @author Alvin
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterNode {

    @NotBlank(message = "Node ID cannot be blank")
    private String nodeId;
    
    @NotBlank(message = "Host cannot be blank")
    private String host;
    
    @NotNull(message = "Port cannot be null")
    @Positive(message = "Port must be positive")
    private int port;
    
    @NotNull(message = "Status cannot be null")
    private NodeStatus status;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastSeen;
    
    private Map<String, Object> metadata;

    // Constructors
    public ClusterNode() {}

    public ClusterNode(String nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.status = NodeStatus.UP;
        this.lastSeen = Instant.now();
    }

    // Getters and Setters
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * 获取节点的完整地址
     */
    public String getAddress() {
        return String.format("%s:%d", host, port);
    }

    /**
     * 更新最后见到时间
     */
    public void updateLastSeen() {
        this.lastSeen = Instant.now();
    }

    @Override
    public String toString() {
        return String.format("ClusterNode{nodeId='%s', host='%s', port=%d, status=%s}", 
                nodeId, host, port, status);
    }
}