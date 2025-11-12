package org.chucc.vcserver.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for POST /version/merge endpoint (Phase 1).
 * Tests fast-forward detection, three-way merge, and conflict detection.
 *
 * <p><strong>Note:</strong> This test uses event-driven setup via ITFixture.
 * The projector is DISABLED by default (API layer tests only).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class MergeOperationsIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String DATASET_NAME = "test-merge-ops";
  private static final String BASE_URL = "/" + DATASET_NAME + "/version/merge";

  private CommitId mainCommit2Id;
  private CommitId featureCommit1Id;

  @Override
  protected String getDatasetName() {
    return DATASET_NAME;
  }

  /**
   * Sets up two branches: main and feature with different commit histories.
   *
   * <p>ITFixture already created initial commit (initialCommitId) and main branch.
   * We create feature branch directly as test setup (acceptable for simple branch creation).
   *
   * <p><strong>Note:</strong> Uses direct repository write for feature branch. This is acceptable
   * for test setup when the projector is disabled. Tests remain API layer tests (no projector).
   */
  @BeforeEach
  void setUp() {
    // Create feature branch starting from same commit as main
    branchRepository.save(DATASET_NAME, new org.chucc.vcserver.domain.Branch("feature", initialCommitId));
  }

  /**
   * Test fast-forward merge when target branch is ancestor of source.
   */
  @Test
  void fastForwardMerge_shouldMovePointer() {
    // Arrange: Add commit to feature branch
    featureCommit1Id = CommitId.generate();
    RDFPatch patch = createPatch(
        NodeFactory.createURI("http://ex.org/g1"),
        NodeFactory.createURI("http://ex.org/s1"),
        NodeFactory.createURI("http://ex.org/p1"),
        NodeFactory.createLiteralString("value1")
    );
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Feature commit 1",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, patch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge feature into main
    String requestBody = """
        {
          "into": "main",
          "from": "feature"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Fast-forward response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("fast-forward");
    assertThat(body.get("into").asText()).isEqualTo("main");
    assertThat(body.get("from").asText()).isEqualTo("feature");
    assertThat(body.get("headCommit").asText()).isEqualTo(featureCommit1Id.value());
    assertThat(body.get("fastForward").asBoolean()).isTrue();
  }

  /**
   * Test no-op merge when branches point to same commit.
   */
  @Test
  void noOpMerge_shouldReturn204() {
    // Act: Merge feature into main (both at same commit)
    String requestBody = """
        {
          "into": "main",
          "from": "feature"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: No-op merge returns 204
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  /**
   * Test three-way merge when branches have diverged without conflicts.
   */
  @Test
  void threeWayMerge_noConflicts_shouldCreateMergeCommit() {
    // Arrange: Diverge branches
    // Main: add triple to g1
    mainCommit2Id = CommitId.generate();
    RDFPatch mainPatch = createPatch(
        NodeFactory.createURI("http://ex.org/g1"),
        NodeFactory.createURI("http://ex.org/s1"),
        NodeFactory.createURI("http://ex.org/p1"),
        NodeFactory.createLiteralString("main-value")
    );
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Main commit",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    // Feature: add triple to g2 (different graph - no conflict)
    featureCommit1Id = CommitId.generate();
    RDFPatch featurePatch = createPatch(
        NodeFactory.createURI("http://ex.org/g2"),
        NodeFactory.createURI("http://ex.org/s2"),
        NodeFactory.createURI("http://ex.org/p2"),
        NodeFactory.createLiteralString("feature-value")
    );
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Feature commit",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featurePatch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge feature into main
    String requestBody = """
        {
          "into": "main",
          "from": "feature"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Merge commit created
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("merged");
    assertThat(body.get("into").asText()).isEqualTo("main");
    assertThat(body.get("from").asText()).isEqualTo("feature");
    assertThat(body.get("mergeCommit").asText()).isNotBlank();
    assertThat(body.get("fastForward").asBoolean()).isFalse();
    assertThat(body.get("strategy").asText()).isEqualTo("three-way");
    assertThat(body.get("conflictsResolved").asInt()).isEqualTo(0);
  }

  /**
   * Test merge conflict detection when branches modify same quad.
   */
  @Test
  void threeWayMerge_withConflicts_shouldReturn409() {
    // Arrange: Diverge branches with conflicting changes
    // Main: add triple
    mainCommit2Id = CommitId.generate();
    RDFPatch mainPatch = createPatch(
        NodeFactory.createURI("http://ex.org/g1"),
        NodeFactory.createURI("http://ex.org/s1"),
        NodeFactory.createURI("http://ex.org/p1"),
        NodeFactory.createLiteralString("main-value")
    );
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Main commit",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    // Feature: add SAME triple (conflict!)
    featureCommit1Id = CommitId.generate();
    RDFPatch featurePatch = createPatch(
        NodeFactory.createURI("http://ex.org/g1"),
        NodeFactory.createURI("http://ex.org/s1"),
        NodeFactory.createURI("http://ex.org/p1"),
        NodeFactory.createLiteralString("feature-value")  // Different value, same quad
    );
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Feature commit",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featurePatch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge feature into main
    String requestBody = """
        {
          "into": "main",
          "from": "feature"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Conflict detected
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("status").asInt()).isEqualTo(409);
    assertThat(body.get("code").asText()).isEqualTo("merge_conflict");
    assertThat(body.has("conflicts")).isTrue();
    assertThat(body.get("conflicts").isArray()).isTrue();
    assertThat(body.get("conflicts").size()).isGreaterThan(0);
  }

  /**
   * Test fast-forward only mode succeeds when fast-forward is possible.
   */
  @Test
  void fastForwardOnlyMode_whenPossible_shouldSucceed() {
    // Arrange: Add commit to feature branch
    featureCommit1Id = CommitId.generate();
    RDFPatch patch = createPatch(
        NodeFactory.createURI("http://ex.org/g1"),
        NodeFactory.createURI("http://ex.org/s1"),
        NodeFactory.createURI("http://ex.org/p1"),
        NodeFactory.createLiteralString("value1")
    );
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Feature commit",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, patch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge with fastForward=only
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "fastForward": "only"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Fast-forward succeeds
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("fast-forward");
  }

  /**
   * Test fast-forward only mode fails when branches have diverged.
   */
  @Test
  void fastForwardOnlyMode_whenNotPossible_shouldReturn422() {
    // Arrange: Diverge branches
    mainCommit2Id = CommitId.generate();
    RDFPatch mainPatch = createPatch(
        NodeFactory.createURI("http://ex.org/g1"),
        NodeFactory.createURI("http://ex.org/s1"),
        NodeFactory.createURI("http://ex.org/p1"),
        NodeFactory.createLiteralString("main-value")
    );
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Main commit",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    featureCommit1Id = CommitId.generate();
    RDFPatch featurePatch = createPatch(
        NodeFactory.createURI("http://ex.org/g2"),
        NodeFactory.createURI("http://ex.org/s2"),
        NodeFactory.createURI("http://ex.org/p2"),
        NodeFactory.createLiteralString("feature-value")
    );
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Feature commit",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featurePatch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge with fastForward=only (should fail)
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "fastForward": "only"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Unprocessable entity (422)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("status").asInt()).isEqualTo(422);
    assertThat(body.get("code").asText()).isEqualTo("fast_forward_required");
  }

  /**
   * Test fast-forward never mode creates merge commit even when fast-forward possible.
   */
  @Test
  void fastForwardNeverMode_shouldCreateMergeCommit() {
    // Arrange: Add commit to feature branch (fast-forward possible)
    featureCommit1Id = CommitId.generate();
    RDFPatch patch = createPatch(
        NodeFactory.createURI("http://ex.org/g1"),
        NodeFactory.createURI("http://ex.org/s1"),
        NodeFactory.createURI("http://ex.org/p1"),
        NodeFactory.createLiteralString("value1")
    );
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Feature commit",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, patch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge with fastForward=never
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "fastForward": "never"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Merge commit created (not fast-forward)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("merged");
    assertThat(body.get("fastForward").asBoolean()).isFalse();
    assertThat(body.has("mergeCommit")).isTrue();
  }

  /**
   * Test merge with invalid branch name returns 404.
   */
  @Test
  void merge_withInvalidBranch_shouldReturn404() {
    // Act: Merge from non-existent branch
    String requestBody = """
        {
          "into": "main",
          "from": "non-existent"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Not found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("status").asInt()).isEqualTo(404);
    assertThat(body.get("code").asText()).isEqualTo("ref_not_found");
  }

  /**
   * Test merge with invalid request body returns 400.
   */
  @Test
  void merge_withInvalidRequest_shouldReturn400() {
    // Act: Missing required field
    String requestBody = """
        {
          "into": "main"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Bad request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("status").asInt()).isEqualTo(400);
    assertThat(body.get("code").asText()).isEqualTo("invalid_request");
  }

  // ========== Phase 2: Conflict Resolution Strategy Tests ==========

  /**
   * Test "ours" strategy with graph-level scope resolves conflicts per graph.
   * When Graph-A has conflicts, keep "into" changes for Graph-A only.
   * Non-conflicting Graph-B changes from "from" should still be merged.
   */
  @Test
  void merge_oursStrategy_graphScope_shouldResolveConflictingGraphsOnly() {
    // Arrange: Create divergent branches with conflict in Graph-A, no conflict in Graph-B
    // Main branch: Update Graph-A
    mainCommit2Id = CommitId.generate();
    RDFChangesCollector mainCollector = new RDFChangesCollector();
    mainCollector.start();
    mainCollector.add(
        NodeFactory.createURI("http://ex.org/graphA"),
        NodeFactory.createURI("http://ex.org/Alice"),
        NodeFactory.createURI("http://ex.org/age"),
        NodeFactory.createLiteralString("31")
    );
    mainCollector.finish();
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Update Alice's age to 31",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainCollector.getRDFPatch());
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    // Feature branch: Conflicting update to Graph-A + new triple in Graph-B
    featureCommit1Id = CommitId.generate();
    RDFChangesCollector featureCollector = new RDFChangesCollector();
    featureCollector.start();
    // Conflict: Same subject/predicate in Graph-A, different value
    featureCollector.add(
        NodeFactory.createURI("http://ex.org/graphA"),
        NodeFactory.createURI("http://ex.org/Alice"),
        NodeFactory.createURI("http://ex.org/age"),
        NodeFactory.createLiteralString("32")
    );
    // No conflict: Different graph (Graph-B)
    featureCollector.add(
        NodeFactory.createURI("http://ex.org/graphB"),
        NodeFactory.createURI("http://ex.org/Bob"),
        NodeFactory.createURI("http://ex.org/name"),
        NodeFactory.createLiteralString("Bob")
    );
    featureCollector.finish();
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Update Alice to 32 and add Bob",
        Instant.now(),
        2
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featureCollector.getRDFPatch());
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge with graph-level "ours" strategy
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "strategy": "ours",
          "conflictScope": "graph"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Merge succeeds with conflicts resolved
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("merged");
    assertThat(body.get("into").asText()).isEqualTo("main");
    assertThat(body.get("from").asText()).isEqualTo("feature");
    assertThat(body.get("strategy").asText()).isEqualTo("ours");
    assertThat(body.get("conflictScope").asText()).isEqualTo("graph");
    assertThat(body.get("conflictsResolved").asInt()).isGreaterThan(0);
    assertThat(body.get("mergeCommit").asText()).isNotBlank();
    assertThat(body.get("fastForward").asBoolean()).isFalse();
  }

  /**
   * Test "ours" strategy with dataset-level scope discards all "from" changes.
   * When ANY conflict exists, keep ALL "into" changes (including non-conflicting graphs).
   */
  @Test
  void merge_oursStrategy_datasetScope_shouldDiscardAllFromChanges() {
    // Arrange: Same setup as graph-level test
    mainCommit2Id = CommitId.generate();
    RDFChangesCollector mainCollector = new RDFChangesCollector();
    mainCollector.start();
    mainCollector.add(
        NodeFactory.createURI("http://ex.org/graphA"),
        NodeFactory.createURI("http://ex.org/Alice"),
        NodeFactory.createURI("http://ex.org/age"),
        NodeFactory.createLiteralString("31")
    );
    mainCollector.finish();
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Update Alice's age to 31",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainCollector.getRDFPatch());
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    featureCommit1Id = CommitId.generate();
    RDFChangesCollector featureCollector = new RDFChangesCollector();
    featureCollector.start();
    featureCollector.add(
        NodeFactory.createURI("http://ex.org/graphA"),
        NodeFactory.createURI("http://ex.org/Alice"),
        NodeFactory.createURI("http://ex.org/age"),
        NodeFactory.createLiteralString("32")
    );
    featureCollector.add(
        NodeFactory.createURI("http://ex.org/graphB"),
        NodeFactory.createURI("http://ex.org/Bob"),
        NodeFactory.createURI("http://ex.org/name"),
        NodeFactory.createLiteralString("Bob")
    );
    featureCollector.finish();
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Update Alice to 32 and add Bob",
        Instant.now(),
        2
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featureCollector.getRDFPatch());
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge with dataset-level "ours" strategy
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "strategy": "ours",
          "conflictScope": "dataset"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Merge succeeds, all "from" changes discarded (including Graph-B)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("merged");
    assertThat(body.get("strategy").asText()).isEqualTo("ours");
    assertThat(body.get("conflictScope").asText()).isEqualTo("dataset");
    assertThat(body.get("conflictsResolved").asInt()).isGreaterThan(0);
  }

  /**
   * Test "theirs" strategy with graph-level scope keeps "from" changes in conflicting graphs.
   * Non-conflicting "into" graphs should still be merged.
   */
  @Test
  void merge_theirsStrategy_graphScope_shouldKeepFromChangesInConflictingGraphs() {
    // Arrange: Divergent branches
    // Main: Add to Graph-A and Graph-C
    mainCommit2Id = CommitId.generate();
    RDFChangesCollector mainCollector = new RDFChangesCollector();
    mainCollector.start();
    mainCollector.add(
        NodeFactory.createURI("http://ex.org/graphA"),
        NodeFactory.createURI("http://ex.org/Alice"),
        NodeFactory.createURI("http://ex.org/age"),
        NodeFactory.createLiteralString("31")
    );
    mainCollector.add(
        NodeFactory.createURI("http://ex.org/graphC"),
        NodeFactory.createURI("http://ex.org/Charlie"),
        NodeFactory.createURI("http://ex.org/email"),
        NodeFactory.createLiteralString("charlie@example.com")
    );
    mainCollector.finish();
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Update Alice and add Charlie",
        Instant.now(),
        2
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainCollector.getRDFPatch());
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    // Feature: Conflicting update to Graph-A
    featureCommit1Id = CommitId.generate();
    RDFChangesCollector featureCollector = new RDFChangesCollector();
    featureCollector.start();
    featureCollector.add(
        NodeFactory.createURI("http://ex.org/graphA"),
        NodeFactory.createURI("http://ex.org/Alice"),
        NodeFactory.createURI("http://ex.org/age"),
        NodeFactory.createLiteralString("32")
    );
    featureCollector.finish();
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Update Alice to 32",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featureCollector.getRDFPatch());
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge with graph-level "theirs" strategy
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "strategy": "theirs",
          "conflictScope": "graph"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Merge succeeds, Graph-A from "feature", Graph-C from "main"
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("merged");
    assertThat(body.get("strategy").asText()).isEqualTo("theirs");
    assertThat(body.get("conflictScope").asText()).isEqualTo("graph");
    assertThat(body.get("conflictsResolved").asInt()).isGreaterThan(0);
  }

  /**
   * Test "theirs" strategy with dataset-level scope discards all "into" changes.
   */
  @Test
  void merge_theirsStrategy_datasetScope_shouldDiscardAllIntoChanges() {
    // Arrange: Same setup as above
    mainCommit2Id = CommitId.generate();
    RDFChangesCollector mainCollector = new RDFChangesCollector();
    mainCollector.start();
    mainCollector.add(
        NodeFactory.createURI("http://ex.org/graphA"),
        NodeFactory.createURI("http://ex.org/Alice"),
        NodeFactory.createURI("http://ex.org/age"),
        NodeFactory.createLiteralString("31")
    );
    mainCollector.add(
        NodeFactory.createURI("http://ex.org/graphC"),
        NodeFactory.createURI("http://ex.org/Charlie"),
        NodeFactory.createURI("http://ex.org/email"),
        NodeFactory.createLiteralString("charlie@example.com")
    );
    mainCollector.finish();
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Update Alice and add Charlie",
        Instant.now(),
        2
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainCollector.getRDFPatch());
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    featureCommit1Id = CommitId.generate();
    RDFChangesCollector featureCollector = new RDFChangesCollector();
    featureCollector.start();
    featureCollector.add(
        NodeFactory.createURI("http://ex.org/graphA"),
        NodeFactory.createURI("http://ex.org/Alice"),
        NodeFactory.createURI("http://ex.org/age"),
        NodeFactory.createLiteralString("32")
    );
    featureCollector.finish();
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Update Alice to 32",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featureCollector.getRDFPatch());
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge with dataset-level "theirs" strategy
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "strategy": "theirs",
          "conflictScope": "dataset"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Merge succeeds, all "into" changes discarded (Graph-A and Graph-C)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("merged");
    assertThat(body.get("strategy").asText()).isEqualTo("theirs");
    assertThat(body.get("conflictScope").asText()).isEqualTo("dataset");
    assertThat(body.get("conflictsResolved").asInt()).isGreaterThan(0);
  }

  /**
   * Test invalid strategy parameter returns 400.
   */
  @Test
  void merge_invalidStrategy_shouldReturn400() {
    // Act: Invalid strategy
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "strategy": "invalid-strategy"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Bad request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("status").asInt()).isEqualTo(400);
    assertThat(body.get("code").asText()).isEqualTo("invalid_request");
  }

  /**
   * Test invalid conflictScope parameter returns 400.
   */
  @Test
  void merge_invalidConflictScope_shouldReturn400() {
    // Act: Invalid conflictScope
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "strategy": "ours",
          "conflictScope": "invalid-scope"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Bad request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("status").asInt()).isEqualTo(400);
    assertThat(body.get("code").asText()).isEqualTo("invalid_request");
  }

  /**
   * Test prefix conflict detection during three-way merge.
   *
   * <p>Both branches modify the same prefix to different IRIs.
   */
  @Test
  void threeWayMerge_withPrefixConflict_shouldReturn409() {
    // Arrange: Create two branches with conflicting prefixes
    // Main branch: PA foaf: <http://xmlns.com/foaf/0.1/>
    CommitId mainCommit2Id = CommitId.generate();
    RDFPatch mainPrefixPatch = createPrefixPatch("foaf", "http://xmlns.com/foaf/0.1/");
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Add foaf prefix on main",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainPrefixPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    // Feature branch: PA foaf: <http://example.org/my-foaf#>
    CommitId featureCommit1Id = CommitId.generate();
    RDFPatch featurePrefixPatch = createPrefixPatch("foaf", "http://example.org/my-foaf#");
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Add different foaf prefix on feature",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featurePrefixPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Attempt merge without strategy
    String requestBody = """
        {
          "into": "main",
          "from": "feature"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Conflict detected
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("status").asInt()).isEqualTo(409);
    assertThat(body.get("type").asText()).isEqualTo("/problems/merge-conflict");

    // Verify prefix conflict is reported
    JsonNode conflicts = body.get("conflicts");
    assertThat(conflicts).isNotNull();
    assertThat(conflicts.isArray()).isTrue();
    assertThat(conflicts.size()).isGreaterThan(0);

    boolean foundPrefixConflict = false;
    for (JsonNode conflict : conflicts) {
      String subject = conflict.get("subject").asText();
      if (subject.startsWith("PREFIX:")) {
        foundPrefixConflict = true;
        assertThat(subject).isEqualTo("PREFIX:foaf");
        assertThat(conflict.get("predicate").asText()).isEqualTo("maps-to");
        assertThat(conflict.get("object").asText())
            .contains("http://xmlns.com/foaf/0.1/")
            .contains("http://example.org/my-foaf#");
      }
    }
    assertThat(foundPrefixConflict).isTrue();
  }

  /**
   * Test resolving prefix conflicts using "ours" strategy.
   */
  @Test
  void threeWayMerge_withPrefixConflict_oursStrategy_shouldResolve() {
    // Arrange: Create prefix conflict (same as previous test)
    CommitId mainCommit2Id = CommitId.generate();
    RDFPatch mainPrefixPatch = createPrefixPatch("foaf", "http://xmlns.com/foaf/0.1/");
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Add foaf prefix on main",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainPrefixPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    CommitId featureCommit1Id = CommitId.generate();
    RDFPatch featurePrefixPatch = createPrefixPatch("foaf", "http://example.org/my-foaf#");
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Add different foaf prefix on feature",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featurePrefixPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge with "ours" strategy
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "strategy": "ours"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Merge successful
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("merged");
    assertThat(body.get("into").asText()).isEqualTo("main");
    assertThat(body.get("from").asText()).isEqualTo("feature");
    assertThat(body.get("mergeCommit").asText()).isNotNull();
    assertThat(body.get("conflictsResolved").asInt()).isEqualTo(1);

    // Note: Verifying final prefix value requires projector-enabled test
  }

  /**
   * Test resolving prefix conflicts using "theirs" strategy.
   */
  @Test
  void threeWayMerge_withPrefixConflict_theirsStrategy_shouldResolve() {
    // Arrange: Create prefix conflict
    CommitId mainCommit2Id = CommitId.generate();
    RDFPatch mainPrefixPatch = createPrefixPatch("foaf", "http://xmlns.com/foaf/0.1/");
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Add foaf prefix on main",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainPrefixPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    CommitId featureCommit1Id = CommitId.generate();
    RDFPatch featurePrefixPatch = createPrefixPatch("foaf", "http://example.org/my-foaf#");
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Add different foaf prefix on feature",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featurePrefixPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge with "theirs" strategy
    String requestBody = """
        {
          "into": "main",
          "from": "feature",
          "strategy": "theirs"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Merge successful
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("merged");
    assertThat(body.get("conflictsResolved").asInt()).isEqualTo(1);
  }

  /**
   * Test that identical prefix changes don't create conflicts.
   */
  @Test
  void threeWayMerge_withIdenticalPrefixChanges_shouldNotConflict() {
    // Arrange: Both branches add same prefix with same IRI
    CommitId mainCommit2Id = CommitId.generate();
    RDFPatch mainPrefixPatch = createPrefixPatch("foaf", "http://xmlns.com/foaf/0.1/");
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Add foaf prefix on main",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainPrefixPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    CommitId featureCommit1Id = CommitId.generate();
    RDFPatch featurePrefixPatch = createPrefixPatch("foaf", "http://xmlns.com/foaf/0.1/");
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Add same foaf prefix on feature",
        Instant.now(),
        1
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featurePrefixPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Merge without strategy
    String requestBody = """
        {
          "into": "main",
          "from": "feature"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: No conflict (identical changes)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = parseJson(response.getBody());
    assertThat(body.get("result").asText()).isEqualTo("merged");
    // Identical changes result in no conflicts, so conflictsResolved should be 0
    assertThat(body.get("conflictsResolved").asInt()).isEqualTo(0);
  }

  /**
   * Test prefix conflict combined with quad conflict.
   */
  @Test
  void threeWayMerge_withPrefixAndQuadConflicts_shouldDetectBoth() {
    // Arrange: Create both prefix conflict and quad conflict
    // Main branch: PA foaf + quad
    CommitId mainCommit2Id = CommitId.generate();
    RDFPatch mainPatch = createPrefixAndQuadPatch(
        "foaf", "http://xmlns.com/foaf/0.1/",
        NodeFactory.createURI("http://ex.org/g1"),
        NodeFactory.createURI("http://ex.org/s1"),
        NodeFactory.createURI("http://ex.org/p1"),
        NodeFactory.createLiteralString("main-value")
    );
    Commit mainCommit2 = new Commit(
        mainCommit2Id,
        List.of(initialCommitId),
        "Alice",
        "Add foaf prefix and data on main",
        Instant.now(),
        2
    );
    commitRepository.save(DATASET_NAME, mainCommit2, mainPatch);
    branchRepository.updateBranchHead(DATASET_NAME, "main", mainCommit2Id);

    // Feature branch: Different PA foaf + conflicting quad
    CommitId featureCommit1Id = CommitId.generate();
    RDFPatch featurePatch = createPrefixAndQuadPatch(
        "foaf", "http://example.org/my-foaf#",
        NodeFactory.createURI("http://ex.org/g1"),
        NodeFactory.createURI("http://ex.org/s1"),
        NodeFactory.createURI("http://ex.org/p1"),
        NodeFactory.createLiteralString("feature-value")
    );
    Commit featureCommit1 = new Commit(
        featureCommit1Id,
        List.of(initialCommitId),
        "Bob",
        "Add different foaf prefix and data on feature",
        Instant.now(),
        2
    );
    commitRepository.save(DATASET_NAME, featureCommit1, featurePatch);
    branchRepository.updateBranchHead(DATASET_NAME, "feature", featureCommit1Id);

    // Act: Attempt merge
    String requestBody = """
        {
          "into": "main",
          "from": "feature"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "Alice");

    ResponseEntity<String> response = restTemplate.exchange(
        BASE_URL,
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert: Both conflicts detected
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    JsonNode body = parseJson(response.getBody());
    JsonNode conflicts = body.get("conflicts");
    assertThat(conflicts.size()).isEqualTo(2);  // One prefix conflict + one quad conflict

    // Verify both types of conflicts are present
    boolean foundPrefixConflict = false;
    boolean foundQuadConflict = false;
    for (JsonNode conflict : conflicts) {
      String subject = conflict.get("subject").asText();
      if (subject.startsWith("PREFIX:")) {
        foundPrefixConflict = true;
      } else {
        foundQuadConflict = true;
      }
    }
    assertThat(foundPrefixConflict).isTrue();
    assertThat(foundQuadConflict).isTrue();
  }

  // Helper methods

  private RDFPatch createPatch(Node g, Node s, Node p, Node o) {
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    collector.add(g, s, p, o);
    collector.finish();
    return collector.getRDFPatch();
  }

  private RDFPatch createPrefixPatch(String prefix, String iri) {
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    collector.addPrefix(null, prefix, iri);
    collector.finish();
    return collector.getRDFPatch();
  }

  private RDFPatch createPrefixAndQuadPatch(
      String prefix, String iri,
      Node g, Node s, Node p, Node o) {
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    collector.addPrefix(null, prefix, iri);
    collector.add(g, s, p, o);
    collector.finish();
    return collector.getRDFPatch();
  }

  private JsonNode parseJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse JSON: " + json, e);
    }
  }
}
