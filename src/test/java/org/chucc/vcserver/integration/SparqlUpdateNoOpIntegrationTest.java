package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration tests for no-op detection with SPARQL UPDATE operations.
 * Per SPARQL 1.2 Protocol §7: A SPARQL UPDATE that results in a no-op patch
 * MUST NOT create a new commit and should return 204 No Content.
 *
 * <p>Note: Projector is DISABLED (default for API layer testing).
 * These tests verify HTTP contract only - branch updates happen via event projection (async).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SparqlUpdateNoOpIntegrationTest {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private static final String DATASET_NAME = "default";
  private CommitId initialCommitId;

  @BeforeAll
  static void startKafka() {
    kafkaContainer = KafkaTestContainers.createKafkaContainer();
    // Container is started by KafkaTestContainers - shared across all tests
  }

  @DynamicPropertySource
  static void configureKafka(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    // Unique consumer group per test class to prevent cross-test event consumption
    registry.add("spring.kafka.consumer.group-id",
        () -> "test-" + System.currentTimeMillis() + "-" + Math.random());
  }

  @BeforeEach
  void setUp() {
    // Clean up repositories before each test
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Create initial commit with some data
    initialCommitId = CommitId.generate();
    Commit initialCommit = new Commit(
        initialCommitId,
        java.util.List.of(),
        "System",
        "Initial commit",
        java.time.Instant.now()
    );

    // Create a patch with initial data
    String initialPatch = "TX .\n"
        + "A <http://example.org/s1> <http://example.org/p1> \"value1\" .\n"
        + "TC .";

    commitRepository.save(DATASET_NAME, initialCommit,
        org.apache.jena.rdfpatch.RDFPatchOps.read(
            new java.io.ByteArrayInputStream(
                initialPatch.getBytes(java.nio.charset.StandardCharsets.UTF_8))));

    Branch mainBranch = new Branch("main", initialCommitId);
    branchRepository.save(DATASET_NAME, mainBranch);
  }

  @Test
  void sparqlUpdate_shouldReturn204_whenInsertingExistingData() {
    // Given: SPARQL UPDATE that inserts data that already exists
    String sparqlUpdate = "INSERT DATA { "
        + "<http://example.org/s1> <http://example.org/p1> \"value1\" . "
        + "}";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Try to insert existing data");

    HttpEntity<String> request = new HttpEntity<>(sparqlUpdate, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: should return 204 No Content (no-op)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Verify no Location header (no new commit)
    assertThat(response.getHeaders().getFirst("Location")).isNull();

    // Note: Branch update verification requires projector enabled (not in this test)
  }

  @Test
  void sparqlUpdate_shouldReturn204_whenDeletingNonExistentData() {
    // Given: SPARQL UPDATE that deletes data that doesn't exist
    String sparqlUpdate = "DELETE DATA { "
        + "<http://example.org/s99> <http://example.org/p99> \"non-existent\" . "
        + "}";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "Bob");
    headers.set("SPARQL-VC-Message", "Try to delete non-existent data");

    HttpEntity<String> request = new HttpEntity<>(sparqlUpdate, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: should return 204 No Content (no-op)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Note: Branch update verification requires projector enabled (not in this test)
  }

  @Test
  void sparqlUpdate_shouldReturn200_whenInsertingNewData() throws Exception {
    // Given: SPARQL UPDATE that inserts new data (not a no-op)
    String sparqlUpdate = "INSERT DATA { "
        + "<http://example.org/s2> <http://example.org/p2> \"value2\" . "
        + "}";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "Charlie");
    headers.set("SPARQL-VC-Message", "Insert new data");

    HttpEntity<String> request = new HttpEntity<>(sparqlUpdate, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: should return 200 OK or 201 Created (not a no-op)
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NO_CONTENT);

    // Verify Location header exists (new commit created)
    String location = response.getHeaders().getFirst("Location");
    assertThat(location).isNotNull();

    // Note: Branch update verification is handled by event projectors (async)
  }

  @Test
  void sparqlUpdate_shouldReturn204_whenWhereClauseMatchesNothing() {
    // Given: SPARQL UPDATE with WHERE clause that matches nothing
    String sparqlUpdate = "DELETE { ?s ?p ?o } "
        + "WHERE { ?s ?p ?o . FILTER(?s = <http://example.org/non-existent>) }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "Dave");
    headers.set("SPARQL-VC-Message", "Conditional update with no matches");

    HttpEntity<String> request = new HttpEntity<>(sparqlUpdate, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: should return 204 No Content (no-op)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Note: Branch update verification requires projector enabled (not in this test)
  }
}
