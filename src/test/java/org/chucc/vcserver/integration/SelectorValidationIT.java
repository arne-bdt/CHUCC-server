package org.chucc.vcserver.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for selector validation.
 * Verifies that conflicting selectors return 400 Bad Request with problem+json.
 * Tests the HTTP contract (command side) only - projector DISABLED.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SelectorValidationIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testBranchAndCommitConflict_returns400() throws Exception {
    // When: query with both branch and commit selectors
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o }")
        .queryParam("branch", "main")
        .queryParam("commit", "abc123")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: should return 400 with problem+json
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/problem+json"));

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(400);
    assertThat(json.get("code").asText()).isEqualTo("selector_conflict");
    assertThat(json.has("type")).isTrue();
    assertThat(json.has("title")).isTrue();
  }

  @Test
  void testCommitAndAsOfConflict_returns400() throws Exception {
    // When: query with both commit and asOf selectors
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o }")
        .queryParam("commit", "abc123")
        .queryParam("asOf", "2024-01-01T00:00:00Z")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: should return 400 with problem+json
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/problem+json"));

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(400);
    assertThat(json.get("code").asText()).isEqualTo("selector_conflict");
  }

  @Test
  void testAllThreeSelectorsConflict_returns400() throws Exception {
    // When: query with all three selectors
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o }")
        .queryParam("branch", "main")
        .queryParam("commit", "abc123")
        .queryParam("asOf", "2024-01-01T00:00:00Z")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: should return 400 with problem+json
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/problem+json"));

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(400);
    assertThat(json.get("code").asText()).isEqualTo("selector_conflict");
  }

  @Test
  void testBranchAndAsOfCombination_isAllowed() throws Exception {
    // When: query with branch and asOf selectors (special case)
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o }")
        .queryParam("branch", "main")
        .queryParam("asOf", "2024-01-01T00:00:00Z")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: should not return 400 (this combination is allowed)
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testSingleBranchSelector_isAllowed() throws Exception {
    // When: query with only branch selector
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o }")
        .queryParam("branch", "main")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: should not return 400
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testSingleCommitSelector_isAllowed() throws Exception {
    // When: query with only commit selector (using existing initial commit)
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o }")
        .queryParam("commit", initialCommitId.value())
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: should not return 400
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testSingleAsOfSelector_isAllowed() throws Exception {
    // When: query with only asOf selector
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o }")
        .queryParam("asOf", "2024-01-01T00:00:00Z")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: should not return 400
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testNoSelectors_isAllowed() throws Exception {
    // When: query with no selectors
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o }")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: should not return 400
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }
}
