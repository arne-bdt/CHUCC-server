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
 * Integration tests for Graph Store Protocol HEAD operation.
 * Tests API layer (synchronous HTTP response validation).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class GraphStoreHeadIT {

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

  @Test
  void headGraph_shouldReturn200_whenDefaultGraphExistsAtBranchHead() {
    // When
    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.HEAD,
        HttpEntity.EMPTY,
        Void.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isEqualTo("\"" + commit2Id.value() + "\"");
    assertThat(response.getHeaders().getFirst("SPARQL-Version-Control")).isEqualTo("true");
    assertThat(response.getHeaders().getContentType()).isNotNull();
    assertThat(response.getBody()).isNull();
  }

  @Test
  void headGraph_shouldReturn200_whenDefaultGraphRequestedAtSpecificCommit() {
    // When
    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?default=true&commit=" + commit1Id.value(),
        HttpMethod.HEAD,
        HttpEntity.EMPTY,
        Void.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isEqualTo("\"" + commit1Id.value() + "\"");
    assertThat(response.getHeaders().getFirst("SPARQL-Version-Control")).isEqualTo("true");
    assertThat(response.getHeaders().getContentType()).isNotNull();
    assertThat(response.getBody()).isNull();
  }

  @Test
  void headGraph_shouldReturnSameHeadersAsGet_whenDefaultGraphRequested() {
    // Given - Execute both HEAD and GET requests
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");
    HttpEntity<String> getRequest = new HttpEntity<>(headers);

    ResponseEntity<Void> headResponse = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.HEAD,
        HttpEntity.EMPTY,
        Void.class
    );

    ResponseEntity<String> getResponse = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.GET,
        getRequest,
        String.class
    );

    // Then
    assertThat(headResponse.getStatusCode()).isEqualTo(getResponse.getStatusCode());
    assertThat(headResponse.getHeaders().getETag()).isEqualTo(getResponse.getHeaders().getETag());
    assertThat(headResponse.getHeaders().getFirst("SPARQL-Version-Control"))
        .isEqualTo(getResponse.getHeaders().getFirst("SPARQL-Version-Control"));
    assertThat(headResponse.getHeaders().getContentType())
        .isNotNull();
    assertThat(headResponse.getBody()).isNull();
    assertThat(getResponse.getBody()).isNotNull();
  }

  @Test
  void headGraph_shouldReturn404_whenGraphDoesNotExist() {
    // When
    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?graph=http://example.org/nonexistent&branch=main",
        HttpMethod.HEAD,
        HttpEntity.EMPTY,
        Void.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNull();
  }

  @Test
  void headGraph_shouldReturn400_whenBothGraphAndDefaultProvided() {
    // When
    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?graph=http://example.org/graph1&default=true&branch=main",
        HttpMethod.HEAD,
        HttpEntity.EMPTY,
        Void.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNull();
  }

  @Test
  void headGraph_shouldReturn400_whenNeitherGraphNorDefaultProvided() {
    // When
    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?branch=main",
        HttpMethod.HEAD,
        HttpEntity.EMPTY,
        Void.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNull();
  }

  @Test
  void headGraph_shouldUseMainBranchByDefault_whenNoSelectorProvided() {
    // When
    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?default=true",
        HttpMethod.HEAD,
        HttpEntity.EMPTY,
        Void.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isEqualTo("\"" + commit2Id.value() + "\"");
  }

  @Test
  void headGraph_shouldReturnNoBody_whenGraphExists() {
    // When
    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.HEAD,
        HttpEntity.EMPTY,
        Void.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNull();
  }
}
