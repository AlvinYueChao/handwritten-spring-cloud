package io.github.alvin.hsc.registry.server.controller;

import io.github.alvin.hsc.registry.server.model.*;
import io.github.alvin.hsc.registry.server.service.ClusterSyncService;
import io.github.alvin.hsc.registry.server.service.ClusterManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClusterController
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class ClusterControllerTest {

    @Mock
    private ClusterSyncService clusterSyncService;
    
    @Mock
    private ClusterManagementService clusterManagementService;

    private ClusterController clusterController;

    @BeforeEach
    void setUp() {
        clusterController = new ClusterController(clusterSyncService, clusterManagementService);
    }

    @Test
    void testReceiveClusterEvent_WithValidEvent_ShouldReturnOk() {
        // Given
        ServiceEvent event = createTestServiceEvent();
        doNothing().when(clusterSyncService).handleClusterEvent(any(ServiceEvent.class));

        // When & Then
        StepVerifier.create(clusterController.receiveClusterEvent(event))
                .expectNextMatches(response -> response.getStatusCode().is2xxSuccessful())
                .verifyComplete();

        verify(clusterSyncService).handleClusterEvent(event);
    }

    @Test
    void testGetClusterStatus_ShouldReturnStatus() {
        // Given
        ClusterStatus expectedStatus = createTestClusterStatus();
        when(clusterManagementService.getClusterStatus()).thenReturn(Mono.just(expectedStatus));

        // When & Then
        StepVerifier.create(clusterController.getClusterStatus())
                .expectNextMatches(response -> 
                        response.getStatusCode().is2xxSuccessful() &&
                        response.getBody() != null &&
                        response.getBody().getClusterId().equals("test-cluster")
                )
                .verifyComplete();
    }

    @Test
    void testGetCurrentNode_ShouldReturnCurrentNode() {
        // Given
        ClusterNode currentNode = createTestClusterNode("current-node", "192.168.1.100", 8761);
        when(clusterManagementService.getCurrentNode()).thenReturn(currentNode);

        // When
        ResponseEntity<ClusterNode> response = clusterController.getCurrentNode();

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getNodeId()).isEqualTo("current-node");
        assertThat(response.getBody().getHost()).isEqualTo("192.168.1.100");
        assertThat(response.getBody().getPort()).isEqualTo(8761);
    }

    @Test
    void testGetClusterNodes_ShouldReturnNodes() {
        // Given
        List<ClusterNode> nodes = List.of(
                createTestClusterNode("node-1", "192.168.1.100", 8761),
                createTestClusterNode("node-2", "192.168.1.101", 8761)
        );
        when(clusterManagementService.getAllNodes()).thenReturn(Flux.fromIterable(nodes));

        // When
        Flux<ClusterNode> response = clusterController.getClusterNodes();

        // Then
        StepVerifier.create(response)
                .expectNext(nodes.get(0))
                .expectNext(nodes.get(1))
                .verifyComplete();
    }

    @Test
    void testJoinCluster_WithValidNode_ShouldReturnOk() {
        // Given
        ClusterNode newNode = createTestClusterNode("new-node", "192.168.1.102", 8761);
        when(clusterManagementService.addNode(any(ClusterNode.class))).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(clusterController.joinCluster(newNode))
                .expectNextMatches(response -> response.getStatusCode().is2xxSuccessful())
                .verifyComplete();

        verify(clusterManagementService).addNode(newNode);
    }

    @Test
    void testJoinCluster_WithServiceError_ShouldPropagateError() {
        // Given
        ClusterNode newNode = createTestClusterNode("new-node", "192.168.1.102", 8761);
        when(clusterManagementService.addNode(any(ClusterNode.class)))
                .thenReturn(Mono.error(new RuntimeException("Join failed")));

        // When & Then
        StepVerifier.create(clusterController.joinCluster(newNode))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void testStreamClusterEvents_ShouldReturnEventStream() {
        // Given
        ServiceEvent event1 = createTestServiceEvent();
        ServiceEvent event2 = createTestServiceEvent();
        event2.setInstanceId("test-instance-2");
        
        when(clusterSyncService.receiveFromCluster())
                .thenReturn(Flux.just(event1, event2));

        // When & Then
        StepVerifier.create(clusterController.streamClusterEvents())
                .expectNext(event1)
                .expectNext(event2)
                .verifyComplete();
    }

    @Test
    void testHealth_ShouldReturnHealthStatus() {
        // Given
        ClusterNode currentNode = createTestClusterNode("current-node", "192.168.1.100", 8761);
        when(clusterManagementService.getCurrentNode()).thenReturn(currentNode);

        // When
        ResponseEntity<Object> response = clusterController.health();

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> healthData = (Map<String, Object>) response.getBody();
        assertThat(healthData.get("status")).isEqualTo("UP");
        assertThat(healthData.get("timestamp")).isNotNull();
        assertThat(healthData.get("node")).isEqualTo(currentNode);
    }

    /**
     * 创建测试用的服务事件
     */
    private ServiceEvent createTestServiceEvent() {
        ServiceInstance instance = new ServiceInstance();
        instance.setServiceId("test-service");
        instance.setInstanceId("test-instance-1");
        instance.setHost("192.168.1.100");
        instance.setPort(8080);
        instance.setStatus(InstanceStatus.UP);
        instance.setRegistrationTime(Instant.now());
        instance.setLastHeartbeat(Instant.now());

        return new ServiceEvent(ServiceEventType.REGISTER, instance);
    }

    /**
     * 创建测试用的集群状态
     */
    private ClusterStatus createTestClusterStatus() {
        ClusterNode node1 = createTestClusterNode("node-1", "192.168.1.100", 8761);
        ClusterNode node2 = createTestClusterNode("node-2", "192.168.1.101", 8761);
        ClusterNode currentNode = createTestClusterNode("current-node", "192.168.1.100", 8761);
        
        List<ClusterNode> nodes = List.of(node1, node2, currentNode);
        return new ClusterStatus("test-cluster", nodes, currentNode);
    }

    /**
     * 创建测试用的集群节点
     */
    private ClusterNode createTestClusterNode(String nodeId, String host, int port) {
        ClusterNode node = new ClusterNode(nodeId, host, port);
        node.setStatus(NodeStatus.UP);
        node.updateLastSeen();
        return node;
    }
}