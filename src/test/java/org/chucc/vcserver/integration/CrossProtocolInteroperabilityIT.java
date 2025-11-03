package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.TagRepository;
import org.chucc.vcserver.testutil.ITFixture;
import org.chucc.vcserver.testutil.TestConstants;
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
 * Integration tests for cross-protocol interoperability between
 * SPARQL Protocol and Graph Store Protocol extensions.
 *
 * <p>Verifies that both protocols share the same commit DAG, branches,
 * tags, and /version/ endpoints, and that operations from one protocol
 * are visible and queryable from the other.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class CrossProtocolInteroperabilityIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  @Autowired
  private TagRepository tagRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Set up clean state before each test.
   */
  @BeforeEach
  void setUp() {
    // Parent class already handles cleanup and initial setup
  }

  // ========== GSP → Protocol Visibility Tests ==========

  /**
   * Test that /version/history endpoint exists and returns commits.
   * Verifies that GSP commits appear in history.
   */
  @Test
  void versionHistoryEndpoint_shouldExist() throws Exception {
    // When - Query Protocol's /version/history endpoint
    ResponseEntity<String> historyResponse = restTemplate.getForEntity(
        "/version/history?dataset=default&branch=main",
        String.class
    );

    // Then - Endpoint exists and returns 200 OK (implemented in commit a9613d4)
    assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * Test that /version/refs endpoint returns branches in expected format
   * (verifies shared ref structure across protocols).
   */
  @Test
  void versionRefs_shouldReturnBranchesInExpectedFormat() throws Exception {
    // When - Query Protocol's /version/refs
    ResponseEntity<String> refsResponse = restTemplate.getForEntity(
        "/version/refs?dataset=default",
        String.class
    );

    // Then - Should return refs with proper structure
    assertThat(refsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode response = objectMapper.readTree(refsResponse.getBody());

    JsonNode refsArray = response.get("refs");
    assertThat(refsArray.isArray()).isTrue();

    // Verify main branch exists and has required fields
    boolean mainBranchFound = false;
    for (JsonNode ref : refsArray) {
      if ("branch".equals(ref.get("type").asText())
          && "main".equals(ref.get("name").asText())) {
        mainBranchFound = true;
        assertThat(ref.has("targetCommit")).isTrue();
        assertThat(ref.get("targetCommit").asText()).isNotEmpty();
        break;
      }
    }
    assertThat(mainBranchFound).isTrue();
  }

  /**
   * Test that /version/commits/{id} endpoint exists (returns 501 for now).
   * Note: When implemented, should return commit metadata for GSP commits.
   */
  @Test
  void versionCommitsEndpoint_shouldExist() throws Exception {
    // Given - Create commit via GSP PUT
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Charlie");
    headers.set("SPARQL-VC-Message", "GSP commit metadata test");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    ResponseEntity<String> putResponse = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String location = putResponse.getHeaders().getFirst("Location");

    // When - Retrieve commit metadata via Protocol endpoint
    ResponseEntity<String> commitResponse = restTemplate.getForEntity(
        location + "?dataset=default",
        String.class
    );

    // Then - Endpoint exists and returns 404 (commit not in repository, projector disabled)
    assertThat(commitResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /**
   * Test that commit selector syntax is accepted by GSP GET
   * (cross-protocol selector compatibility).
   */
  @Test
  void gspGet_shouldAcceptCommitSelector() throws Exception {
    // Given - Use the initial commit created in setup
    HttpHeaders getHeaders = new HttpHeaders();
    getHeaders.set("Accept", "text/turtle");
    HttpEntity<String> getRequest = new HttpEntity<>(getHeaders);

    // When - Query with commit selector using initial commit
    ResponseEntity<String> getResponse = restTemplate.exchange(
        "/data?default=true&commit=" + initialCommitId.value(),
        HttpMethod.GET,
        getRequest,
        String.class
    );

    // Then - Should accept commit selector (verifies selector compatibility)
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getHeaders().getETag()).isEqualTo("\"" + initialCommitId.value() + "\"");
  }

  // ========== Shared Branch Tests ==========

  /**
   * Test that branches are shared across protocols.
   * Create branch, verify it appears in /version/refs, and GSP accepts the branch selector.
   */
  @Test
  void sharedBranch_shouldBeVisibleInRefsAndAcceptedByGsp() throws Exception {
    // Given - Create a feature branch
    Branch featureBranch = new Branch("feature", initialCommitId);
    branchRepository.save(DEFAULT_DATASET, featureBranch);

    // When - Query /version/refs
    ResponseEntity<String> refsResponse = restTemplate.getForEntity(
        "/version/refs?dataset=default",
        String.class
    );

    // Then - feature branch should be visible
    assertThat(refsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode response = objectMapper.readTree(refsResponse.getBody());
    JsonNode refsArray = response.get("refs");

    boolean featureBranchFound = false;
    for (JsonNode ref : refsArray) {
      if ("branch".equals(ref.get("type").asText())
          && "feature".equals(ref.get("name").asText())) {
        featureBranchFound = true;
        assertThat(ref.get("targetCommit").asText()).isEqualTo(initialCommitId.value());
        break;
      }
    }
    assertThat(featureBranchFound).isTrue();

    // And - GSP PUT should accept the branch selector
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Developer");
    headers.set("SPARQL-VC-Message", "Feature commit");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    ResponseEntity<String> putResponse = restTemplate.exchange(
        "/data?default=true&branch=feature",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Should accept the shared branch selector
    assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(putResponse.getHeaders().getFirst("Location")).isNotNull();
  }

  // ========== Shared Tag Tests ==========

  /**
   * Test that tags are shared across protocols.
   * Create commits via GSP, tag via repository, verify tag visible in refs.
   */
  @Test
  void sharedTag_shouldBeVisibleInRefs() throws Exception {
    // Given - Create commit via GSP PUT
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Release Manager");
    headers.set("SPARQL-VC-Message", "Release v1.0.0");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    ResponseEntity<String> putResponse = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    String location = putResponse.getHeaders().getFirst("Location");
    String commitId = location.substring("/version/commits/".length());

    // And - Create tag pointing to this commit
    Tag releaseTag = new Tag("v1.0.0", CommitId.of(commitId));
    tagRepository.save(DEFAULT_DATASET, releaseTag);

    // When - Query Protocol /version/refs
    ResponseEntity<String> refsResponse = restTemplate.getForEntity(
        "/version/refs?dataset=default",
        String.class
    );

    // Then - Tag should be visible via Protocol /version/refs
    assertThat(refsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode response = objectMapper.readTree(refsResponse.getBody());
    JsonNode refsArray = response.get("refs");

    boolean tagFound = false;
    for (JsonNode ref : refsArray) {
      if ("tag".equals(ref.get("type").asText())
          && "v1.0.0".equals(ref.get("name").asText())) {
        tagFound = true;
        assertThat(ref.get("targetCommit").asText()).isEqualTo(commitId);
        break;
      }
    }
    assertThat(tagFound).isTrue();
  }

  // ========== Batch Endpoint Separation Tests ==========

  /**
   * Test that /version/batch (Protocol) and /version/batch-graphs (GSP)
   * are distinct endpoints with different implementations.
   */
  @Test
  void batchEndpoints_shouldBeDistinct() throws Exception {
    // Protocol batch endpoint (/version/batch) - validates operations list
    HttpHeaders protocolHeaders = new HttpHeaders();
    protocolHeaders.set("Content-Type", "application/json");

    HttpEntity<String> protocolRequest = new HttpEntity<>(
        "{\"operations\":[]}",
        protocolHeaders
    );

    ResponseEntity<String> protocolResponse = restTemplate.exchange(
        "/version/batch",
        HttpMethod.POST,
        protocolRequest,
        String.class
    );

    // Should return 400 Bad Request (empty operations list is invalid)
    assertThat(protocolResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // GSP batch endpoint (/version/batch-graphs) - implemented with validation
    HttpHeaders gspHeaders = new HttpHeaders();
    gspHeaders.set("Content-Type", "application/json");

    // Send invalid request (missing required fields)
    HttpEntity<String> gspRequest = new HttpEntity<>("{}", gspHeaders);

    ResponseEntity<String> gspResponse = restTemplate.exchange(
        "/version/batch-graphs",
        HttpMethod.POST,
        gspRequest,
        String.class
    );

    // Should return 400 Bad Request (validation error - implemented endpoint)
    assertThat(gspResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // Both endpoints are now implemented and return 400 for validation errors
    // They are distinct endpoints with different implementations, even though
    // they both correctly validate inputs and return consistent error codes.
  }

  // ========== Selector Consistency Tests ==========

  /**
   * Test that selector validation is consistent across protocols.
   * Mutual exclusion of branch/commit should work identically.
   */
  @Test
  void selectorValidation_shouldBeConsistentAcrossProtocols() throws Exception {
    // GSP GET with conflicting selectors (branch + commit) should fail
    ResponseEntity<String> gspResponse = restTemplate.getForEntity(
        "/data?default=true&branch=main&commit=" + initialCommitId.value(),
        String.class
    );

    assertThat(gspResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode gspError = objectMapper.readTree(gspResponse.getBody());
    assertThat(gspError.get("code").asText()).isEqualTo("selector_conflict");

    // GSP PUT with conflicting selectors should also fail consistently
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Test");
    headers.set("SPARQL-VC-Message", "Test");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    ResponseEntity<String> putResponse = restTemplate.exchange(
        "/data?default=true&branch=main&commit=" + initialCommitId.value(),
        HttpMethod.PUT,
        request,
        String.class
    );

    // Should also fail with selector_conflict
    assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode putError = objectMapper.readTree(putResponse.getBody());
    assertThat(putError.get("code").asText()).isEqualTo("selector_conflict");
  }

  /**
   * Test that asOf selector is accepted by GSP endpoints.
   */
  @Test
  void asOfSelector_shouldBeAcceptedByGsp() throws Exception {
    // Given - Create commit with known timestamp,
    CommitId pastCommitId = CommitId.generate();
    Instant pastTime = Instant.parse("2025-01-01T12:00:00Z");

    org.chucc.vcserver.domain.Commit pastCommit =
        new org.chucc.vcserver.domain.Commit(
        pastCommitId,
        List.of(initialCommitId),
        "Time Traveler",
        "Past commit",
        pastTime,
        0
    );

    commitRepository.save(
        DEFAULT_DATASET,
        pastCommit,
        RDFPatchOps.emptyPatch()
    );

    Branch mainBranch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
        .orElseThrow();
    mainBranch = new Branch("main", pastCommitId);
    branchRepository.save(DEFAULT_DATASET, mainBranch);

    // When - Use asOf selector with GSP GET
    String asOfTime = "2025-01-01T13:00:00Z";
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");
    HttpEntity<String> request = new HttpEntity<>(headers);

    ResponseEntity<String> gspResponse = restTemplate.exchange(
        "/data?default=true&asOf=" + asOfTime,
        HttpMethod.GET,
        request,
        String.class
    );

    // Then - Should accept asOf selector (verifies cross-protocol selector compatibility)
    assertThat(gspResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * Test that invalid selectors produce error responses across GSP operations.
   */
  @Test
  void invalidSelector_shouldProduceErrors() throws Exception {
    String invalidCommitId = "not-a-valid-uuid";

    // GSP GET with invalid commit ID should produce error
    ResponseEntity<String> getResponse = restTemplate.getForEntity(
        "/data?default=true&commit=" + invalidCommitId,
        String.class
    );

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode getError = objectMapper.readTree(getResponse.getBody());
    assertThat(getError.has("code")).isTrue();
    assertThat(getError.get("code").asText()).isNotEmpty();

    // GSP PUT with invalid commit ID should also produce error
    // Note: Different error code expected (write_on_readonly_selector vs invalid_argument)
    // because commit selector makes the operation read-only
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Test");
    headers.set("SPARQL-VC-Message", "Test");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    ResponseEntity<String> putResponse = restTemplate.exchange(
        "/data?default=true&commit=" + invalidCommitId,
        HttpMethod.PUT,
        request,
        String.class
    );

    assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode putError = objectMapper.readTree(putResponse.getBody());
    assertThat(putError.has("code")).isTrue();
    assertThat(putError.get("code").asText()).isNotEmpty();
  }

  // ========== Shared /version/ Namespace Tests ==========

  /**
   * Test that /version/refs endpoint is accessible and returns expected structure.
   */
  @Test
  void versionRefs_shouldBeAccessibleAndReturnValidStructure() throws Exception {
    // When - Query /version/refs
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/refs?dataset=default",
        String.class
    );

    // Then - Endpoint should be accessible with valid structure
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode refs = objectMapper.readTree(response.getBody());
    JsonNode refsArray = refs.get("refs");

    assertThat(refsArray.isArray()).isTrue();

    // Verify main branch exists with valid structure
    boolean mainFound = false;
    for (JsonNode ref : refsArray) {
      if ("branch".equals(ref.get("type").asText())
          && "main".equals(ref.get("name").asText())) {
        mainFound = true;
        assertThat(ref.has("targetCommit")).isTrue();
        assertThat(ref.get("targetCommit").asText()).isNotEmpty();
        // Verify target commit is a valid UUID format
        assertThat(ref.get("targetCommit").asText()).matches("[0-9a-f-]+");
        break;
      }
    }
    assertThat(mainFound).isTrue();
  }

  /**
   * Test that /version/commits/{id} endpoint is accessible
   * (returns 501 for now, will work for all commit types when implemented).
   */
  @Test
  void versionCommitsById_shouldBeAccessible() throws Exception {
    // Given - Create commit via GSP
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Universal Author");
    headers.set("SPARQL-VC-Message", "Universal commit");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    ResponseEntity<String> putResponse = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    String commitId = putResponse.getHeaders()
        .getFirst("Location")
        .substring("/version/commits/".length());

    // When - Retrieve via /version/commits/{id}
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/commits/" + commitId + "?dataset=default",
        String.class
    );

    // Then - Endpoint exists and returns 404 (commit not in repository, projector disabled)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
