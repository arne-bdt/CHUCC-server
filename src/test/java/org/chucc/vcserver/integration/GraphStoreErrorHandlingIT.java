package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chucc.vcserver.testutil.ExpectedErrorContext;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for Graph Store Protocol error handling.
 * Verifies that all error responses follow RFC 7807 problem+json format
 * with consistent error codes and helpful messages.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class GraphStoreErrorHandlingIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void selectorConflict_bothGraphAndDefault_shouldReturn400WithProblemJson() throws Exception {
    // Arrange: Request with both graph and default parameters (mutually exclusive)
    String url = "/data?graph=http://example.org/graph1&default=true";

    // Act: Make GET request with conflicting selectors
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Assert: Verify 400 Bad Request with problem+json
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    // Parse and verify problem details
    JsonNode problem = objectMapper.readTree(response.getBody());
    assertThat(problem.get("type").asText()).isEqualTo("about:blank");
    assertThat(problem.get("status").asInt()).isEqualTo(400);
    assertThat(problem.get("code").asText()).isEqualTo("selector_conflict");
    assertThat(problem.get("detail").asText()).contains("mutually exclusive");
    assertThat(problem.has("instance")).isTrue();
    assertThat(problem.get("instance").asText()).matches("/errors/[0-9a-f-]+");

    // Verify helpful hint is included
    assertThat(problem.has("hint")).isTrue();
    assertThat(problem.get("hint").asText()).contains("exactly one");

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  void selectorConflict_neitherGraphNorDefault_shouldReturn400() throws Exception {
    // Arrange: Request with neither graph nor default parameter
    String url = "/data";

    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode problem = objectMapper.readTree(response.getBody());
    assertThat(problem.get("code").asText()).isEqualTo("selector_conflict");
    assertThat(problem.has("hint")).isTrue();

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  void graphNotFound_nonExistentGraph_shouldReturn404WithProblemJson() throws Exception {
    // Arrange: Request for a graph that doesn't exist
    String graphIri = "http://example.org/nonexistent";
    String url = "/data?graph=" + graphIri;

    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Assert: Verify 404 Not Found with problem+json
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    JsonNode problem = objectMapper.readTree(response.getBody());
    assertThat(problem.get("type").asText()).isEqualTo("about:blank");
    assertThat(problem.get("status").asInt()).isEqualTo(404);
    assertThat(problem.get("code").asText()).isEqualTo("graph_not_found");
    // Check either title or detail field (for backwards compatibility during transition)
    String message = problem.has("detail") && problem.get("detail") != null
        ? problem.get("detail").asText()
        : problem.get("title").asText();
    assertThat(message).contains("example.org/nonexistent");
    if (problem.has("graphIri") && problem.get("graphIri") != null) {
      assertThat(problem.get("graphIri").asText()).contains("example.org/nonexistent");
    }

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  void invalidArgument_invalidTimestamp_shouldReturn400WithProblemJson() throws Exception {
    // Arrange: Request with malformed timestamp
    String url = "/data?default=true&asOf=not-a-timestamp";

    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Assert: Verify 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode problem = objectMapper.readTree(response.getBody());
    assertThat(problem.get("status").asInt()).isEqualTo(400);
    assertThat(problem.get("code").asText()).isEqualTo("invalid_argument");

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  @SuppressWarnings("try")  // Suppress "resource never referenced" - used for MDC side-effects
  void invalidArgument_invalidGraphIri_shouldReturnError() throws Exception {
    try (var ignored = ExpectedErrorContext.suppress("Bad character in IRI")) {
      // Arrange: Request with invalid graph IRI (contains spaces)
      String url = "/data?graph=invalid%20iri";

      // Act
      ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

      // Assert: Either 400 (validation error) or 404 (not found) are acceptable
      assertThat(response.getStatusCode().is4xxClientError()).isTrue();

      JsonNode problem = objectMapper.readTree(response.getBody());
      // Should have a valid error code
      assertThat(problem.has("code")).isTrue();
      assertThat(problem.get("code").asText()).isIn("selector_conflict", "graph_not_found", "invalid_argument");

      // Note: Repository updates handled by ReadModelProjector (disabled in this test)
    }
  }

  @Test
  void writeOnReadOnlySelector_commitParameter_shouldReturn400() throws Exception {
    // Arrange: Attempt to write with commit selector (read-only)
    String url = "/data?default=true&commit=abc123";
    String rdfData = "<http://ex.org/s> <http://ex.org/p> <http://ex.org/o> .";

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.CONTENT_TYPE, "text/turtle");
    headers.set("SPARQL-VC-Author", "TestAuthor");
    headers.set("SPARQL-VC-Message", "Test commit");

    HttpEntity<String> request = new HttpEntity<>(rdfData, headers);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.PUT,
        request,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode problem = objectMapper.readTree(response.getBody());
    assertThat(problem.get("code").asText()).isEqualTo("write_on_readonly_selector");
    // Check either title or detail field (for backwards compatibility during transition)
    String message = problem.has("detail") && problem.get("detail") != null
        ? problem.get("detail").asText()
        : problem.get("title").asText();
    assertThat(message).contains("Cannot write to a specific commit");

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  void writeOnReadOnlySelector_asOfParameter_shouldReturn400() throws Exception {
    // Arrange: Attempt to write with asOf selector (read-only)
    String url = "/data?default=true&asOf=2025-10-01T12:00:00Z";
    String rdfData = "<http://ex.org/s> <http://ex.org/p> <http://ex.org/o> .";

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.CONTENT_TYPE, "text/turtle");
    headers.set("SPARQL-VC-Author", "TestAuthor");
    headers.set("SPARQL-VC-Message", "Test commit");

    HttpEntity<String> request = new HttpEntity<>(rdfData, headers);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.PUT,
        request,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode problem = objectMapper.readTree(response.getBody());
    assertThat(problem.get("code").asText()).isEqualTo("write_on_readonly_selector");
    // Check either title or detail field (for backwards compatibility during transition)
    String message = problem.has("detail") && problem.get("detail") != null
        ? problem.get("detail").asText()
        : problem.get("title").asText();
    assertThat(message).contains("Cannot write with asOf selector");

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  void unsupportedMediaType_patchWithWrongContentType_shouldReturn415() throws Exception {
    // Arrange: PATCH request with wrong Content-Type
    String url = "/data?default=true";
    String patchData = "TX .\\nA <http://ex.org/s> <http://ex.org/p> <http://ex.org/o> .\\nTC .";

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.CONTENT_TYPE, "text/turtle"); // Wrong! Should be text/rdf-patch
    headers.set("SPARQL-VC-Author", "TestAuthor");
    headers.set("SPARQL-VC-Message", "Test patch");

    HttpEntity<String> request = new HttpEntity<>(patchData, headers);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    // Verify response body is valid JSON (handled by Zalando problem-spring-web)
    JsonNode problem = objectMapper.readTree(response.getBody());
    assertThat(problem).isNotNull();
    assertThat(problem.get("status").asInt()).isEqualTo(415);

    // The response should mention the unsupported content type
    String responseBody = response.getBody();
    assertThat(responseBody).contains("text/turtle");
    assertThat(responseBody).contains("not supported");

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
    // Note: HttpMediaTypeNotSupportedException handled by Zalando problem-spring-web
  }

  @Test
  void problemDetailStructure_shouldIncludeAllRequiredFields() throws Exception {
    // Arrange: Trigger any error (selector conflict)
    String url = "/data?graph=http://example.org/g&default=true";

    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Assert: Verify all RFC 7807 required and recommended fields
    JsonNode problem = objectMapper.readTree(response.getBody());

    // Required fields
    assertThat(problem.has("type")).isTrue();
    assertThat(problem.has("title")).isTrue();
    assertThat(problem.has("status")).isTrue();

    // Extension fields (our additions)
    assertThat(problem.has("code")).isTrue();
    assertThat(problem.has("detail")).isTrue();
    assertThat(problem.has("instance")).isTrue();

    // Verify types
    assertThat(problem.get("type").isTextual()).isTrue();
    assertThat(problem.get("title").isTextual()).isTrue();
    assertThat(problem.get("status").isInt()).isTrue();
    assertThat(problem.get("code").isTextual()).isTrue();
    assertThat(problem.get("detail").isTextual()).isTrue();
    assertThat(problem.get("instance").isTextual()).isTrue();

    // Verify content-type header
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  void errorMessages_shouldNotLeakSensitiveInformation() throws Exception {
    // Arrange: Various error scenarios
    String[] urls = {
        "/data", // Selector conflict
        "/data?graph=http://example.org/nonexistent", // Graph not found
        "/data?default=true&asOf=invalid" // Invalid argument
    };

    for (String url : urls) {
      // Act
      ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

      // Assert: Verify no stack traces or internal paths in error messages
      JsonNode problem = objectMapper.readTree(response.getBody());
      String detail = problem.get("detail") != null ? problem.get("detail").asText() : "";
      String title = problem.get("title") != null ? problem.get("title").asText() : "";

      assertThat(detail).doesNotContain("Exception");
      assertThat(detail).doesNotContain("at org.chucc");
      assertThat(detail).doesNotContain("java.lang");
      assertThat(title).doesNotContain("Exception");

      // Verify no internal implementation details
      assertThat(response.getBody()).doesNotContain("InvocationTargetException");
      assertThat(response.getBody()).doesNotContain("NullPointerException");
    }

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  void errorCodes_shouldBeConsistent() throws Exception {
    // Arrange & Act: Trigger same error multiple times
    String url = "/data?graph=http://example.org/g&default=true";

    ResponseEntity<String> response1 = restTemplate.getForEntity(url, String.class);
    ResponseEntity<String> response2 = restTemplate.getForEntity(url, String.class);

    // Assert: Error codes should be identical
    JsonNode problem1 = objectMapper.readTree(response1.getBody());
    JsonNode problem2 = objectMapper.readTree(response2.getBody());

    assertThat(problem1.get("code").asText())
        .isEqualTo(problem2.get("code").asText())
        .isEqualTo("selector_conflict");

    assertThat(problem1.get("status").asInt())
        .isEqualTo(problem2.get("status").asInt())
        .isEqualTo(400);

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }
}
