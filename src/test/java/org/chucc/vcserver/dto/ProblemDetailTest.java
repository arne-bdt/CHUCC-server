package org.chucc.vcserver.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProblemDetail DTO.
 * Verifies RFC 7807 compliance for problem+json format.
 */
class ProblemDetailTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDefaultConstructor() {
        ProblemDetail problem = new ProblemDetail();
        assertEquals("about:blank", problem.getType());
        assertNull(problem.getTitle());
        assertEquals(0, problem.getStatus());
        assertNull(problem.getCode());
    }

    @Test
    void testConstructorWithoutType() {
        ProblemDetail problem = new ProblemDetail("Bad Request", 400, "selector_conflict");

        assertEquals("about:blank", problem.getType());
        assertEquals("Bad Request", problem.getTitle());
        assertEquals(400, problem.getStatus());
        assertEquals("selector_conflict", problem.getCode());
    }

    @Test
    void testConstructorWithType() {
        ProblemDetail problem = new ProblemDetail("https://example.org/errors/selector-conflict",
                                                   "Bad Request",
                                                   400,
                                                   "selector_conflict");

        assertEquals("https://example.org/errors/selector-conflict", problem.getType());
        assertEquals("Bad Request", problem.getTitle());
        assertEquals(400, problem.getStatus());
        assertEquals("selector_conflict", problem.getCode());
    }

    @Test
    void testSerializationToJson() throws Exception {
        ProblemDetail problem = new ProblemDetail("Bad Request", 400, "selector_conflict");

        String json = objectMapper.writeValueAsString(problem);

        assertTrue(json.contains("\"type\":\"about:blank\""));
        assertTrue(json.contains("\"title\":\"Bad Request\""));
        assertTrue(json.contains("\"status\":400"));
        assertTrue(json.contains("\"code\":\"selector_conflict\""));
    }

    @Test
    void testDeserializationFromJson() throws Exception {
        String json = "{\"type\":\"about:blank\"," +
                      "\"title\":\"Bad Request\"," +
                      "\"status\":400," +
                      "\"code\":\"selector_conflict\"}";

        ProblemDetail problem = objectMapper.readValue(json, ProblemDetail.class);

        assertEquals("about:blank", problem.getType());
        assertEquals("Bad Request", problem.getTitle());
        assertEquals(400, problem.getStatus());
        assertEquals("selector_conflict", problem.getCode());
    }

    @Test
    void testSetters() {
        ProblemDetail problem = new ProblemDetail();
        problem.setType("https://example.org/errors/test");
        problem.setTitle("Test Error");
        problem.setStatus(500);
        problem.setCode("test_error");

        assertEquals("https://example.org/errors/test", problem.getType());
        assertEquals("Test Error", problem.getTitle());
        assertEquals(500, problem.getStatus());
        assertEquals("test_error", problem.getCode());
    }

    @Test
    void testJsonIncludeNonNull() throws Exception {
        // Test that null fields are not included in JSON
        ProblemDetail problem = new ProblemDetail();
        problem.setStatus(400);

        String json = objectMapper.writeValueAsString(problem);

        assertFalse(json.contains("\"title\""), "Null title should not be in JSON");
        assertFalse(json.contains("\"code\""), "Null code should not be in JSON");
        assertTrue(json.contains("\"type\":\"about:blank\""));
        assertTrue(json.contains("\"status\":400"));
    }
}
