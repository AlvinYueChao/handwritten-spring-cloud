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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for ClusterSyncServiceImpl
 * 
 * @author Alvin
 */
@ExtendWith(MockitoExtension.class)
class ClusterSyncServiceImplTest {

    @Mock
    private WebClient webClient;
    
    @Mock
    private WebClient.Builder webClientBuilder;
    
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    
    @Mock
    private WebClient.ResponseSpec responseSpec;

    private RegistryServerProperties properties;
    private ClusterSyncServiceImpl clusterSyncService;

    @BeforeEach
    void setUp() throws Exception {
        // 设置属性
        properties = new RegistryServerProperties();
        properties.setPort(8761);
        
        RegistryServerProperties.ClusterConfig clusterConfig = new RegistryServerProperties.ClusterConfig();
        clusterConfig.setEnabled(true);
        clusterConfig.setNodes(Arrays.asList("192.168.1.100:8761", "192.168.1.101:8761"));
        clusterConfig.setSyncInterval(Duration.ofSeconds(30));
        properties.setCluster(clusterConfig);

        // 设置WebClient mock
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        // 创建共享缓存
        ClusterNodeCache clusterNodeCache = new ClusterNodeCache();
        
        // 创建服务实例
        clusterSyncService = new ClusterSyncServiceImpl(properties, webClientBuilder, clusterNodeCache);
        
        // 调用PostConstruct方法进行初始化
        clusterSyncService.initialize();
    }

    @Test
    void testSyncToCluster_WhenClusterDisabled_ShouldReturnEmpty() {
        // Given
        properties.getCluster().setEnabled(false);
        ClusterNodeCache clusterNodeCache = new ClusterNodeCache();
        clusterSyncService = new ClusterSyncServiceImpl(properties, webClientBuilder, clusterNodeCache);
        
        ServiceEvent event = createTestServiceEvent();

        // When & Then
        StepVerifier.create(clusterSyncService.syncToCluster(event))
                .verifyComplete();
    }

    @Test
    void testSyncToCluster_WhenNoClusterNodes_ShouldReturnEmpty() {
        // Given
        properties.getCluster().setNodes(List.of());
        ClusterNodeCache clusterNodeCache = new ClusterNodeCache();
        clusterSyncService = new ClusterSyncServiceImpl(properties, webClientBuilder, clusterNodeCache);
        
        ServiceEvent event = createTestServiceEvent();

        // When & Then
        StepVerifier.create(clusterSyncService.syncToCluster(event))
                .verifyComplete();
    }

    @Test
    void testSyncToCluster_WhenClusterEnabled_ShouldSyncToNodes() {
        // Given
        ServiceEvent event = createTestServiceEvent();
        
        // Mock WebClient chain with lenient mode
        lenient().when(webClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersUriSpec);
        lenient().when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(clusterSyncService.syncToCluster(event))
                .verifyComplete();
    }

    @Test
    void testReceiveFromCluster_ShouldReturnEventFlux() {
        // When
        StepVerifier.create(clusterSyncService.receiveFromCluster().take(1))
                .then(() -> {
                    // 模拟接收到事件
                    ServiceEvent event = createTestServiceEvent();
                    clusterSyncService.handleClusterEvent(event);
                })
                .expectNextMatches(event -> 
                        event.getServiceId().equals("test-service") &&
                        event.getInstanceId().equals("test-instance-1")
                )
                .verifyComplete();
    }

    @Test
    void testStartSync_ShouldSucceed() {
        // When & Then
        StepVerifier.create(clusterSyncService.startSync())
                .verifyComplete();
    }

    @Test
    void testStopSync_ShouldSucceed() {
        // When & Then
        StepVerifier.create(clusterSyncService.stopSync())
                .verifyComplete();
    }

    @Test
    void testHandleClusterEvent_ShouldEmitEvent() {
        // Given
        ServiceEvent event = createTestServiceEvent();

        // When
        clusterSyncService.handleClusterEvent(event);

        // Then - 验证事件被发送到事件流
        StepVerifier.create(clusterSyncService.receiveFromCluster().take(1))
                .expectNextMatches(receivedEvent -> 
                        receivedEvent.getServiceId().equals(event.getServiceId()) &&
                        receivedEvent.getInstanceId().equals(event.getInstanceId())
                )
                .verifyComplete();
    }

    @Test
    void testGetCurrentNode_ShouldReturnCurrentNode() {
        // When
        ClusterNode currentNode = clusterSyncService.getCurrentNode();

        // Then
        assertThat(currentNode).isNotNull();
        assertThat(currentNode.getNodeId()).isNotNull();
        assertThat(currentNode.getHost()).isNotNull();
        assertThat(currentNode.getPort()).isEqualTo(8761);
    }

    @Test
    void testGetClusterNodes_ShouldReturnNodes() {
        // When
        Map<String, ClusterNode> nodes = clusterSyncService.getClusterNodes();

        // Then
        assertThat(nodes).isNotNull();
        // 至少包含当前节点
        assertThat(nodes).isNotEmpty();
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
     * 创建测试用的集群节点
     */
    private ClusterNode createTestClusterNode(String nodeId, String host, int port) {
        ClusterNode node = new ClusterNode(nodeId, host, port);
        node.setStatus(NodeStatus.UP);
        node.updateLastSeen();
        return node;
    }
}