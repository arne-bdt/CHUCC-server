package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdfpatch.RDFPatchOps;
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
 * Integration tests for Graph Store Protocol GET operation.
 * Tests API layer (synchronous HTTP response validation).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class GraphStoreGetIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private static final String DATASET_NAME = "default";

  private CommitId commit1Id;
  private CommitId commit2Id;

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
    // Clean up repositories
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Create first commit with default graph data
    commit1Id = CommitId.generate();
    Commit commit1 = new Commit(
        commit1Id,
        java.util.List.of(),
        "System",
        "Add initial data",
        java.time.Instant.now()
    );

    String patch1 = "TX .\n"
        + "A <http://example.org/s1> <http://example.org/p1> \"value1\" .\n"
        + "TC .";
    commitRepository.save(DATASET_NAME, commit1,
        RDFPatchOps.read(new java.io.ByteArrayInputStream(patch1.getBytes(
            java.nio.charset.StandardCharsets.UTF_8))));

    // Create second commit with more default graph data
    commit2Id = CommitId.generate();
    Commit commit2 = new Commit(
        commit2Id,
        java.util.List.of(commit1Id),
        "System",
        "Add more data",
        java.time.Instant.now()
    );

    String patch2 = "TX .\n"
        + "A <http://example.org/s2> <http://example.org/p2> \"value2\" .\n"
        + "TC .";
    commitRepository.save(DATASET_NAME, commit2,
        RDFPatchOps.read(new java.io.ByteArrayInputStream(patch2.getBytes(
            java.nio.charset.StandardCharsets.UTF_8))));

    // Create main branch pointing to commit2
    Branch mainBranch = new Branch("main", commit2Id);
    branchRepository.save(DATASET_NAME, mainBranch);
  }

  // TODO: Named graph support - requires proper RDF Patch quad handling
  // @Test
  // void getGraph_shouldReturn200_whenNamedGraphExistsAtBranchHead() {
  //   Test retrieving named graphs from the dataset
  // }

  @Test
  void getGraph_shouldReturn200_whenDefaultGraphRequestedAtBranchHead() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");
    HttpEntity<String> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.GET,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().getFirst("SPARQL-Version-Control")).isEqualTo("true");

    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("http://example.org/s2");
    assertThat(body).contains("value2");
  }

  @Test
  void getGraph_shouldReturn200_whenDefaultGraphRequestedAtSpecificCommit() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");
    HttpEntity<String> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&commit=" + commit1Id.value(),
        HttpMethod.GET,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isEqualTo("\"" + commit1Id.value() + "\"");

    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("http://example.org/s1");
    assertThat(body).contains("value1");
  }

  @Test
  void getGraph_shouldReturn404_whenGraphDoesNotExist() {
    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/nonexistent&branch=main",
        HttpMethod.GET,
        HttpEntity.EMPTY,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("graph_not_found");
  }

  @Test
  void getGraph_shouldReturn406_whenAcceptHeaderUnsupported() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "unsupported/format");
    HttpEntity<String> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/graph1&branch=main",
        HttpMethod.GET,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
  }

  @Test
  void getGraph_shouldSerializeToNTriples_whenAcceptNTriples() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "application/n-triples");
    HttpEntity<String> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.GET,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/n-triples");

    String body = response.getBody();
    assertThat(body).isNotNull();
    // N-Triples format uses full URIs without prefixes
    assertThat(body).contains("<http://example.org/s");
    assertThat(body).contains("<http://example.org/p");
  }

  @Test
  void getGraph_shouldSerializeToJsonLd_whenAcceptJsonLd() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "application/ld+json");
    HttpEntity<String> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.GET,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/ld+json");

    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("@");
  }

  @Test
  void getGraph_shouldReturn400_whenBothGraphAndDefaultProvided() {
    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/graph1&default=true&branch=main",
        HttpMethod.GET,
        HttpEntity.EMPTY,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("selector_conflict");
  }

  @Test
  void getGraph_shouldReturn400_whenNeitherGraphNorDefaultProvided() {
    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?branch=main",
        HttpMethod.GET,
        HttpEntity.EMPTY,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("selector_conflict");
  }

  @Test
  void getGraph_shouldUseMainBranchByDefault_whenNoSelectorProvided() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");
    HttpEntity<String> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true",
        HttpMethod.GET,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isEqualTo("\"" + commit2Id.value() + "\"");
  }
}
