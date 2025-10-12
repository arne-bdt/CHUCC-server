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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration tests for no-op patch detection.
 * Per SPARQL 1.2 Protocol §7: A no-op patch (applies cleanly but yields no dataset change)
 * MUST NOT create a new commit and should return 204 No Content.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class NoOpPatchIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private static final String DATASET_NAME = "test-dataset";
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
  void createCommit_shouldReturn204_whenPatchAddsExistingTriple() {
    // Given: patch that adds a triple that already exists
    String patchBody = "TX .\n"
        + "A <http://example.org/s1> <http://example.org/p1> \"value1\" .\n"
        + "TC .";

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Try to add existing triple");

    HttpEntity<String> request = new HttpEntity<>(patchBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: should return 204 No Content
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getBody()).isNullOrEmpty();

    // Verify no Location header (no new commit created)
    assertThat(response.getHeaders().getFirst("Location")).isNull();

    // Verify no ETag header
    assertThat(response.getHeaders().getFirst("ETag")).isNull();

    // Verify branch HEAD is unchanged (still points to initial commit)
    Branch branch = branchRepository.findByDatasetAndName(DATASET_NAME, "main")
        .orElseThrow();
    assertThat(branch.getCommitId()).isEqualTo(initialCommitId);

    // Note: Commit count verification not included as it depends on async projection
  }

  @Test
  void createCommit_shouldReturn204_whenPatchDeletesNonExistentTriple() {
    // Given: patch that deletes a triple that doesn't exist
    String patchBody = "TX .\n"
        + "D <http://example.org/s99> <http://example.org/p99> \"non-existent\" .\n"
        + "TC .";

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Bob");
    headers.set("SPARQL-VC-Message", "Try to delete non-existent triple");

    HttpEntity<String> request = new HttpEntity<>(patchBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: should return 204 No Content
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getBody()).isNullOrEmpty();

    // Verify branch HEAD is unchanged
    Branch branch = branchRepository.findByDatasetAndName(DATASET_NAME, "main")
        .orElseThrow();
    assertThat(branch.getCommitId()).isEqualTo(initialCommitId);
  }

  @Test
  void createCommit_shouldReturn204_whenPatchHasSelfCancelingOperations() {
    // Given: patch with operations that cancel each other out
    String patchBody = "TX .\n"
        + "A <http://example.org/s2> <http://example.org/p2> \"temp\" .\n"
        + "D <http://example.org/s2> <http://example.org/p2> \"temp\" .\n"
        + "TC .";

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Charlie");
    headers.set("SPARQL-VC-Message", "Self-canceling operations");

    HttpEntity<String> request = new HttpEntity<>(patchBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: should return 204 No Content
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getBody()).isNullOrEmpty();

    // Verify branch HEAD is unchanged
    Branch branch = branchRepository.findByDatasetAndName(DATASET_NAME, "main")
        .orElseThrow();
    assertThat(branch.getCommitId()).isEqualTo(initialCommitId);
  }

  @Test
  void createCommit_shouldReturn204_whenEmptyPatch() {
    // Given: completely empty patch (only TX/TC)
    String patchBody = "TX .\nTC .";

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Dave");
    headers.set("SPARQL-VC-Message", "Empty patch");

    HttpEntity<String> request = new HttpEntity<>(patchBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: should return 204 No Content
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getBody()).isNullOrEmpty();

    // Verify branch HEAD is unchanged
    Branch branch = branchRepository.findByDatasetAndName(DATASET_NAME, "main")
        .orElseThrow();
    assertThat(branch.getCommitId()).isEqualTo(initialCommitId);
  }

  @Test
  void createCommit_shouldReturn201_whenPatchAddsNewTriple() throws Exception {
    // Given: patch that adds a new triple (not a no-op)
    String patchBody = "TX .\n"
        + "A <http://example.org/s2> <http://example.org/p2> \"value2\" .\n"
        + "TC .";

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Eve");
    headers.set("SPARQL-VC-Message", "Add new triple");

    HttpEntity<String> request = new HttpEntity<>(patchBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: should return 201 Created (not a no-op)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Verify Location header exists
    String location = response.getHeaders().getFirst("Location");
    assertThat(location).isNotNull();
    assertThat(location).startsWith("/version/commits/");

    // Verify ETag header exists
    String etag = response.getHeaders().getFirst("ETag");
    assertThat(etag).isNotNull();

    // Note: Branch update verification is handled by event projectors (async)
  }

  @Test
  void createCommit_shouldReturn201_whenPatchDeletesExistingTriple() throws Exception {
    // Given: patch that deletes an existing triple (not a no-op)
    String patchBody = "TX .\n"
        + "D <http://example.org/s1> <http://example.org/p1> \"value1\" .\n"
        + "TC .";

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Frank");
    headers.set("SPARQL-VC-Message", "Delete existing triple");

    HttpEntity<String> request = new HttpEntity<>(patchBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: should return 201 Created (not a no-op)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Verify Location header exists
    String location = response.getHeaders().getFirst("Location");
    assertThat(location).isNotNull();

    // Verify ETag header exists
    String etag = response.getHeaders().getFirst("ETag");
    assertThat(etag).isNotNull();

    // Note: Branch update verification is handled by event projectors (async)
  }
}
