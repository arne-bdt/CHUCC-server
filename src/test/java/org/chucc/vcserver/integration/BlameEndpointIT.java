package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.BlameResponse;
import org.chucc.vcserver.dto.QuadBlameInfo;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for GET /version/blame endpoint.
 * Tests graph-scoped blame with pagination and quad attribution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class BlameEndpointIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Override
  protected boolean shouldCreateInitialSetup() {
    return false; // Custom setup per test
  }

  @Test
  void blameGraph_basicScenario_shouldReturnQuadAttribution() {
    // Given: Create 2 commits with different authors in same graph
    String dataset = "test-blame";
    String graph = "http://example.org/data";

    // Commit 1: Alice adds 2 quads
    // RDF Patch format: A S P O G . (graph comes LAST!)
    String patch1 = String.format(
        "TX .%n"
            + "A <http://ex.org/s1> <http://ex.org/p1> \"value1\" <%s> .%n"
            + "A <http://ex.org/s2> <http://ex.org/p2> \"value2\" <%s> .%n"
            + "TC .",
        graph, graph
    );
    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Alice's commit",
        patch1
    );

    // Commit 2: Bob adds 1 quad
    String patch2 = String.format(
        "TX .%n"
            + "A <http://ex.org/s3> <http://ex.org/p3> \"value3\" <%s> .%n"
            + "TC .",
        graph
    );
    CommitId commit2 = createCommit(
        dataset,
        List.of(commit1),
        "Bob <bob@example.org>",
        "Bob's commit",
        patch2
    );

    // When: Blame graph at commit2
    String url = String.format(
        "/version/blame?dataset=%s&commit=%s&graph=%s",
        dataset, commit2.value(), graph
    );
    ResponseEntity<BlameResponse> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        BlameResponse.class
    );

    // Then: Should return all 3 quads with correct attribution
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BlameResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.commit()).isEqualTo(commit2.value());
    assertThat(body.graph()).isEqualTo(graph);
    assertThat(body.quads()).hasSize(3);

    // First 2 quads blamed to Alice (commit1)
    long aliceQuads = body.quads().stream()
        .filter(q -> q.commitId().equals(commit1.value()))
        .filter(q -> "Alice <alice@example.org>".equals(q.lastModifiedBy()))
        .count();
    assertThat(aliceQuads).isEqualTo(2);

    // Last quad blamed to Bob (commit2)
    long bobQuads = body.quads().stream()
        .filter(q -> q.commitId().equals(commit2.value()))
        .filter(q -> "Bob <bob@example.org>".equals(q.lastModifiedBy()))
        .count();
    assertThat(bobQuads).isEqualTo(1);
  }

  @Test
  void blameGraph_deleteAndReAdd_shouldBlameReAddCommit() {
    // Given: Test delete/re-add scenario (critical for quad removal logic)
    String dataset = "test-reblame";
    String graph = "http://example.org/data";

    // Commit 1: Alice adds quad
    String patch1 = String.format(
        "TX .%nA <http://ex.org/s1> <http://ex.org/p1> \"value1\" <%s> .%nTC .",
        graph
    );
    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Add quad",
        patch1
    );

    // Commit 2: Bob deletes quad
    String patch2 = String.format(
        "TX .%nD <http://ex.org/s1> <http://ex.org/p1> \"value1\" <%s> .%nTC .",
        graph
    );
    CommitId commit2 = createCommit(
        dataset,
        List.of(commit1),
        "Bob <bob@example.org>",
        "Delete quad",
        patch2
    );

    // Commit 3: Charlie re-adds same quad
    String patch3 = String.format(
        "TX .%nA <http://ex.org/s1> <http://ex.org/p1> \"value1\" <%s> .%nTC .",
        graph
    );
    CommitId commit3 = createCommit(
        dataset,
        List.of(commit2),
        "Charlie <charlie@example.org>",
        "Re-add quad",
        patch3
    );

    // When: Blame at commit3
    String url = String.format(
        "/version/blame?dataset=%s&commit=%s&graph=%s",
        dataset, commit3.value(), graph
    );
    ResponseEntity<BlameResponse> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        BlameResponse.class
    );

    // Then: Quad should be blamed to Charlie (commit3), not Alice (commit1)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BlameResponse body = response.getBody();
    assertThat(body.quads()).hasSize(1);

    QuadBlameInfo blameInfo = body.quads().get(0);
    assertThat(blameInfo.commitId()).isEqualTo(commit3.value());
    assertThat(blameInfo.lastModifiedBy()).isEqualTo("Charlie <charlie@example.org>");
    assertThat(blameInfo.subject()).isEqualTo("http://ex.org/s1");
  }

  @Test
  void blameGraph_graphIsolation_shouldOnlyShowTargetGraph() {
    // Given: Same triple in different graphs
    String dataset = "test-isolation";
    String graph1 = "http://example.org/graph1";
    String graph2 = "http://example.org/graph2";

    // Commit 1: Alice adds triple to graph1
    String patch1 = String.format(
        "TX .%nA <http://ex.org/s1> <http://ex.org/p1> \"value1\" <%s> .%nTC .",
        graph1
    );
    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Add to graph1",
        patch1
    );

    // Commit 2: Bob adds same triple to graph2
    String patch2 = String.format(
        "TX .%nA <http://ex.org/s1> <http://ex.org/p1> \"value1\" <%s> .%nTC .",
        graph2
    );
    CommitId commit2 = createCommit(
        dataset,
        List.of(commit1),
        "Bob <bob@example.org>",
        "Add to graph2",
        patch2
    );

    // When: Blame graph1
    String url1 = String.format(
        "/version/blame?dataset=%s&commit=%s&graph=%s",
        dataset, commit2.value(), graph1
    );
    ResponseEntity<BlameResponse> response1 = restTemplate.exchange(
        url1,
        HttpMethod.GET,
        null,
        BlameResponse.class
    );

    // Then: Should only see Alice's quad from graph1
    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
    BlameResponse body1 = response1.getBody();
    assertThat(body1.quads()).hasSize(1);
    assertThat(body1.quads().get(0).lastModifiedBy()).isEqualTo("Alice <alice@example.org>");
    assertThat(body1.quads().get(0).commitId()).isEqualTo(commit1.value());

    // When: Blame graph2
    String url2 = String.format(
        "/version/blame?dataset=%s&commit=%s&graph=%s",
        dataset, commit2.value(), graph2
    );
    ResponseEntity<BlameResponse> response2 = restTemplate.exchange(
        url2,
        HttpMethod.GET,
        null,
        BlameResponse.class
    );

    // Then: Should only see Bob's quad from graph2
    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
    BlameResponse body2 = response2.getBody();
    assertThat(body2.quads()).hasSize(1);
    assertThat(body2.quads().get(0).lastModifiedBy()).isEqualTo("Bob <bob@example.org>");
    assertThat(body2.quads().get(0).commitId()).isEqualTo(commit2.value());
  }

  @Test
  void blameGraph_withPagination_shouldReturnCorrectSubset() {
    // Given: Graph with 150 quads
    String dataset = "test-pagination";
    String graph = "http://example.org/data";

    // Build patch with 150 quads
    StringBuilder patchBuilder = new StringBuilder("TX .\n");
    for (int i = 1; i <= 150; i++) {
      patchBuilder.append(String.format(
          "A <http://ex.org/s%d> <http://ex.org/p1> \"value%d\" <%s> .\n",
          i, i, graph
      ));
    }
    patchBuilder.append("TC .");

    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Add 150 quads",
        patchBuilder.toString()
    );

    // When: Request first page (offset=0, limit=100)
    String url1 = String.format(
        "/version/blame?dataset=%s&commit=%s&graph=%s&offset=0&limit=100",
        dataset, commit1.value(), graph
    );
    ResponseEntity<BlameResponse> response1 = restTemplate.exchange(
        url1,
        HttpMethod.GET,
        null,
        BlameResponse.class
    );

    // Then: Should return 100 quads with hasMore=true
    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
    BlameResponse body1 = response1.getBody();
    assertThat(body1.quads()).hasSize(100);
    assertThat(body1.pagination().offset()).isEqualTo(0);
    assertThat(body1.pagination().limit()).isEqualTo(100);
    assertThat(body1.pagination().hasMore()).isTrue();

    // When: Request second page (offset=100, limit=100)
    String url2 = String.format(
        "/version/blame?dataset=%s&commit=%s&graph=%s&offset=100&limit=100",
        dataset, commit1.value(), graph
    );
    ResponseEntity<BlameResponse> response2 = restTemplate.exchange(
        url2,
        HttpMethod.GET,
        null,
        BlameResponse.class
    );

    // Then: Should return 50 quads with hasMore=false
    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
    BlameResponse body2 = response2.getBody();
    assertThat(body2.quads()).hasSize(50);
    assertThat(body2.pagination().offset()).isEqualTo(100);
    assertThat(body2.pagination().limit()).isEqualTo(100);
    assertThat(body2.pagination().hasMore()).isFalse();
  }

  @Test
  void blameGraph_withMoreResults_shouldIncludeLinkHeader() {
    // Given: Graph with 150 quads
    String dataset = "test-link-header";
    String graph = "http://example.org/data";

    StringBuilder patchBuilder = new StringBuilder("TX .\n");
    for (int i = 1; i <= 150; i++) {
      patchBuilder.append(String.format(
          "A <http://ex.org/s%d> <http://ex.org/p1> \"value%d\" <%s> .\n",
          i, i, graph
      ));
    }
    patchBuilder.append("TC .");

    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Add 150 quads",
        patchBuilder.toString()
    );

    // When: Request first page
    String url = String.format(
        "/version/blame?dataset=%s&commit=%s&graph=%s&offset=0&limit=100",
        dataset, commit1.value(), graph
    );
    ResponseEntity<BlameResponse> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        BlameResponse.class
    );

    // Then: Should include Link header for next page
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().containsKey("Link")).isTrue();

    String linkHeader = response.getHeaders().getFirst("Link");
    assertThat(linkHeader).isNotNull();
    assertThat(linkHeader).contains("offset=100");
    assertThat(linkHeader).contains("limit=100");
    assertThat(linkHeader).contains("rel=\"next\"");
    // Verify URL encoding of graph parameter
    assertThat(linkHeader).contains("graph=");
  }

  @Test
  void blameGraph_defaultGraph_shouldHandleDefaultKeyword() {
    // Given: Quads in default graph
    String dataset = "test-default-graph";

    // Add quads to default graph (no graph URI in patch = default graph)
    String patch1 = "TX .\n"
        + "A <http://ex.org/s1> <http://ex.org/p1> \"value1\" .\n"
        + "A <http://ex.org/s2> <http://ex.org/p2> \"value2\" .\n"
        + "TC .";

    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Add to default graph",
        patch1
    );

    // When: Blame default graph using "default" keyword
    String url = String.format(
        "/version/blame?dataset=%s&commit=%s&graph=default",
        dataset, commit1.value()
    );
    ResponseEntity<BlameResponse> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        BlameResponse.class
    );

    // Then: Should return quads from default graph
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BlameResponse body = response.getBody();
    assertThat(body.graph()).isEqualTo("default");
    assertThat(body.quads()).hasSize(2);
    assertThat(body.quads()).allMatch(
        q -> "Alice <alice@example.org>".equals(q.lastModifiedBy())
    );
  }

  @Test
  void blameGraph_missingGraphParameter_shouldReturn400() {
    // Given: Valid dataset and commit
    String dataset = "test-missing-graph";
    String graph = "http://example.org/data";

    String patch1 = String.format(
        "TX .%nA <http://ex.org/s1> <http://ex.org/p1> \"value1\" <%s> .%nTC .",
        graph
    );
    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Add quad",
        patch1
    );

    // When: Request without graph parameter
    String url = String.format(
        "/version/blame?dataset=%s&commit=%s",
        dataset, commit1.value()
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
  void blameGraph_graphNotInCommit_shouldReturn404() {
    // Given: Graph with data
    String dataset = "test-missing-graph-404";
    String graph1 = "http://example.org/graph1";
    String graph2 = "http://example.org/graph2";

    String patch1 = String.format(
        "TX .%nA <http://ex.org/s1> <http://ex.org/p1> \"value1\" <%s> .%nTC .",
        graph1
    );
    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Add to graph1",
        patch1
    );

    // When: Request blame for non-existent graph
    String url = String.format(
        "/version/blame?dataset=%s&commit=%s&graph=%s",
        dataset, commit1.value(), graph2
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
  void blameGraph_limitExceedsMaximum_shouldReturn400() {
    // Given: Valid dataset
    String dataset = "test-limit-validation";
    String graph = "http://example.org/data";

    String patch1 = String.format(
        "TX .%nA <http://ex.org/s1> <http://ex.org/p1> \"value1\" <%s> .%nTC .",
        graph
    );
    CommitId commit1 = createCommit(
        dataset,
        List.of(),
        "Alice <alice@example.org>",
        "Add quad",
        patch1
    );

    // When: Request with limit > 1000
    String url = String.format(
        "/version/blame?dataset=%s&commit=%s&graph=%s&limit=1001",
        dataset, commit1.value(), graph
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
  void blameGraph_commitNotFound_shouldReturn404() {
    // When: Request with non-existent commit
    String url = "/version/blame?dataset=test&commit=01933e4a-0000-7000-8000-000000000000"
        + "&graph=http://example.org/data";
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        String.class
    );

    // Then: Should return 404 Not Found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
