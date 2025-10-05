package org.chucc.vcserver.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConflictItem DTO.
 * Verifies all required fields are present per SPARQL 1.2 Protocol §11.
 */
class ConflictItemTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testConstructorWithRequiredFields() {
        ConflictItem item = new ConflictItem("http://example.org/graph1",
                                              "http://example.org/subject1",
                                              "http://example.org/predicate1",
                                              "http://example.org/object1");

        assertEquals("http://example.org/graph1", item.getGraph());
        assertEquals("http://example.org/subject1", item.getSubject());
        assertEquals("http://example.org/predicate1", item.getPredicate());
        assertEquals("http://example.org/object1", item.getObject());
        assertNull(item.getDetails());
    }

    @Test
    void testConstructorWithAllFields() {
        ConflictItem item = new ConflictItem("http://example.org/graph1",
                                              "http://example.org/subject1",
                                              "http://example.org/predicate1",
                                              "http://example.org/object1",
                                              "Conflict details");

        assertEquals("http://example.org/graph1", item.getGraph());
        assertEquals("http://example.org/subject1", item.getSubject());
        assertEquals("http://example.org/predicate1", item.getPredicate());
        assertEquals("http://example.org/object1", item.getObject());
        assertEquals("Conflict details", item.getDetails());
    }

    @Test
    void testSerializationToJson() throws Exception {
        ConflictItem item = new ConflictItem("http://example.org/graph1",
                                              "http://example.org/subject1",
                                              "http://example.org/predicate1",
                                              "http://example.org/object1",
                                              "Details");

        String json = objectMapper.writeValueAsString(item);

        assertTrue(json.contains("\"graph\":\"http://example.org/graph1\""));
        assertTrue(json.contains("\"subject\":\"http://example.org/subject1\""));
        assertTrue(json.contains("\"predicate\":\"http://example.org/predicate1\""));
        assertTrue(json.contains("\"object\":\"http://example.org/object1\""));
        assertTrue(json.contains("\"details\":\"Details\""));
    }

    @Test
    void testDeserializationFromJson() throws Exception {
        String json = "{\"graph\":\"http://example.org/graph1\"," +
                      "\"subject\":\"http://example.org/subject1\"," +
                      "\"predicate\":\"http://example.org/predicate1\"," +
                      "\"object\":\"http://example.org/object1\"," +
                      "\"details\":\"Details\"}";

        ConflictItem item = objectMapper.readValue(json, ConflictItem.class);

        assertEquals("http://example.org/graph1", item.getGraph());
        assertEquals("http://example.org/subject1", item.getSubject());
        assertEquals("http://example.org/predicate1", item.getPredicate());
        assertEquals("http://example.org/object1", item.getObject());
        assertEquals("Details", item.getDetails());
    }

    @Test
    void testAllRequiredFieldsPresent() {
        // Per §11, graph, subject, predicate, object are REQUIRED
        ConflictItem item = new ConflictItem();
        item.setGraph("http://example.org/graph1");
        item.setSubject("http://example.org/subject1");
        item.setPredicate("http://example.org/predicate1");
        item.setObject("http://example.org/object1");

        assertNotNull(item.getGraph(), "graph field is required");
        assertNotNull(item.getSubject(), "subject field is required");
        assertNotNull(item.getPredicate(), "predicate field is required");
        assertNotNull(item.getObject(), "object field is required");
    }
}
