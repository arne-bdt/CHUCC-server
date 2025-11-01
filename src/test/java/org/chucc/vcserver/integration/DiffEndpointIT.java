package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for GET /version/diff endpoint.
 * Tests diff computation between two commits returning RDF Patch format.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class DiffEndpointIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Override
  protected boolean shouldCreateInitialSetup() {
    return false; // Custom setup per test
  }

  @Test
  void diffCommits_withAdditions_shouldReturnRdfPatch() {
    // Given: Create two commits - second adds a triple
    String dataset = "test-diff";
    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Initial commit",
        createSimplePatch("http://ex.org/s1", "http://ex.org/p1", "value1")
    );

    CommitId commit2 = createCommit(
        dataset,
        List.of(commit1),
        "Alice <alice@example.org>",
        "Add triple",
        createSimplePatch("http://ex.org/s2", "http://ex.org/p2", "value2")
    );

    // When: Request diff from commit1 to commit2
    String url = String.format(
        "/version/diff?dataset=%s&from=%s&to=%s",
        dataset, commit1.value(), commit2.value()
    );
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        String.class
    );

    // Then: Should return 200 OK with RDF Patch
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("text/rdf-patch");

    String patchText = response.getBody();
    assertThat(patchText).isNotNull();
    // Should contain addition from commit2
    assertThat(patchText).contains("A <http://ex.org/s2> <http://ex.org/p2> \"value2\"");
    // Should not contain deletion (commit1 triple still exists in commit2)
    assertThat(patchText).doesNotContain("D <http://ex.org/s1>");
  }

  @Test
  void diffCommits_withDeletions_shouldIncludeDeleteOperations() {
    // Given: Create two commits - second deletes a triple
    String dataset = "test-diff-delete";

    // Commit1: Add two triples
    String patch1 = String.format(
        "TX .%nA <http://ex.org/s1> <http://ex.org/p1> \"value1\" .%n"
            + "A <http://ex.org/s2> <http://ex.org/p2> \"value2\" .%nTC ."
    );
    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Add two triples",
        patch1
    );

    // Commit2: Delete one triple
    String patch2 = String.format(
        "TX .%nD <http://ex.org/s1> <http://ex.org/p1> \"value1\" .%nTC ."
    );
    CommitId commit2 = createCommit(
        dataset,
        List.of(commit1),
        "Alice <alice@example.org>",
        "Delete triple",
        patch2
    );

    // When: Request diff from commit1 to commit2
    String url = String.format(
        "/version/diff?dataset=%s&from=%s&to=%s",
        dataset, commit1.value(), commit2.value()
    );
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        String.class
    );

    // Then: Should show deletion
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String patchText = response.getBody();
    assertThat(patchText).contains("D <http://ex.org/s1> <http://ex.org/p1> \"value1\"");
  }

  @Test
  void diffCommits_sameCommit_shouldReturnEmptyPatch() {
    // Given: Single commit
    String dataset = "test-diff-same";
    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Initial commit",
        createSimplePatch("http://ex.org/s1", "http://ex.org/p1", "value1")
    );

    // When: Request diff from commit to itself
    String url = String.format(
        "/version/diff?dataset=%s&from=%s&to=%s",
        dataset, commit1.value(), commit1.value()
    );
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        String.class
    );

    // Then: Should return empty patch (no changes)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String patchText = response.getBody();
    assertThat(patchText).isNotNull();
    // Empty patch should not contain any A or D operations
    assertThat(patchText).doesNotContain("A ");
    assertThat(patchText).doesNotContain("D ");
  }

  @Test
  void diffCommits_missingDatasetParameter_shouldReturn400() {
    // When: Call without dataset parameter
    CommitId dummyId = CommitId.generate();
    String url = String.format(
        "/version/diff?from=%s&to=%s",
        dummyId.value(), dummyId.value()
    );
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        String.class
    );

    // Then: Should return 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void diffCommits_missingFromParameter_shouldReturn400() {
    // When: Call without from parameter
    CommitId dummyId = CommitId.generate();
    String url = String.format(
        "/version/diff?dataset=test&to=%s",
        dummyId.value()
    );
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        String.class
    );

    // Then: Should return 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void diffCommits_missingToParameter_shouldReturn400() {
    // When: Call without to parameter
    CommitId dummyId = CommitId.generate();
    String url = String.format(
        "/version/diff?dataset=test&from=%s",
        dummyId.value()
    );
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        String.class
    );

    // Then: Should return 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void diffCommits_nonExistentFromCommit_shouldReturn404() {
    // Given: One valid commit
    String dataset = "test-diff-404";
    CommitId validCommit = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Initial commit",
        createSimplePatch("http://ex.org/s1", "http://ex.org/p1", "value1")
    );

    // When: Request diff with non-existent 'from' commit
    CommitId nonExistent = CommitId.generate();
    String url = String.format(
        "/version/diff?dataset=%s&from=%s&to=%s",
        dataset, nonExistent.value(), validCommit.value()
    );
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        String.class
    );

    // Then: Should return 404 Not Found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void diffCommits_nonExistentToCommit_shouldReturn404() {
    // Given: One valid commit
    String dataset = "test-diff-404-to";
    CommitId validCommit = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Initial commit",
        createSimplePatch("http://ex.org/s1", "http://ex.org/p1", "value1")
    );

    // When: Request diff with non-existent 'to' commit
    CommitId nonExistent = CommitId.generate();
    String url = String.format(
        "/version/diff?dataset=%s&from=%s&to=%s",
        dataset, validCommit.value(), nonExistent.value()
    );
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        String.class
    );

    // Then: Should return 404 Not Found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void diffCommits_invalidFromCommitId_shouldReturn400() {
    // When: Request diff with invalid commit ID format
    String url = "/version/diff?dataset=test&from=invalid-id&to=another-invalid-id";
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        String.class
    );

    // Then: Should return 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
