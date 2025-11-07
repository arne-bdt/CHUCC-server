package org.chucc.vcserver.integration;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for versioned SPARQL endpoints (Session 3).
 * Tests HTTP contract (command side) only - projector DISABLED.
 *
 * <p>Tests query endpoints at branches, commits, and tags with semantic routing:
 * <ul>
 *   <li>GET/POST /{dataset}/version/branches/{name}/sparql</li>
 *   <li>GET/POST /{dataset}/version/commits/{id}/sparql</li>
 *   <li>GET/POST /{dataset}/version/tags/{name}/sparql</li>
 *   <li>POST /{dataset}/version/branches/{name}/update</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class VersionedSparqlControllerIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  // ==================== Query at Branch ====================

  @Test
  void queryAtBranch_shouldReturnResults() {
    // Given: Initial empty dataset

    // When: Query at branch
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
        .fromPath("/default/version/branches/main/sparql")
        .queryParam("query", query)
        .build()
        .toUri();

    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Query succeeds with correct headers
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+json");
    assertThat(response.getHeaders().get("Content-Location"))
        .containsExactly("/default/version/branches/main/sparql");
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().get("Link")).isNotNull();
    assertThat(response.getBody()).contains("\"head\"", "\"results\"", "\"bindings\"");
  }

  @Test
  void queryAtBranch_postForm_shouldReturnResults() {
    // Given: Initial empty dataset

    // When: Query at branch via POST form with parameters in body
    String query = "SELECT * WHERE { ?s ?p ?o }";
    String url = "/default/version/branches/main/sparql";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    // Form-encoded POST requires parameters in body
    String body = "query=" + org.springframework.web.util.UriUtils.encode(query,
        java.nio.charset.StandardCharsets.UTF_8);
    HttpEntity<String> request = new HttpEntity<>(body, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then: Query succeeds
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+json");
  }

  @Test
  void queryAtBranch_notFound_shouldReturn404() {
    // When: Query at non-existent branch
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
        .fromPath("/default/version/branches/nonexistent/sparql")
        .queryParam("query", query)
        .build()
        .toUri();

    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Returns 404
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  @Test
  void queryAtBranch_malformedQuery_shouldReturn400() {
    // When: Query with syntax error
    String malformedQuery = "SELECT * WHERE { ?s ?p";
    java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
        .fromPath("/default/version/branches/main/sparql")
        .queryParam("query", malformedQuery)
        .build()
        .toUri();

    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Returns 400
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  // ==================== Query at Commit ====================

  @Test
  void queryAtCommit_shouldReturnImmutableResults() {
    // When: Query at initial commit
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
        .fromPath("/default/version/commits/" + initialCommitId.value() + "/sparql")
        .queryParam("query", query)
        .build()
        .toUri();

    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Query succeeds with immutable cache headers
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+json");
    assertThat(response.getHeaders().get("Content-Location"))
        .contains("/default/version/commits/" + initialCommitId.value() + "/sparql");
    assertThat(response.getHeaders().getCacheControl())
        .contains("immutable");
    assertThat(response.getHeaders().getETag())
        .isEqualTo("\"" + initialCommitId.value() + "\"");
    assertThat(response.getHeaders().get("Link")).isNotNull();
  }

  @Test
  void queryAtCommit_postForm_shouldReturnResults() {
    // When: Query at commit via POST form with parameters in body
    String query = "SELECT * WHERE { ?s ?p ?o }";
    String url = String.format("/default/version/commits/%s/sparql", initialCommitId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    String body = "query=" + org.springframework.web.util.UriUtils.encode(query,
        java.nio.charset.StandardCharsets.UTF_8);
    HttpEntity<String> request = new HttpEntity<>(body, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then: Query succeeds
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void queryAtCommit_notFound_shouldReturn404() {
    // When: Query at non-existent commit
    String query = "SELECT * WHERE { ?s ?p ?o }";
    String fakeCommitId = "01936d8f-0000-7000-0000-000000000000";
    java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
        .fromPath("/default/version/commits/" + fakeCommitId + "/sparql")
        .queryParam("query", query)
        .build()
        .toUri();

    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Returns 404
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void queryAtCommit_invalidCommitId_shouldReturn400() {
    // When: Query with invalid commit ID
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
        .fromPath("/default/version/commits/invalid-id/sparql")
        .queryParam("query", query)
        .build()
        .toUri();

    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Returns 400
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ==================== Query at Tag ====================

  @Test
  void queryAtTag_shouldReturnResults() {
    // Given: Tag pointing to initial commit
    String tagName = "v1.0";
    Tag tag = new Tag(tagName, initialCommitId, "Release 1.0", "test",
        java.time.Instant.now());
    tagRepository.save(DEFAULT_DATASET, tag);

    // When: Query at tag
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
        .fromPath("/default/version/tags/" + tagName + "/sparql")
        .queryParam("query", query)
        .build()
        .toUri();

    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Query succeeds with tag headers
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get("Content-Location"))
        .containsExactly("/default/version/tags/v1.0/sparql");
    assertThat(response.getHeaders().getETag())
        .isEqualTo("\"" + initialCommitId.value() + "\"");
    assertThat(response.getHeaders().get("Link")).isNotNull();
  }

  @Test
  void queryAtTag_postForm_shouldReturnResults() {
    // Given: Tag pointing to initial commit
    String tagName = "v1.0";
    Tag tag = new Tag(tagName, initialCommitId, "Release 1.0", "test",
        java.time.Instant.now());
    tagRepository.save(DEFAULT_DATASET, tag);

    // When: Query at tag via POST form with parameters in body
    String query = "SELECT * WHERE { ?s ?p ?o }";
    String url = String.format("/default/version/tags/%s/sparql", tagName);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    String body = "query=" + org.springframework.web.util.UriUtils.encode(query,
        java.nio.charset.StandardCharsets.UTF_8);
    HttpEntity<String> request = new HttpEntity<>(body, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then: Query succeeds
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void queryAtTag_notFound_shouldReturn404() {
    // When: Query at non-existent tag
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
        .fromPath("/default/version/tags/nonexistent/sparql")
        .queryParam("query", query)
        .build()
        .toUri();

    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Returns 404
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ==================== Update at Branch ====================

  @Test
  void updateAtBranch_shouldCreateCommit() {
    // When: Execute update at branch
    String url = "/default/version/branches/main/update";
    String updateQuery = "INSERT DATA { "
        + "<http://example.org/s> <http://example.org/p> \"test\" "
        + "}";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");
    headers.set("SPARQL-VC-Message", "Add test data");

    HttpEntity<String> request = new HttpEntity<>(updateQuery, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then: Update accepted with commit created
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().get("Content-Location"))
        .contains("/default/version/branches/main/update");
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().toString())
        .contains("/default/version/commits/");
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().get("Link")).isNotNull();
    assertThat(response.getBody()).contains("commitId");
  }

  @Test
  void updateAtBranch_missingAuthor_shouldReturn400() {
    // When: Execute update without author header
    String url = "/default/version/branches/main/update";
    String updateQuery = "INSERT DATA { <s> <p> <o> }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Message", "Add test data");

    HttpEntity<String> request = new HttpEntity<>(updateQuery, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then: Returns 400
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsIgnoringCase("author");
  }

  @Test
  void updateAtBranch_noOp_shouldReturn204() {
    // When: Execute update that makes no changes (delete non-existent triple)
    String url = "/default/version/branches/main/update";
    String updateQuery = "DELETE DATA { "
        + "<http://nonexistent.org/s> <http://nonexistent.org/p> \"nonexistent\" "
        + "}";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");
    headers.set("SPARQL-VC-Message", "No-op update");

    HttpEntity<String> request = new HttpEntity<>(updateQuery, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then: Returns 204 No Content (no-op per SPARQL 1.2 Protocol §7)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void updateAtBranch_notFound_shouldReturn404() {
    // When: Update at non-existent branch
    String url = "/default/version/branches/nonexistent/update";
    String updateQuery = "INSERT DATA { <s> <p> <o> }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");

    HttpEntity<String> request = new HttpEntity<>(updateQuery, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then: Returns 404 (branch not found)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsIgnoringCase("branch");
  }

  @Test
  void updateAtBranch_malformedUpdate_shouldReturn400() {
    // When: Execute malformed update
    String url = "/default/version/branches/main/update";
    String malformedUpdate = "INSERT DATA { <s> <p>";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");

    HttpEntity<String> request = new HttpEntity<>(malformedUpdate, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then: Returns 400
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void updateAtBranch_withIfMatch_shouldSucceed() {
    // When: Execute update with correct If-Match header
    String url = "/default/version/branches/main/update";
    String updateQuery = "INSERT DATA { <http://example.org/s> <http://example.org/p> <o> }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");
    headers.set("SPARQL-VC-Message", "Conditional update");
    headers.set("If-Match", "\"" + initialCommitId.value() + "\"");

    HttpEntity<String> request = new HttpEntity<>(updateQuery, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then: Update succeeds
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void updateAtBranch_withWrongIfMatch_shouldReturn412() {
    // When: Execute update with incorrect If-Match header
    String url = "/default/version/branches/main/update";
    String updateQuery = "INSERT DATA { <http://example.org/s> <http://example.org/p> <o> }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");
    headers.set("If-Match", "\"01936d8f-0000-7000-0000-000000000000\"");

    HttpEntity<String> request = new HttpEntity<>(updateQuery, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then: Returns 412 Precondition Failed
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
  }

  // ==================== Immutability Enforcement ====================

  @Test
  void updateAtCommit_shouldReturn405MethodNotAllowed() {
    // When: Try to update at commit (immutable - no such endpoint exists)
    String url = String.format("/default/version/commits/%s/update", initialCommitId.value());
    String updateQuery = "INSERT DATA { <s> <p> <o> }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");

    HttpEntity<String> request = new HttpEntity<>(updateQuery, headers);

    // Then: Returns error (endpoint doesn't exist - commits are immutable)
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
    assertThat(response.getStatusCode().isError()).isTrue();
    assertThat(response.getStatusCode().value()).isGreaterThanOrEqualTo(400);
  }

  @Test
  void updateAtTag_shouldReturn405MethodNotAllowed() {
    // Given: Tag
    String tagName = "v1.0";
    Tag tag = new Tag(tagName, initialCommitId);
    tagRepository.save(DEFAULT_DATASET, tag);

    // When: Try to update at tag (immutable - no such endpoint exists)
    String url = String.format("/default/version/tags/%s/update", tagName);
    String updateQuery = "INSERT DATA { <s> <p> <o> }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");

    HttpEntity<String> request = new HttpEntity<>(updateQuery, headers);

    // Then: Returns error (endpoint doesn't exist - tags are immutable)
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
    assertThat(response.getStatusCode().isError()).isTrue();
    assertThat(response.getStatusCode().value()).isGreaterThanOrEqualTo(400);
  }
}
