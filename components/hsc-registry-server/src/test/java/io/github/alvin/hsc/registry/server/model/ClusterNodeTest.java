package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClusterNode model
 * 
 * @author Alvin
 */
class ClusterNodeTest {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testValidClusterNode() {
        ClusterNode node = new ClusterNode("node-1", "localhost", 8761);
        
        Set<ConstraintViolation<ClusterNode>> violations = validator.validate(node);
        assertTrue(violations.isEmpty(), "Valid cluster node should have no validation errors");
    }

    @Test
    void testClusterNodeWithBlankNodeId() {
        ClusterNode node = new ClusterNode("", "localhost", 8761);
        
        Set<ConstraintViolation<ClusterNode>> violations = validator.validate(node);
        assertEquals(1, violations.size());
        assertEquals("Node ID cannot be blank", violations.iterator().next().getMessage());
    }

    @Test
    void testClusterNodeWithNullNodeId() {
        ClusterNode node = new ClusterNode();
        node.setHost("localhost");
        node.setPort(8761);
        node.setStatus(NodeStatus.UP);
        
        Set<ConstraintViolation<ClusterNode>> violations = validator.validate(node);
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("Node ID cannot be blank")));
    }

    @Test
    void testClusterNodeWithBlankHost() {
        ClusterNode node = new ClusterNode("node-1", "", 8761);
        
        Set<ConstraintViolation<ClusterNode>> violations = validator.validate(node);
        assertEquals(1, violations.size());
        assertEquals("Host cannot be blank", violations.iterator().next().getMessage());
    }

    @Test
    void testClusterNodeWithNullHost() {
        ClusterNode node = new ClusterNode();
        node.setNodeId("node-1");
        node.setPort(8761);
        node.setStatus(NodeStatus.UP);
        
        Set<ConstraintViolation<ClusterNode>> violations = validator.validate(node);
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("Host cannot be blank")));
    }

    @Test
    void testClusterNodeWithInvalidPort() {
        ClusterNode node = new ClusterNode("node-1", "localhost", -1);
        
        Set<ConstraintViolation<ClusterNode>> violations = validator.validate(node);
        assertEquals(1, violations.size());
        assertEquals("Port must be positive", violations.iterator().next().getMessage());
    }

    @Test
    void testClusterNodeWithZeroPort() {
        ClusterNode node = new ClusterNode("node-1", "localhost", 0);
        
        Set<ConstraintViolation<ClusterNode>> violations = validator.validate(node);
        assertEquals(1, violations.size());
        assertEquals("Port must be positive", violations.iterator().next().getMessage());
    }

    @Test
    void testClusterNodeWithNullStatus() {
        ClusterNode node = new ClusterNode("node-1", "localhost", 8761);
        node.setStatus(null);
        
        Set<ConstraintViolation<ClusterNode>> violations = validator.validate(node);
        assertEquals(1, violations.size());
        assertEquals("Status cannot be null", violations.iterator().next().getMessage());
    }

    @Test
    void testClusterNodeDefaultValues() {
        ClusterNode node = new ClusterNode("node-1", "localhost", 8761);
        
        assertEquals("node-1", node.getNodeId());
        assertEquals("localhost", node.getHost());
        assertEquals(8761, node.getPort());
        assertEquals(NodeStatus.UP, node.getStatus());
        assertNotNull(node.getLastSeen());
    }

    @Test
    void testClusterNodeGetAddress() {
        ClusterNode node = new ClusterNode("node-1", "localhost", 8761);
        assertEquals("localhost:8761", node.getAddress());
        
        node.setHost("192.168.1.100");
        node.setPort(9000);
        assertEquals("192.168.1.100:9000", node.getAddress());
    }

    @Test
    void testClusterNodeUpdateLastSeen() {
        ClusterNode node = new ClusterNode("node-1", "localhost", 8761);
        Instant originalLastSeen = node.getLastSeen();
        
        // Wait a bit to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        node.updateLastSeen();
        assertTrue(node.getLastSeen().isAfter(originalLastSeen));
    }

    @Test
    void testClusterNodeWithMetadata() {
        ClusterNode node = new ClusterNode("node-1", "localhost", 8761);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        metadata.put("region", "us-east-1");
        metadata.put("weight", 100);
        node.setMetadata(metadata);
        
        assertEquals("1.0.0", node.getMetadata().get("version"));
        assertEquals("us-east-1", node.getMetadata().get("region"));
        assertEquals(100, node.getMetadata().get("weight"));
    }

    @Test
    void testClusterNodeWithDifferentStatuses() {
        ClusterNode upNode = new ClusterNode("node-1", "localhost", 8761);
        upNode.setStatus(NodeStatus.UP);
        assertEquals(NodeStatus.UP, upNode.getStatus());
        
        ClusterNode downNode = new ClusterNode("node-2", "localhost", 8762);
        downNode.setStatus(NodeStatus.DOWN);
        assertEquals(NodeStatus.DOWN, downNode.getStatus());
        
        ClusterNode startingNode = new ClusterNode("node-3", "localhost", 8763);
        startingNode.setStatus(NodeStatus.STARTING);
        assertEquals(NodeStatus.STARTING, startingNode.getStatus());
        
        ClusterNode unknownNode = new ClusterNode("node-4", "localhost", 8764);
        unknownNode.setStatus(NodeStatus.UNKNOWN);
        assertEquals(NodeStatus.UNKNOWN, unknownNode.getStatus());
    }

    @Test
    void testClusterNodeJsonSerialization() throws Exception {
        ClusterNode node = new ClusterNode("node-1", "localhost", 8761);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        node.setMetadata(metadata);
        
        String json = objectMapper.writeValueAsString(node);
        assertNotNull(json);
        assertTrue(json.contains("node-1"));
        assertTrue(json.contains("localhost"));
        assertTrue(json.contains("8761"));
        assertTrue(json.contains("UP"));
        assertTrue(json.contains("1.0.0"));
    }

    @Test
    void testClusterNodeJsonDeserialization() throws Exception {
        String json = """
            {
                "nodeId": "node-1",
                "host": "localhost",
                "port": 8761,
                "status": "UP",
                "metadata": {
                    "version": "1.0.0",
                    "region": "us-east-1"
                }
            }
            """;
        
        ClusterNode node = objectMapper.readValue(json, ClusterNode.class);
        assertEquals("node-1", node.getNodeId());
        assertEquals("localhost", node.getHost());
        assertEquals(8761, node.getPort());
        assertEquals(NodeStatus.UP, node.getStatus());
        assertEquals("1.0.0", node.getMetadata().get("version"));
        assertEquals("us-east-1", node.getMetadata().get("region"));
    }

    @Test
    void testClusterNodeToString() {
        ClusterNode node = new ClusterNode("node-1", "localhost", 8761);
        String toString = node.toString();
        assertTrue(toString.contains("node-1"));
        assertTrue(toString.contains("localhost"));
        assertTrue(toString.contains("8761"));
        assertTrue(toString.contains("UP"));
    }

    @Test
    void testClusterNodeEmptyConstructor() {
        ClusterNode node = new ClusterNode();
        assertNull(node.getNodeId());
        assertNull(node.getHost());
        assertEquals(0, node.getPort());
        assertNull(node.getStatus());
        assertNull(node.getLastSeen());
        assertNull(node.getMetadata());
    }
}