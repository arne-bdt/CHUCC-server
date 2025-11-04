package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Integration tests for batch graph operations endpoint.
 * Tests API layer (synchronous HTTP response validation).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class BatchGraphsIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  /**
   * Uses event-driven setup (Session 4 migration).
   */
  @Override
  protected void createInitialCommitAndBranch(String dataset) {
    createInitialCommitAndBranchViaEvents(dataset);
  }

  @Test
  void batchGraphs_shouldReturn200WithCommits_whenModeSingleAndChangesExist() throws Exception {
    // Given
    String batchRequest = """
        {
          "mode": "single",
          "branch": "main",
          "author": "Alice",
          "message": "Batch update multiple graphs",
          "operations": [
            {
              "method": "PUT",
              "graph": "http://example.org/g1",
              "data": "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \\"value1\\" .",
              "contentType": "text/turtle"
            },
            {
              "method": "PUT",
              "graph": "http://example.org/g2",
              "data": "@prefix ex: <http://example.org/> . ex:s2 ex:p2 \\"value2\\" .",
              "contentType": "text/turtle"
            }
          ]
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request = new HttpEntity<>(batchRequest, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/batch-graphs",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then - API response verification (synchronous)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).isNotNull();

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.has("commits")).isTrue();
    assertThat(json.get("commits").size()).isEqualTo(1);

    JsonNode commit = json.get("commits").get(0);
    assertThat(commit.get("id").asText()).isNotEmpty();
    assertThat(commit.get("operations").size()).isEqualTo(2);
    assertThat(commit.get("operations").get(0).asText())
        .contains("PUT");

    // Note: Repository updates handled by event projectors (async)
  }

  @Test
  void batchGraphs_shouldReturn200WithMultipleCommits_whenModeMultiple() throws Exception {
    // Given
    String batchRequest = """
        {
          "mode": "multiple",
          "branch": "main",
          "author": "Bob",
          "message": "Batch update",
          "operations": [
            {
              "method": "PUT",
              "graph": "http://example.org/g3",
              "data": "@prefix ex: <http://example.org/> . ex:s3 ex:p3 \\"value3\\" .",
              "contentType": "text/turtle"
            },
            {
              "method": "PUT",
              "graph": "http://example.org/g4",
              "data": "@prefix ex: <http://example.org/> . ex:s4 ex:p4 \\"value4\\" .",
              "contentType": "text/turtle"
            }
          ]
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request = new HttpEntity<>(batchRequest, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/batch-graphs",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).isNotNull();

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.has("commits")).isTrue();
    assertThat(json.get("commits").size()).isEqualTo(2);

    // Verify each commit has operation info
    for (int i = 0; i < 2; i++) {
      JsonNode commit = json.get("commits").get(i);
      assertThat(commit.get("id").asText()).isNotEmpty();
      assertThat(commit.get("operations").size()).isEqualTo(1);
    }
  }

  // Note: No-op detection test omitted from integration tests due to async architecture
  // Branch updates happen asynchronously via event projectors, so consecutive batch
  // requests see stale branch state. No-op detection is thoroughly tested in unit tests.
  // See GraphStorePutIT for similar commented-out test.

  @Test
  void batchGraphs_shouldReturn400_whenInvalidRequest() {
    // Given - Missing required field
    String batchRequest = """
        {
          "mode": "single",
          "author": "Alice",
          "message": "Missing branch",
          "operations": []
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request = new HttpEntity<>(batchRequest, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/batch-graphs",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void batchGraphs_shouldReturn400_whenInvalidMode() {
    // Given
    String batchRequest = """
        {
          "mode": "invalid",
          "branch": "main",
          "author": "Alice",
          "message": "Invalid mode",
          "operations": [
            {
              "method": "PUT",
              "graph": "http://example.org/g6",
              "data": "@prefix ex: <http://example.org/> . ex:s6 ex:p6 \\"value6\\" .",
              "contentType": "text/turtle"
            }
          ]
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request = new HttpEntity<>(batchRequest, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/batch-graphs",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void batchGraphs_shouldSupportMixedOperations() throws Exception {
    // Given - Create initial graph
    HttpHeaders headers1 = new HttpHeaders();
    headers1.set("Content-Type", "text/turtle");
    headers1.set("SPARQL-VC-Author", "Alice");
    headers1.set("SPARQL-VC-Message", "Create graph");

    String turtleContent = "@prefix ex: <http://example.org/> . ex:s7 ex:p7 \"value7\" .";
    restTemplate.exchange(
        "/data?graph=http://example.org/g7&branch=main",
        HttpMethod.PUT,
        new HttpEntity<>(turtleContent, headers1),
        String.class
    );

    // When - Execute batch with PUT, POST, DELETE
    String batchRequest = """
        {
          "mode": "single",
          "branch": "main",
          "author": "Bob",
          "message": "Mixed operations",
          "operations": [
            {
              "method": "PUT",
              "graph": "http://example.org/g8",
              "data": "@prefix ex: <http://example.org/> . ex:s8 ex:p8 \\"value8\\" .",
              "contentType": "text/turtle"
            },
            {
              "method": "POST",
              "graph": "http://example.org/g7",
              "data": "@prefix ex: <http://example.org/> . ex:s7b ex:p7b \\"added\\" .",
              "contentType": "text/turtle"
            },
            {
              "method": "DELETE",
              "graph": "http://example.org/g7"
            }
          ]
        }
        """;

    HttpHeaders headers2 = new HttpHeaders();
    headers2.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request = new HttpEntity<>(batchRequest, headers2);

    ResponseEntity<String> response = restTemplate.exchange(
        "/version/batch-graphs",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).isNotNull();

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("commits").size()).isEqualTo(1);
    assertThat(json.get("commits").get(0).get("operations").size()).isEqualTo(3);
  }
}
