package org.chucc.vcserver.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConflictProblemDetail DTO.
 * Verifies conflict extension to RFC 7807 problem+json format.
 */
class ConflictProblemDetailTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testConstructorWithConflicts() {
        List<ConflictItem> conflicts = Arrays.asList(
                new ConflictItem("http://example.org/graph1", "http://example.org/s1", "http://example.org/p1", "http://example.org/o1"),
                new ConflictItem("http://example.org/graph2", "http://example.org/s2", "http://example.org/p2", "http://example.org/o2")
        );

        ConflictProblemDetail problem = new ConflictProblemDetail("Merge conflict", 409, "merge_conflict", conflicts);

        assertEquals("Merge conflict", problem.getTitle());
        assertEquals(409, problem.getStatus());
        assertEquals("merge_conflict", problem.getCode());
        assertEquals(2, problem.getConflicts().size());
    }

    @Test
    void testConstructorWithTypeAndConflicts() {
        List<ConflictItem> conflicts = Arrays.asList(
                new ConflictItem("http://example.org/graph1", "http://example.org/s1", "http://example.org/p1", "http://example.org/o1")
        );

        ConflictProblemDetail problem = new ConflictProblemDetail(
                "https://example.org/errors/merge-conflict",
                "Merge conflict",
                409,
                "merge_conflict",
                conflicts
        );

        assertEquals("https://example.org/errors/merge-conflict", problem.getType());
        assertEquals("Merge conflict", problem.getTitle());
        assertEquals(409, problem.getStatus());
        assertEquals("merge_conflict", problem.getCode());
        assertEquals(1, problem.getConflicts().size());
    }

    @Test
    void testAddConflict() {
        ConflictProblemDetail problem = new ConflictProblemDetail();

        assertNull(problem.getConflicts());

        problem.addConflict(new ConflictItem("http://example.org/graph1", "http://example.org/s1", "http://example.org/p1", "http://example.org/o1"));

        assertNotNull(problem.getConflicts());
        assertEquals(1, problem.getConflicts().size());

        problem.addConflict(new ConflictItem("http://example.org/graph2", "http://example.org/s2", "http://example.org/p2", "http://example.org/o2"));

        assertEquals(2, problem.getConflicts().size());
    }

    @Test
    void testSerializationToJson() throws Exception {
        List<ConflictItem> conflicts = Arrays.asList(
                new ConflictItem("http://example.org/graph1", "http://example.org/s1", "http://example.org/p1", "http://example.org/o1")
        );

        ConflictProblemDetail problem = new ConflictProblemDetail("Merge conflict", 409, "merge_conflict", conflicts);

        String json = objectMapper.writeValueAsString(problem);

        assertTrue(json.contains("\"title\":\"Merge conflict\""));
        assertTrue(json.contains("\"status\":409"));
        assertTrue(json.contains("\"code\":\"merge_conflict\""));
        assertTrue(json.contains("\"conflicts\""));
        assertTrue(json.contains("\"graph\":\"http://example.org/graph1\""));
    }

    @Test
    void testDeserializationFromJson() throws Exception {
        String json = "{\"type\":\"about:blank\"," +
                      "\"title\":\"Merge conflict\"," +
                      "\"status\":409," +
                      "\"code\":\"merge_conflict\"," +
                      "\"conflicts\":[{" +
                      "\"graph\":\"http://example.org/graph1\"," +
                      "\"subject\":\"http://example.org/s1\"," +
                      "\"predicate\":\"http://example.org/p1\"," +
                      "\"object\":\"http://example.org/o1\"" +
                      "}]}";

        ConflictProblemDetail problem = objectMapper.readValue(json, ConflictProblemDetail.class);

        assertEquals("about:blank", problem.getType());
        assertEquals("Merge conflict", problem.getTitle());
        assertEquals(409, problem.getStatus());
        assertEquals("merge_conflict", problem.getCode());
        assertEquals(1, problem.getConflicts().size());
        assertEquals("http://example.org/graph1", problem.getConflicts().get(0).getGraph());
    }

    @Test
    void testInheritsFromProblemDetail() {
        ConflictProblemDetail problem = new ConflictProblemDetail();

        assertTrue(problem instanceof ProblemDetail, "ConflictProblemDetail should extend ProblemDetail");
    }
}
