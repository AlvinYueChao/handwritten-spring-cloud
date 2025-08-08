package io.github.alvin.hsc.registry.server.service;

import io.github.alvin.hsc.registry.server.cache.ClusterNodeCache;
import io.github.alvin.hsc.registry.server.config.RegistryServerProperties;
import io.github.alvin.hsc.registry.server.model.*;
import io.github.alvin.hsc.registry.server.service.impl.ClusterManagementServiceImpl;
import io.github.alvin.hsc.registry.server.service.impl.ClusterSyncServiceImpl;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Cluster Integration Tests
 * 集群功能集成测试
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class ClusterIntegrationTest {

    @Mock
    private WebClient.Builder webClientBuilder;
    
    @Mock
    private WebClient webClient;

    private RegistryServerProperties properties;
    private ClusterNodeCache clusterNodeCache;
    private ClusterManagementServiceImpl clusterManagementService;
    private ClusterSyncServiceImpl clusterSyncService;

    @BeforeEach
    void setUp() throws Exception {
        // 设置属性
        properties = new RegistryServerProperties();
        properties.setPort(8761);
        
        RegistryServerProperties.ClusterConfig clusterConfig = new RegistryServerProperties.ClusterConfig();
        clusterConfig.setEnabled(true);
        clusterConfig.setNodes(Arrays.asList("192.168.1.100:8761", "192.168.1.101:8761", "192.168.1.102:8761"));
        clusterConfig.setSyncInterval(Duration.ofSeconds(10));
        properties.setCluster(clusterConfig);

        // 设置WebClient mock
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        // 创建共享缓存
        clusterNodeCache = new ClusterNodeCache();
        
        // 创建服务实例
        clusterManagementService = new ClusterManagementServiceImpl(properties, webClientBuilder, clusterNodeCache);
        clusterSyncService = new ClusterSyncServiceImpl(properties, webClientBuilder, clusterNodeCache);
        
        // 初始化服务
        clusterManagementService.initialize();
        clusterSyncService.initialize();
    }

    @Test
    void testClusterInitialization() {
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
    void testNodeAdditionAndRemoval() {
        // Given
        ClusterNode newNode = new ClusterNode("test-node-1", "192.168.1.200", 8761);

        // When - Add node
        StepVerifier.create(clusterManagementService.addNode(newNode))
                .verifyComplete();

        // Then - Verify node was added
        StepVerifier.create(clusterManagementService.getAllNodes())
                .expectNextCount(5) // 3 configured + 1 current + 1 new
                .verifyComplete();

        // When - Remove node
        StepVerifier.create(clusterManagementService.removeNode("test-node-1"))
                .verifyComplete();

        // Then - Verify node was removed
        StepVerifier.create(clusterManagementService.getAllNodes())
                .expectNextCount(4) // Back to 3 configured + 1 current
                .verifyComplete();
    }

    @Test
    void testHealthyNodesFiltering() {
        // Given - Add a healthy and an unhealthy node
        ClusterNode healthyNode = new ClusterNode("healthy-node", "192.168.1.200", 8761);
        healthyNode.setStatus(NodeStatus.UP);
        
        ClusterNode unhealthyNode = new ClusterNode("unhealthy-node", "192.168.1.201", 8761);
        unhealthyNode.setStatus(NodeStatus.DOWN);

        // When
        StepVerifier.create(clusterManagementService.addNode(healthyNode))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(unhealthyNode))
                .verifyComplete();

        // Then - Only healthy nodes should be returned
        StepVerifier.create(clusterManagementService.getHealthyNodes())
                .expectNextMatches(node -> node.getStatus() == NodeStatus.UP)
                .expectNextMatches(node -> node.getStatus() == NodeStatus.UP) // Current node
                .verifyComplete();
    }

    @Test
    void testLeaderElection() {
        // Given - Add multiple healthy nodes
        ClusterNode node1 = new ClusterNode("node-1", "192.168.1.200", 8761);
        node1.setStatus(NodeStatus.UP);
        
        ClusterNode node2 = new ClusterNode("node-2", "192.168.1.201", 8761);
        node2.setStatus(NodeStatus.UP);

        StepVerifier.create(clusterManagementService.addNode(node1))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(node2))
                .verifyComplete();

        // When - Elect leader
        StepVerifier.create(clusterManagementService.electLeader())
                .expectNextMatches(leader -> {
                    assertThat(leader).isNotNull();
                    assertThat(leader.getStatus()).isEqualTo(NodeStatus.UP);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void testFailoverDetection() {
        // Given - Add nodes where majority are down
        ClusterNode node1 = new ClusterNode("node-1", "192.168.1.200", 8761);
        node1.setStatus(NodeStatus.DOWN);
        
        ClusterNode node2 = new ClusterNode("node-2", "192.168.1.201", 8761);
        node2.setStatus(NodeStatus.DOWN);
        
        ClusterNode node3 = new ClusterNode("node-3", "192.168.1.202", 8761);
        node3.setStatus(NodeStatus.DOWN);

        StepVerifier.create(clusterManagementService.addNode(node1))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(node2))
                .verifyComplete();
        
        StepVerifier.create(clusterManagementService.addNode(node3))
                .verifyComplete();

        // When & Then - Should detect need for failover
        StepVerifier.create(clusterManagementService.needsFailover())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testFailoverExecution() {
        // Given
        ClusterNode failedNode = new ClusterNode("failed-node", "192.168.1.200", 8761);
        failedNode.setStatus(NodeStatus.UP);

        StepVerifier.create(clusterManagementService.addNode(failedNode))
                .verifyComplete();

        // When - Perform failover
        StepVerifier.create(clusterManagementService.performFailover("failed-node"))
                .verifyComplete();

        // Then - Node should be marked as down
        StepVerifier.create(clusterManagementService.getAllNodes())
                .expectNextMatches(node -> {
                    if ("failed-node".equals(node.getNodeId())) {
                        return node.getStatus() == NodeStatus.DOWN;
                    }
                    return true;
                })
                .expectNextCount(4) // Skip other nodes (3 configured + 1 current)
                .verifyComplete();
    }

    @Test
    void testClusterSyncIntegration() {
        // Given
        ServiceInstance instance = createTestServiceInstance();
        ServiceEvent event = new ServiceEvent(ServiceEventType.REGISTER, instance);

        // When - Sync event to cluster
        StepVerifier.create(clusterSyncService.syncToCluster(event))
                .verifyComplete();

        // Then - Event should be received from cluster
        StepVerifier.create(clusterSyncService.receiveFromCluster().take(1))
                .then(() -> clusterSyncService.handleClusterEvent(event))
                .expectNextMatches(receivedEvent -> 
                        receivedEvent.getServiceId().equals(event.getServiceId()) &&
                        receivedEvent.getInstanceId().equals(event.getInstanceId())
                )
                .verifyComplete();
    }

    @Test
    void testClusterStatusConsistency() {
        // Given - Check initial cluster status
        ClusterStatus initialStatus = clusterManagementService.getClusterStatus().block();
        int initialNodeCount = initialStatus.getTotalNodes();
        
        // Given - Multiple operations
        ClusterNode node1 = new ClusterNode("consistency-node-1", "192.168.1.200", 8761);
        ClusterNode node2 = new ClusterNode("consistency-node-2", "192.168.1.201", 8761);

        // When - Add nodes sequentially
        clusterManagementService.addNode(node1).block();
        clusterManagementService.addNode(node2).block();
        
        // Then - Check final status
        ClusterStatus finalStatus = clusterManagementService.getClusterStatus().block();
        
        // Should have initial nodes + 2 new nodes
        assertThat(finalStatus.getTotalNodes()).isEqualTo(initialNodeCount + 2);
        
        // Verify the new nodes are present
        boolean hasNode1 = finalStatus.getNodes().stream()
                .anyMatch(node -> "consistency-node-1".equals(node.getNodeId()));
        boolean hasNode2 = finalStatus.getNodes().stream()
                .anyMatch(node -> "consistency-node-2".equals(node.getNodeId()));
                
        assertThat(hasNode1).isTrue();
        assertThat(hasNode2).isTrue();
    }

    @Test
    void testClusterManagementLifecycle() {
        // When - Start cluster management
        StepVerifier.create(clusterManagementService.startClusterManagement())
                .verifyComplete();

        // Then - Should be able to get cluster status
        StepVerifier.create(clusterManagementService.getClusterStatus())
                .expectNextMatches(status -> status != null)
                .verifyComplete();

        // When - Stop cluster management
        StepVerifier.create(clusterManagementService.stopClusterManagement())
                .verifyComplete();
    }

    @Test
    void testInvalidNodeOperations() {
        // Test adding null node
        StepVerifier.create(clusterManagementService.addNode(null))
                .expectError(IllegalArgumentException.class)
                .verify();

        // Test removing null/empty node ID
        StepVerifier.create(clusterManagementService.removeNode(null))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(clusterManagementService.removeNode(""))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    /**
     * 创建测试用的服务实例
     */
    private ServiceInstance createTestServiceInstance() {
        ServiceInstance instance = new ServiceInstance();
        instance.setServiceId("test-service");
        instance.setInstanceId("test-instance-1");
        instance.setHost("192.168.1.100");
        instance.setPort(8080);
        instance.setStatus(InstanceStatus.UP);
        instance.setRegistrationTime(Instant.now());
        instance.setLastHeartbeat(Instant.now());
        return instance;
    }
}