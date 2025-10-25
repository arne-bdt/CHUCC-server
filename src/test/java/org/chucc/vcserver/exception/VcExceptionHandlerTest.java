package org.chucc.vcserver.exception;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.chucc.vcserver.dto.ConflictItem;
import org.chucc.vcserver.dto.ConflictProblemDetail;
import org.chucc.vcserver.dto.ProblemDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VcExceptionHandler.
 * Verifies exception to problem+json conversion.
 */
class VcExceptionHandlerTest {

    private VcExceptionHandler handler;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new VcExceptionHandler(meterRegistry);
    }

    @Test
    void testHandleSelectorConflictException() {
        SelectorConflictException ex = new SelectorConflictException("Multiple selectors provided");

        ResponseEntity<ProblemDetail> response = handler.handleVcException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("application/problem+json"),
                     response.getHeaders().getContentType());

        ProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals("Multiple selectors provided", problem.getTitle());
        assertEquals(400, problem.getStatus());
        assertEquals("selector_conflict", problem.getCode());
    }

    @Test
    void testHandleMergeConflictException() {
        List<ConflictItem> conflicts = Arrays.asList(
                new ConflictItem("http://example.org/graph1",
                                "http://example.org/s1",
                                "http://example.org/p1",
                                "http://example.org/o1")
        );
        MergeConflictException ex = new MergeConflictException("Merge failed", conflicts);

        ResponseEntity<ConflictProblemDetail> response = handler.handleMergeConflict(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("application/problem+json"),
                     response.getHeaders().getContentType());

        ConflictProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals("Merge failed", problem.getTitle());
        assertEquals(409, problem.getStatus());
        assertEquals("merge_conflict", problem.getCode());
        assertNotNull(problem.getConflicts());
        assertEquals(1, problem.getConflicts().size());
        assertEquals("http://example.org/graph1", problem.getConflicts().get(0).getGraph());
    }

    @Test
    void testHandleRebaseConflictException() {
        List<ConflictItem> conflicts = Arrays.asList(
                new ConflictItem("http://example.org/graph1",
                                "http://example.org/s1",
                                "http://example.org/p1",
                                "http://example.org/o1")
        );
        RebaseConflictException ex = new RebaseConflictException("Rebase failed", conflicts);

        ResponseEntity<ConflictProblemDetail> response = handler.handleRebaseConflict(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("application/problem+json"),
                     response.getHeaders().getContentType());

        ConflictProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals("Rebase failed", problem.getTitle());
        assertEquals(409, problem.getStatus());
        assertEquals("rebase_conflict", problem.getCode());
        assertNotNull(problem.getConflicts());
        assertEquals(1, problem.getConflicts().size());
    }

    @Test
    void testHandleCherryPickConflictException() {
        List<ConflictItem> conflicts = Arrays.asList(
                new ConflictItem("http://example.org/graph1",
                                "http://example.org/s1",
                                "http://example.org/p1",
                                "http://example.org/o1")
        );
        CherryPickConflictException ex = new CherryPickConflictException("Cherry-pick failed", conflicts);

        ResponseEntity<ConflictProblemDetail> response = handler.handleCherryPickConflict(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("application/problem+json"),
                     response.getHeaders().getContentType());

        ConflictProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals("Cherry-pick failed", problem.getTitle());
        assertEquals(409, problem.getStatus());
        assertEquals("cherry_pick_conflict", problem.getCode());
        assertNotNull(problem.getConflicts());
        assertEquals(1, problem.getConflicts().size());
    }

    @Test
    void testHandleConcurrentWriteConflictException() {
        ConcurrentWriteConflictException ex = new ConcurrentWriteConflictException("Concurrent write detected");

        ResponseEntity<ProblemDetail> response = handler.handleVcException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("application/problem+json"),
                     response.getHeaders().getContentType());

        ProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals("Concurrent write detected", problem.getTitle());
        assertEquals(409, problem.getStatus());
        assertEquals("concurrent_write_conflict", problem.getCode());
    }

    @Test
    void testHandleGraphNotFoundException() {
        GraphNotFoundException ex = new GraphNotFoundException("Graph not found");

        ResponseEntity<ProblemDetail> response = handler.handleVcException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("application/problem+json"),
                     response.getHeaders().getContentType());

        ProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals("Graph not found", problem.getTitle());
        assertEquals(404, problem.getStatus());
        assertEquals("graph_not_found", problem.getCode());
    }

    @Test
    void testHandleTagRetargetForbiddenException() {
        TagRetargetForbiddenException ex = new TagRetargetForbiddenException("Tag cannot be retargeted");

        ResponseEntity<ProblemDetail> response = handler.handleVcException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("application/problem+json"),
                     response.getHeaders().getContentType());

        ProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals("Tag cannot be retargeted", problem.getTitle());
        assertEquals(409, problem.getStatus());
        assertEquals("tag_retarget_forbidden", problem.getCode());
    }

    @Test
    void testContentTypeIsAlwaysProblemJson() {
        // Test various exception types to ensure Content-Type is always application/problem+json
        VcException[] exceptions = {
                new SelectorConflictException("test"),
                new ConcurrentWriteConflictException("test"),
                new GraphNotFoundException("test"),
                new TagRetargetForbiddenException("test")
        };

        for (VcException ex : exceptions) {
            ResponseEntity<ProblemDetail> response = handler.handleVcException(ex);
            assertEquals(MediaType.parseMediaType("application/problem+json"),
                         response.getHeaders().getContentType(),
                         "Content-Type must be application/problem+json for " + ex.getClass().getSimpleName());
        }
    }

    @Test
    void testEmptyConflictsList() {
        // Test that empty conflicts list is handled properly
        MergeConflictException ex = new MergeConflictException("Merge failed", null);

        ResponseEntity<ConflictProblemDetail> response = handler.handleMergeConflict(ex);

        assertNotNull(response);
        ConflictProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertNotNull(problem.getConflicts());
        assertTrue(problem.getConflicts().isEmpty());
    }

    @Test
    void testMultipleConflicts() {
        List<ConflictItem> conflicts = Arrays.asList(
                new ConflictItem("http://example.org/graph1", "http://example.org/s1", "http://example.org/p1", "http://example.org/o1"),
                new ConflictItem("http://example.org/graph2", "http://example.org/s2", "http://example.org/p2", "http://example.org/o2"),
                new ConflictItem("http://example.org/graph3", "http://example.org/s3", "http://example.org/p3", "http://example.org/o3")
        );
        MergeConflictException ex = new MergeConflictException("Multiple conflicts", conflicts);

        ResponseEntity<ConflictProblemDetail> response = handler.handleMergeConflict(ex);

        assertNotNull(response);
        ConflictProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals(3, problem.getConflicts().size());
    }
}
