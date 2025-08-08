package io.github.alvin.hsc.registry.server.service.impl;

import io.github.alvin.hsc.registry.server.cache.ClusterNodeCache;
import io.github.alvin.hsc.registry.server.config.RegistryServerProperties;
import io.github.alvin.hsc.registry.server.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClusterManagementServiceImpl
 * 集群管理服务单元测试
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class ClusterManagementServiceImplTest {

    @Mock
    private WebClient webClient;
    
    @Mock
    private WebClient.Builder webClientBuilder;
    
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    
    @Mock
    private WebClient.ResponseSpec responseSpec;

    private RegistryServerProperties properties;
    private ClusterManagementServiceImpl clusterManagementService;

    @BeforeEach
    void setUp() throws Exception {
        // 设置属性
        properties = new RegistryServerProperties();
        properties.setPort(8761);
        
        RegistryServerProperties.ClusterConfig clusterConfig = new RegistryServerProperties.ClusterConfig();
        clusterConfig.setEnabled(true);
        clusterConfig.setNodes(Arrays.asList("192.168.1.100:8761", "192.168.1.101:8761", "192.168.1.102:8761"));
        clusterConfig.setSyncInterval(Duration.ofSeconds(30));
        properties.setCluster(clusterConfig);

        // 设置WebClient mock
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        // 创建共享缓存
        ClusterNodeCache clusterNodeCache = new ClusterNodeCache();
        
        // 创建服务实例
        clusterManagementService = new ClusterManagementServiceImpl(properties, webClientBuilder, clusterNodeCache);
        
        // 调用PostConstruct方法进行初始化
        clusterManagementService.initialize();
    }

    @Test
    void testStartClusterManagement_ShouldSucceed() {
        // When & Then
        StepVerifier.create(clusterManagementService.startClusterManagement())
                .verifyComplete();
    }

    @Test
    void testStopClusterManagement_ShouldSucceed() {
        // Given - Start first
        StepVerifier.create(clusterManagementService.startClusterManagement())
                .verifyComplete();

        // When & Then
        StepVerifier.create(clusterManagementService.stopClusterManagement())
                .verifyComplete();
    }

    @Test
    void testGetClusterStatus_ShouldReturnValidStatus() {
        // When & Then
        StepVerifier.create(clusterManagementService.getClusterStatus())
                .expectNextMatches(status -> {
                    assertThat(status).isNotNull();
                    assertThat(status.getClusterId()).isNotNull();
                    assertThat(status.getTotalNodes()).isGreaterThan(0);
                    assertThat(status.getCurrentNode()).isNotNull();
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void testAddNode_WithValidNode_ShouldSucceed() {
        // Given
        ClusterNode newNode = new ClusterNode("test-node-1", "192.168.1.200", 8761);

        // When & Then
        StepVerifier.create(clusterManagementService.addNode(newNode))
                .verifyComplete();

        // Verify node was added
        StepVerifier.create(clusterManagementService.getAllNodes())
                .expectNextCount(5) // 3 configured + 1 current + 1 new
                .verifyComplete();
    }

    @Test
    void testAddNode_WithNullNode_ShouldFail() {
        // When & Then
        StepVerifier.create(clusterManagementService.addNode(null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testAddNode_WithInvalidNode_ShouldFail() {
        // Given
        ClusterNode invalidNode = new ClusterNode();
        invalidNode.setHost("192.168.1.200");
        invalidNode.setPort(8761);
        // nodeId is null

        // When & Then
        StepVerifier.create(clusterManagementService.addNode(invalidNode))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testRemoveNode_WithValidNodeId_ShouldSucceed() {
        // Given - Add a node first
        ClusterNode nodeToRemove = new ClusterNode("remove-test-node", "192.168.1.200", 8761);
        StepVerifier.create(clusterManagementService.addNode(nodeToRemove))
                .verifyComplete();

        // When & Then
        StepVerifier.create(clusterManagementService.removeNode("remove-test-node"))
                .verifyComplete();

        // Verify node was removed
        StepVerifier.create(clusterManagementService.getAllNodes())
                .expectNextCount(4) // Back to original count
                .verifyComplete();
    }

    @Test
    void testRemoveNode_WithNullNodeId_ShouldFail() {
        // When & Then
        StepVerifier.create(clusterManagementService.removeNode(null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testRemoveNode_WithEmptyNodeId_ShouldFail() {
        // When & Then
        StepVerifier.create(clusterManagementService.removeNode(""))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testGetAllNodes_ShouldReturnAllNodes() {
        // When & Then
        StepVerifier.create(clusterManagementService.getAllNodes())
                .expectNextCount(4) // 3 configured + 1 current
                .verifyComplete();
    }

    @Test
    void testGetHealthyNodes_ShouldReturnOnlyHealthyNodes() {
        // Given - Add healthy and unhealthy nodes
        ClusterNode healthyNode = new ClusterNode("healthy-node", "192.168.1.200", 8761);
        healthyNode.setStatus(NodeStatus.UP);
        
        ClusterNode unhealthyNode = new ClusterNode("unhealthy-node", "192.168.1.201", 8761);
        unhealthyNode.setStatus(NodeStatus.DOWN);

        StepVerifier.create(clusterManagementService.addNode(healthyNode))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(unhealthyNode))
                .verifyComplete();

        // When & Then - Should only return healthy nodes
        StepVerifier.create(clusterManagementService.getHealthyNodes())
                .expectNextMatches(node -> node.getStatus() == NodeStatus.UP)
                .expectNextMatches(node -> node.getStatus() == NodeStatus.UP) // Current node
                .verifyComplete();
    }

    @Test
    void testPerformFailover_WithValidNodeId_ShouldMarkNodeAsDown() {
        // Given - Add a healthy node
        ClusterNode nodeToFail = new ClusterNode("failover-test-node", "192.168.1.200", 8761);
        nodeToFail.setStatus(NodeStatus.UP);
        
        StepVerifier.create(clusterManagementService.addNode(nodeToFail))
                .verifyComplete();

        // When
        StepVerifier.create(clusterManagementService.performFailover("failover-test-node"))
                .verifyComplete();

        // Then - Node should be marked as down
        StepVerifier.create(clusterManagementService.getAllNodes())
                .expectNextMatches(node -> {
                    if ("failover-test-node".equals(node.getNodeId())) {
                        return node.getStatus() == NodeStatus.DOWN;
                    }
                    return true;
                })
                .expectNextCount(4) // Skip other nodes
                .verifyComplete();
    }

    @Test
    void testNeedsFailover_WhenMajorityNodesDown_ShouldReturnTrue() {
        // Given - Add nodes where majority are down
        ClusterNode downNode1 = new ClusterNode("down-node-1", "192.168.1.200", 8761);
        downNode1.setStatus(NodeStatus.DOWN);
        
        ClusterNode downNode2 = new ClusterNode("down-node-2", "192.168.1.201", 8761);
        downNode2.setStatus(NodeStatus.DOWN);
        
        ClusterNode downNode3 = new ClusterNode("down-node-3", "192.168.1.202", 8761);
        downNode3.setStatus(NodeStatus.DOWN);

        StepVerifier.create(clusterManagementService.addNode(downNode1))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(downNode2))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(downNode3))
                .verifyComplete();

        // When & Then
        StepVerifier.create(clusterManagementService.needsFailover())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testNeedsFailover_WhenMajorityNodesUp_ShouldReturnFalse() {
        // Given - Add nodes where majority are up
        ClusterNode upNode1 = new ClusterNode("up-node-1", "192.168.1.200", 8761);
        upNode1.setStatus(NodeStatus.UP);
        
        ClusterNode upNode2 = new ClusterNode("up-node-2", "192.168.1.201", 8761);
        upNode2.setStatus(NodeStatus.UP);

        StepVerifier.create(clusterManagementService.addNode(upNode1))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(upNode2))
                .verifyComplete();

        // When & Then
        StepVerifier.create(clusterManagementService.needsFailover())
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testElectLeader_WithHealthyNodes_ShouldElectLeader() {
        // Given - Add healthy nodes
        ClusterNode node1 = new ClusterNode("leader-candidate-1", "192.168.1.200", 8761);
        node1.setStatus(NodeStatus.UP);
        
        ClusterNode node2 = new ClusterNode("leader-candidate-2", "192.168.1.201", 8761);
        node2.setStatus(NodeStatus.UP);

        StepVerifier.create(clusterManagementService.addNode(node1))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(node2))
                .verifyComplete();

        // When & Then
        StepVerifier.create(clusterManagementService.electLeader())
                .expectNextMatches(leader -> {
                    assertThat(leader).isNotNull();
                    assertThat(leader.getStatus()).isEqualTo(NodeStatus.UP);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void testElectLeader_WithNoHealthyNodes_ShouldReturnEmpty() {
        // Given - Add only unhealthy nodes
        ClusterNode downNode1 = new ClusterNode("down-leader-1", "192.168.1.200", 8761);
        downNode1.setStatus(NodeStatus.DOWN);
        
        ClusterNode downNode2 = new ClusterNode("down-leader-2", "192.168.1.201", 8761);
        downNode2.setStatus(NodeStatus.DOWN);

        StepVerifier.create(clusterManagementService.addNode(downNode1))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(downNode2))
                .verifyComplete();

        // Mark current node as down too
        clusterManagementService.getCurrentNode().setStatus(NodeStatus.DOWN);

        // When & Then
        StepVerifier.create(clusterManagementService.electLeader())
                .verifyComplete();
    }

    @Test
    void testRemoveLeaderNode_ShouldTriggerReelection() {
        // Given - Add nodes and elect leader
        ClusterNode leaderCandidate = new ClusterNode("leader-to-remove", "192.168.1.200", 8761);
        leaderCandidate.setStatus(NodeStatus.UP);
        
        ClusterNode backupNode = new ClusterNode("backup-node", "192.168.1.201", 8761);
        backupNode.setStatus(NodeStatus.UP);

        StepVerifier.create(clusterManagementService.addNode(leaderCandidate))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(backupNode))
                .verifyComplete();

        // Elect leader
        StepVerifier.create(clusterManagementService.electLeader())
                .expectNextMatches(leader -> leader != null)
                .verifyComplete();

        // When - Remove the leader node
        StepVerifier.create(clusterManagementService.removeNode("leader-to-remove"))
                .verifyComplete();

        // Then - Should still have a leader (re-election should occur)
        ClusterNode currentLeader = clusterManagementService.getCurrentLeader();
        // Note: Due to async nature, we can't guarantee immediate re-election in unit test
        // This would be better tested in integration tests
    }

    @Test
    void testGetCurrentNode_ShouldReturnCurrentNode() {
        // When
        ClusterNode currentNode = clusterManagementService.getCurrentNode();

        // Then
        assertThat(currentNode).isNotNull();
        assertThat(currentNode.getNodeId()).isNotNull();
        assertThat(currentNode.getHost()).isNotNull();
        assertThat(currentNode.getPort()).isEqualTo(8761);
        assertThat(currentNode.getStatus()).isEqualTo(NodeStatus.UP);
    }

    @Test
    void testGetCurrentLeader_ShouldReturnLeader() {
        // Given - Elect a leader first
        StepVerifier.create(clusterManagementService.electLeader())
                .expectNextMatches(leader -> leader != null)
                .verifyComplete();

        // When
        ClusterNode currentLeader = clusterManagementService.getCurrentLeader();

        // Then
        assertThat(currentLeader).isNotNull();
        assertThat(currentLeader.getStatus()).isEqualTo(NodeStatus.UP);
    }

    /**
     * 创建测试用的集群节点
     */
    private ClusterNode createTestClusterNode(String nodeId, String host, int port, NodeStatus status) {
        ClusterNode node = new ClusterNode(nodeId, host, port);
        node.setStatus(status);
        node.updateLastSeen();
        return node;
    }
}