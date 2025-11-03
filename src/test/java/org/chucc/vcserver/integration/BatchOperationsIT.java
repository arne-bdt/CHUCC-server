package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for batch write operations endpoint (POST /version/batch).
 *
 * <p>These tests verify the HTTP contract for batch operations including:
 * <ul>
 *   <li>Successful batch of SPARQL updates creates single commit</li>
 *   <li>Successful batch of RDF patches creates single commit</li>
 *   <li>Mixed operations (updates + patches) work correctly</li>
 *   <li>Validation errors (empty operations, mixed branches)</li>
 *   <li>Invalid operation types return 400 Bad Request</li>
 *   <li>Missing required headers return 400 Bad Request</li>
 * </ul>
 *
 * <p>Note: These are API layer tests that verify the HTTP contract only.
 * Repository updates are handled by ReadModelProjector (disabled in these tests).
 * Batch operations materialize datasets via DatasetService (synchronous repository read),
 * which does not require the async Kafka listener projector.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public class BatchOperationsIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void batchUpdate_shouldCreateSingleCommit_whenMultipleSparqlUpdates() {
    // Given: Batch with multiple SPARQL updates
    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "update",
                "sparql", "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }"
            ),
            Map.of(
                "type", "update",
                "sparql", "INSERT DATA { <http://ex.org/s2> <http://ex.org/p> \"v2\" }"
            )
        ),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Batch insert test data");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().get("Location")).isNotNull();
    assertThat(response.getHeaders().get("SPARQL-VC-Status")).contains("pending");

    @SuppressWarnings("unchecked")
    Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("status")).isEqualTo("accepted");
    assertThat(body.get("commitId")).isNotNull();
    assertThat(body.get("branch")).isEqualTo("main");
    assertThat(body.get("operationCount")).isEqualTo(2);
    assertThat(body.get("message")).isEqualTo("Batch insert test data");

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  void batchUpdate_shouldCreateSingleCommit_whenMultipleRdfPatches() {
    // Given: Batch with multiple RDF patches
    String patch1 = "TX .\n"
        + "A <http://ex.org/s1> <http://ex.org/p> \"v1\" .\n"
        + "TC .";
    String patch2 = "TX .\n"
        + "A <http://ex.org/s2> <http://ex.org/p> \"v2\" .\n"
        + "TC .";

    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "applyPatch",
                "patch", patch1
            ),
            Map.of(
                "type", "applyPatch",
                "patch", patch2
            )
        ),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Batch patch test data");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().get("Location")).isNotNull();

    @SuppressWarnings("unchecked")
    Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("status")).isEqualTo("accepted");
    assertThat(body.get("commitId")).isNotNull();
    assertThat(body.get("operationCount")).isEqualTo(2);
  }

  @Test
  void batchUpdate_shouldCreateSingleCommit_whenMixedOperations() {
    // Given: Batch with both SPARQL update and RDF patch
    String patch = "TX .\n"
        + "A <http://ex.org/s2> <http://ex.org/p> \"v2\" .\n"
        + "TC .";

    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "update",
                "sparql", "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }"
            ),
            Map.of(
                "type", "applyPatch",
                "patch", patch
            )
        ),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Mixed batch operations");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("operationCount")).isEqualTo(2);
  }

  @Test
  void batchUpdate_shouldReturn400_whenOperationsListEmpty() {
    // Given: Empty operations list
    Map<String, Object> request = Map.of(
        "operations", List.of(),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Test message");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void batchUpdate_shouldReturn400_whenInvalidOperationType() {
    // Given: Invalid operation type
    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "invalidType",
                "sparql", "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }"
            )
        ),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Test message");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void batchUpdate_shouldReturn400_whenMissingRequiredContent() {
    // Given: Update operation without SPARQL query
    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "update"
                // Missing sparql field
            )
        ),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Test message");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void batchUpdate_shouldReturn400_whenMissingAuthorHeader() {
    // Given: Request without SPARQL-VC-Author header
    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "update",
                "sparql", "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }"
            )
        ),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Test message");
    // Missing SPARQL-VC-Author

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void batchUpdate_shouldReturn400_whenMissingMessageHeader() {
    // Given: Request without SPARQL-VC-Message header
    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "update",
                "sparql", "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }"
            )
        ),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "testUser");
    // Missing SPARQL-VC-Message

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void batchUpdate_shouldReturn400_whenMalformedSparql() {
    // Given: Batch with malformed SPARQL query
    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "update",
                "sparql", "MALFORMED SPARQL QUERY"
            )
        ),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Test message");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void batchUpdate_shouldUseBranchFromOperations_whenRequestLevelBranchNotProvided() {
    // Given: No request-level branch, operations specify branch
    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "update",
                "sparql", "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }",
                "branch", "main"
            )
        )
        // No request-level branch
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Test message");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("branch")).isEqualTo("main");
  }
}
