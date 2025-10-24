package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.BeforeEach;
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
 * Integration tests for Graph Store Protocol time-travel queries using asOf selector.
 * Verifies that asOf parameter works correctly for GET and HEAD operations
 * with inclusive timestamp semantics.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class GraphStoreTimeTravelIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private static final String DATASET_NAME = "default";
  private static final String GRAPH_IRI = "http://example.org/timetravel-graph";
  private static final String TURTLE_V1 = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"v1\" .";
  private static final String TURTLE_V2 = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"v2\" .";
  private static final String TURTLE_V3 = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"v3\" .";

  private CommitId commit1Id;
  private CommitId commit2Id;
  private CommitId commit3Id;
  private Instant timestamp1;
  private Instant timestamp2;
  private Instant timestamp3;

  /**
   * Set up test data with commits at different times.
   */
  @BeforeEach
  void setUp() {
    // Create commits with specific timestamps representing data evolution
    // Using relative timestamps to ensure tests work regardless of current date
    Instant baseTime = Instant.now().minus(java.time.Duration.ofDays(3));
    timestamp1 = baseTime;
    timestamp2 = baseTime.plus(java.time.Duration.ofDays(1));
    timestamp3 = baseTime.plus(java.time.Duration.ofDays(2));

    commit1Id = CommitId.generate();
    commit2Id = CommitId.generate();
    commit3Id = CommitId.generate();

    // Create patches for each version
    RDFPatch patch1 = createAddPatch(TURTLE_V1);
    RDFPatch patch2 = createAddPatch(TURTLE_V2);
    RDFPatch patch3 = createAddPatch(TURTLE_V3);

    Commit commit1 = new Commit(
        commit1Id,
        List.of(),
        "Alice",
        "Version 1",
        timestamp1
    );

    Commit commit2 = new Commit(
        commit2Id,
        List.of(commit1Id),
        "Bob",
        "Version 2",
        timestamp2
    );

    Commit commit3 = new Commit(
        commit3Id,
        List.of(commit2Id),
        "Charlie",
        "Version 3",
        timestamp3
    );

    commitRepository.save(DATASET_NAME, commit1, patch1);
    commitRepository.save(DATASET_NAME, commit2, patch2);
    commitRepository.save(DATASET_NAME, commit3, patch3);

    Branch mainBranch = new Branch("main", commit3Id);
    branchRepository.save(DATASET_NAME, mainBranch);
  }

  /**
   * Create an RDFPatch that adds triples from Turtle content.
   *
   * @param turtleContent Turtle content to add
   * @return RDFPatch
   */
  private RDFPatch createAddPatch(String turtleContent) {
    Model model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, new ByteArrayInputStream(
        turtleContent.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    RDFDataMgr.write(out, model, Lang.NTRIPLES);
    String ntriples = out.toString(StandardCharsets.UTF_8);

    StringBuilder patchText = new StringBuilder();
    patchText.append("TX .\n");
    for (String line : ntriples.split("\n")) {
      if (!line.trim().isEmpty()) {
        String cleanLine = line.trim();
        if (cleanLine.endsWith(".")) {
          cleanLine = cleanLine.substring(0, cleanLine.length() - 1).trim();
        }
        String[] parts = cleanLine.split("\\s+", 3);
        if (parts.length == 3) {
          patchText.append("A ").append(parts[0]).append(" ").append(parts[1])
              .append(" ").append(parts[2]).append(" <").append(GRAPH_IRI).append("> .\n");
        }
      }
    }
    patchText.append("TC .\n");

    return RDFPatchOps.read(new ByteArrayInputStream(
        patchText.toString().getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void getGraph_shouldReturn200WithHistoricalData_whenAsOfWithBranch() {
    // Given - Commits created in setUp() at timestamp1, timestamp2, timestamp3
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");

    // When - GET graph with asOf pointing to middle version (timestamp2)
    String url = String.format("/data?graph=%s&branch=main&asOf=%s",
        GRAPH_IRI, timestamp2.toString());
    HttpEntity<Void> request = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        request,
        String.class
    );

    // Then - Should return 200 with historical data from commit2
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getHeaders().getFirst("ETag")).isEqualTo("\"" + commit2Id.value() + "\"");
    assertThat(response.getHeaders().getFirst("SPARQL-Version-Control"))
        .isEqualTo("true");
  }

  @Test
  void getGraph_shouldBeInclusive_whenAsOfMatchesExactTimestamp() {
    // Given - Commit at exactly timestamp2
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");

    // When - GET graph with asOf at exact commit timestamp (inclusive)
    String url = String.format("/data?graph=%s&branch=main&asOf=%s",
        GRAPH_IRI, timestamp2.toString());
    HttpEntity<Void> request = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        request,
        String.class
    );

    // Then - Should return 200 (inclusive: commit at timestamp is selected)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getHeaders().getFirst("ETag")).isEqualTo("\"" + commit2Id.value() + "\"");
  }

  @Test
  void getGraph_shouldReturn200_whenAsOfWithBranchSelector() {
    // Given - Commits created in setUp()
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");

    // When - GET graph with asOf + branch (valid combination)
    String url = String.format("/data?graph=%s&branch=main&asOf=%s",
        GRAPH_IRI, timestamp3.toString());
    HttpEntity<Void> request = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        request,
        String.class
    );

    // Then - Should accept both parameters
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("ETag")).isEqualTo("\"" + commit3Id.value() + "\"");
  }

  @Test
  void getGraph_shouldReturn200_whenAsOfWithoutBranch() {
    // Given - Commits created in setUp()
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");

    // When - GET graph with asOf only (global time-travel)
    String url = String.format("/data?graph=%s&asOf=%s",
        GRAPH_IRI, timestamp2.toString());
    HttpEntity<Void> request = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        request,
        String.class
    );

    // Then - Should accept asOf without branch (valid per spec)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("ETag")).isEqualTo("\"" + commit2Id.value() + "\"");
  }

  @Test
  void getGraph_shouldReturn404_whenAsOfBeforeAnyCommit() {
    // Given - First commit is at timestamp1
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");

    // When - GET graph with asOf before any commit (timestamp1 minus 1 day)
    Instant veryOld = timestamp1.minus(java.time.Duration.ofDays(1));
    String url = String.format("/data?graph=%s&branch=main&asOf=%s",
        GRAPH_IRI, veryOld.toString());
    HttpEntity<Void> request = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        request,
        String.class
    );

    // Then - Should return 404 (no commit found)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void getGraph_shouldReturnLatest_whenAsOfInFuture() {
    // Given - Latest commit is at timestamp3
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");

    // When - GET graph with future asOf timestamp (timestamp3 plus 1 day)
    Instant future = timestamp3.plus(java.time.Duration.ofDays(1));
    String url = String.format("/data?graph=%s&branch=main&asOf=%s",
        GRAPH_IRI, future.toString());
    HttpEntity<Void> request = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        request,
        String.class
    );

    // Then - Should return 200 with latest commit (commit3)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getHeaders().getFirst("ETag")).isEqualTo("\"" + commit3Id.value() + "\"");
  }

  @Test
  void getGraph_shouldReturn400_whenAsOfAndCommitBothSpecified() {
    // Given - Commits created in setUp()

    // When - GET graph with both asOf and commit (mutually exclusive)
    String url = String.format("/data?graph=%s&commit=%s&asOf=%s",
        GRAPH_IRI, commit1Id.value(), timestamp2.toString());
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then - Should return 400 (invalid parameter combination)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("mutually exclusive");
  }

  @Test
  void getGraph_shouldReturn400_whenAsOfHasInvalidFormat() {
    // Given - Commits created in setUp()

    // When - GET graph with invalid asOf format
    String url = String.format("/data?graph=%s&branch=main&asOf=not-a-timestamp",
        GRAPH_IRI);
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then - Should return 400 (invalid timestamp format)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Invalid RFC 3339 timestamp");
  }

  @Test
  void headGraph_shouldReturn200_whenAsOfSelector() {
    // Given - Commits created in setUp()

    // When - HEAD graph with asOf
    String url = String.format("/data?graph=%s&branch=main&asOf=%s",
        GRAPH_IRI, timestamp2.toString());
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.HEAD,
        null,
        String.class
    );

    // Then - Should return 200 with headers only
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("ETag")).isEqualTo("\"" + commit2Id.value() + "\"");
    assertThat(response.getBody()).isNull();
  }

  @Test
  void putGraph_shouldReturn400_whenAsOfSpecified() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Attempt with asOf");

    // When - PUT with asOf (write operation)
    String url = String.format("/data?graph=%s&branch=main&asOf=%s",
        GRAPH_IRI, timestamp2.toString());
    HttpEntity<String> request = new HttpEntity<>(TURTLE_V1, headers);
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then - Should return 400 (asOf not allowed for writes)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Cannot write with asOf selector");
  }

  @Test
  void postGraph_shouldReturn400_whenAsOfSpecified() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Attempt with asOf");

    // When - POST with asOf (write operation)
    String url = String.format("/data?graph=%s&branch=main&asOf=%s",
        GRAPH_IRI, timestamp2.toString());
    HttpEntity<String> request = new HttpEntity<>(TURTLE_V1, headers);
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then - Should return 400 (asOf not allowed for writes)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Cannot write with asOf selector");
  }

  @Test
  void deleteGraph_shouldReturn400_whenAsOfSpecified() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Attempt with asOf");

    // When - DELETE with asOf (write operation)
    String url = String.format("/data?graph=%s&branch=main&asOf=%s",
        GRAPH_IRI, timestamp2.toString());
    HttpEntity<Void> request = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        url,
        HttpMethod.DELETE,
        request,
        String.class
    );

    // Then - Should return 400 (asOf not allowed for writes)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Cannot write with asOf selector");
  }
}
